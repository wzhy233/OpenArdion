package io.github.wzhy233.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class GameActionTools {
    private static final ConcurrentHashMap<String, Job> JOBS = new ConcurrentHashMap<>();
    private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();
    private static volatile Job active;
    private static boolean registered;
    private static Boolean pauseOnLostFocus;
    private static int focusHolds;

    static void ensureTick() {
        if (registered) return;
        registered = true;
        ClientTickEvents.START_CLIENT_TICK.register(GameActionTools::tick);
    }

    static void consoleOpened() { holdFocus(); }

    static void holdFocus() {
        var client = MinecraftClient.getInstance();
        if (focusHolds++ == 0) {
            if (pauseOnLostFocus == null) pauseOnLostFocus = client.options.pauseOnLostFocus;
            client.options.pauseOnLostFocus = false;
        }
    }

    static void releaseFocus() {
        if (focusHolds <= 0) return;
        var client = MinecraftClient.getInstance();
        if (--focusHolds == 0 && pauseOnLostFocus != null) {
            client.options.pauseOnLostFocus = pauseOnLostFocus;
            pauseOnLostFocus = null;
        }
    }

    static void consoleClosed() {
        io.github.wzhy233.ardion.debug.ClientDiagnostics.control("stop", "", 0);
        cancel("CLI closed");
        GameMovement.release();
        releaseFocus();
    }

    static void requireIdle() {
        if (active != null) throw new IllegalStateException("Game action " + active.id + " is running; inspect action_status or cancel_action first");
    }

    static String start(String kind, JsonObject args) {
        requireIdle();
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        if (!player.isAlive()) throw new IllegalStateException("Player is dead");
        if (client.isPaused()) throw new IllegalStateException("Minecraft is paused; close the pause menu first");
        if (!kind.equals("wait") && !kind.equals("craft") && client.currentScreen != null) throw new IllegalStateException("Close the open screen with inventory_action before movement or world actions");
        Job job = new Job(kind, client.world);
        job.limit = args.has("timeoutTicks") ? GameInventoryTools.integer(args, "timeoutTicks", 1, 12000) : 1200;
        switch (kind) {
            case "navigate" -> {
                job.target = GameInteractionTools.position(args);
                job.range = GameToolSupport.number(args, "range", 1);
                if (!Double.isFinite(job.range) || job.range < 0.5 || job.range > 8) throw new IllegalArgumentException("range must be 0.5-8 blocks");
                job.route = plan(client, job);
            }
            case "mine" -> {
                job.target = GameInteractionTools.position(args);
                GameInteractionTools.hit(job.target, "");
                var block = client.world.getBlockState(job.target);
                if (block.isAir() || block.getHardness(client.world, job.target) < 0) throw new IllegalArgumentException("Target is air or unbreakable");
                job.block = Registries.BLOCK.getId(block.getBlock()).toString();
                if (!args.has("autoTool") || args.get("autoTool").getAsBoolean()) {
                    int best = player.getInventory().getSelectedSlot();
                    double score = -1;
                    for (int slot = 0; slot < 36; slot++) {
                        var stack = player.getInventory().getStack(slot);
                        double speed = stack.getMiningSpeedMultiplier(block) + (stack.isSuitableFor(block) ? 1000 : 0);
                        if (speed > score) { best = slot; score = speed; }
                    }
                    JsonObject equip = new JsonObject(); equip.addProperty("action", "equip"); equip.addProperty("slot", best);
                    GameInventoryTools.action(equip);
                }
            }
            case "use" -> {
                job.hand = GameInteractionTools.hand(args);
                job.duration = args.has("ticks") ? GameInventoryTools.integer(args, "ticks", 1, 12000) : 40;
                job.limit = Math.max(job.limit, job.duration + 5);
                if (player.getStackInHand(job.hand).isEmpty()) throw new IllegalArgumentException("Selected hand is empty");
            }
            case "wait" -> {
                job.duration = GameInventoryTools.integer(args, "ticks", 1, 12000);
                job.limit = job.duration + 5;
            }
            case "craft" -> {
                if (!(player.currentScreenHandler instanceof CraftingScreenHandler) && !(player.currentScreenHandler instanceof PlayerScreenHandler)) {
                    throw new IllegalStateException("Open player inventory or a crafting table first; furnace input/output uses inventory_action");
                }
                job.recipe = GameInventoryTools.integer(args, "recipeId", 0, Integer.MAX_VALUE);
                var entry = GameInventoryTools.entries().stream().filter(row -> row.id().index() == job.recipe).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Recipe is not in the current server recipe book"));
                var outputs = entry.getStacks(SlotDisplayContexts.createParameters(client.world));
                if (outputs.isEmpty()) throw new IllegalArgumentException("Recipe has no output display");
                job.item = Registries.ITEM.getId(outputs.getFirst().getItem()).toString();
                job.count = count(client, job.item);
                job.syncId = GameInventoryTools.integer(args, "syncId", 0, Integer.MAX_VALUE);
                if (job.syncId != player.currentScreenHandler.syncId) throw new IllegalStateException("Container changed; inspect inventory again");
                job.all = args.has("all") && args.get("all").getAsBoolean();
            }
            default -> throw new IllegalArgumentException("Unknown action");
        }
        job.before = observation(client);
        active = job;
        JOBS.put(job.id, job);
        HISTORY.addLast(job.id);
        while (HISTORY.size() > 32) JOBS.remove(HISTORY.removeFirst());
        return describe(job).toString();
    }

    static String status(JsonObject args) throws Exception {
        String id = GameToolSupport.text(args, "actionId");
        Job job = JOBS.get(id);
        if (job == null) throw new IllegalArgumentException("Unknown actionId (recent 32 actions are retained)");
        String closed = closeIfStalled(job);
        if (closed != null) return closed;
        int wait = args.has("waitMs") ? GameInventoryTools.integer(args, "waitMs", 0, 10000) : 0;
        if (wait > 0 && !MinecraftClient.getInstance().isOnThread() && !job.done.isDone()) {
            try { return job.done.get(wait, TimeUnit.MILLISECONDS); }
            catch (TimeoutException ignored) { }
        }
        closed = closeIfStalled(job);
        if (closed != null) return closed;
        return GameToolSupport.onClient(() -> {
            if (!job.done.isDone() && MinecraftClient.getInstance().isPaused()) {
                finish(job, "failed", "Minecraft was paused; the action was stopped so the agent is not left running");
            }
            return describe(job).toString();
        });
    }

    private static boolean deadlinePassed(Job job) {
        return System.nanoTime() - job.started > (job.limit / 20.0 + 15) * 1_000_000_000L;
    }

    private static String closeIfStalled(Job job) throws Exception {
        if (job.done.isDone() || !deadlinePassed(job)) return null;
        String message = "Action timed out; client ticks were not advancing";
        try {
            return GameToolSupport.onClient(() -> {
                if (!job.done.isDone()) finish(job, "failed", message);
                return describe(job).toString();
            });
        } catch (java.io.IOException exception) {
            finish(job, "failed", "Action timed out and the game thread did not answer; movement release was queued");
            return describe(job).toString();
        }
    }

    static String cancel(String reason) {
        if (active == null) return "{\"status\":\"idle\"}";
        Job job = active;
        finish(job, "cancelled", reason);
        return describe(job).toString();
    }

    private static void tick(MinecraftClient client) {
        Job job = active;
        if (job == null) return;
        try {
            var player = GameToolSupport.requirePlayer(client);
            if (client.world != job.world || !player.isAlive()) throw new IllegalStateException("World changed or player died");
            if (client.isPaused()) throw new IllegalStateException("Minecraft was paused");
            if (++job.ticks > job.limit || System.nanoTime() - job.started > (job.limit / 20.0 + 15) * 1_000_000_000L) {
                throw new IllegalStateException("Action timed out; inspect the returned observations before retrying");
            }
            switch (job.kind) {
                case "wait" -> { if (job.ticks >= job.duration) finish(job, "succeeded", "Waited for game ticks"); }
                case "navigate" -> navigate(client, job);
                case "mine" -> mine(client, job);
                case "use" -> {
                    if (client.currentScreen != null) throw new IllegalStateException("A screen interrupted item use");
                    if (job.ticks == 1) {
                        var result = client.interactionManager.interactItem(player, job.hand);
                        if (!result.isAccepted()) throw new IllegalStateException("Item use was not accepted: " + result);
                    }
                    if (job.ticks >= job.duration || (job.ticks > 2 && !player.isUsingItem())) finish(job, "succeeded", "Item-use interaction finished; inspect inventory/effects to verify the task");
                }
                case "craft" -> craft(client, job);
            }
        } catch (Exception error) {
            finish(job, "failed", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private static void craft(MinecraftClient client, Job job) {
        var player = client.player;
        var handler = player.currentScreenHandler;
        if (handler.syncId != job.syncId) throw new IllegalStateException("Crafting screen changed");
        if (job.ticks == 1) {
            client.interactionManager.clickRecipe(handler.syncId, new net.minecraft.recipe.NetworkRecipeId(job.recipe), job.all);
            return;
        }
        if (job.collected) {
            if (job.ticks - job.settle >= 5 && count(client, job.item) > job.count) finish(job, "succeeded", "Crafted output observed in player inventory");
            if (job.ticks - job.settle > 60) throw new IllegalStateException("Output was not added; inventory may be full or the server rejected the recipe");
            return;
        }
        var output = handler.getSlot(0).getStack();
        if (!output.isEmpty() && Registries.ITEM.getId(output.getItem()).toString().equals(job.item)) {
            client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, player);
            job.collected = true;
            job.settle = job.ticks;
        } else if (job.ticks > 60) throw new IllegalStateException("Recipe produced no output; check ingredients, station and server recipe availability");
    }

    private static void mine(MinecraftClient client, Job job) {
        if (client.currentScreen != null) throw new IllegalStateException("A screen interrupted mining");
        if (!client.world.isChunkLoaded(job.target)) throw new IllegalStateException("Target chunk unloaded");
        var block = client.world.getBlockState(job.target);
        if (block.isAir()) {
            if (++job.settle >= 5) finish(job, "succeeded", "Target observed as air; dropped items may still need collecting");
            return;
        }
        job.settle = 0;
        if (!Registries.BLOCK.getId(block.getBlock()).toString().equals(job.block)) throw new IllegalStateException("Target block changed during mining");
        var hit = GameInteractionTools.hit(job.target, "");
        GameInteractionTools.face(hit.getPos());
        if (job.ticks == 1) {
            if (!client.interactionManager.attackBlock(job.target, hit.getSide())) throw new IllegalStateException("Cannot start breaking this block");
        } else client.interactionManager.updateBlockBreakingProgress(job.target, hit.getSide());
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void navigate(MinecraftClient client, Job job) {
        if (client.currentScreen != null) throw new IllegalStateException("A screen interrupted movement");
        Vec3d position = client.player.getEntityPos();
        if (position.distanceTo(Vec3d.ofBottomCenter(job.target)) <= job.range + 0.05) {
            finish(job, "succeeded", "Arrived within the requested range");
            return;
        }
        if (job.last == null || position.distanceTo(job.last) > 0.15) { job.last = position; job.stuck = 0; }
        if (++job.stuck > 80) throw new IllegalStateException("Movement is stuck; inspect terrain and choose another route");
        if (job.route.isEmpty() || job.ticks % 20 == 0) job.route = plan(client, job);
        while (!job.route.isEmpty() && position.distanceTo(bottom(job.route.getFirst())) < 0.4) job.route = job.route.subList(1, job.route.size());
        WalkPath.Point next = job.route.isEmpty() ? point(client.player.getBlockPos()) : job.route.getFirst();
        if (!grid(client).standable(next)) { job.route = plan(client, job); return; }
        Vec3d target = bottom(next);
        Vec3d delta = target.subtract(position);
        client.player.setYaw((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
        client.player.setPitch(15);
        GameMovement.steer(next.y() > position.y + 0.2 && client.player.isOnGround());
    }

    private static List<WalkPath.Point> plan(MinecraftClient client, Job job) {
        BlockPos start = client.player.getBlockPos();
        return WalkPath.find(point(start), point(job.target), job.range, grid(client));
    }

    private static WalkPath.Grid grid(MinecraftClient client) {
        return new WalkPath.Grid() {
            public boolean clear(WalkPath.Point point) {
                BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
                if (!client.world.isChunkLoaded(pos) || pos.getY() < client.world.getBottomY() || pos.getY() + 1 > client.world.getTopYInclusive()) return false;
                if (!client.world.getWorldBorder().contains(pos)) return false;
                for (BlockPos block : List.of(pos, pos.up())) if (!safe(block)) return false;
                return client.world.isSpaceEmpty(client.player, new Box(point.x() + 0.2, point.y() + 0.01, point.z() + 0.2, point.x() + 0.8, point.y() + 1.8, point.z() + 0.8));
            }
            private boolean safe(BlockPos pos) {
                var state = client.world.getBlockState(pos);
                return state.getFluidState().isEmpty() && !state.isOf(Blocks.FIRE) && !state.isOf(Blocks.SOUL_FIRE)
                        && !state.isOf(Blocks.CACTUS) && !state.isOf(Blocks.MAGMA_BLOCK) && !state.isOf(Blocks.CAMPFIRE)
                        && !state.isOf(Blocks.SOUL_CAMPFIRE) && !state.isOf(Blocks.POWDER_SNOW) && !state.isOf(Blocks.SWEET_BERRY_BUSH);
            }
            public boolean standable(WalkPath.Point point) {
                BlockPos floor = new BlockPos(point.x(), point.y() - 1, point.z());
                return client.world.isChunkLoaded(floor) && safe(floor) && clear(point)
                        && client.world.getBlockState(floor).isSideSolidFullSquare(client.world, floor, net.minecraft.util.math.Direction.UP);
            }
        };
    }

    static boolean usingItem() { return active != null && active.kind.equals("use"); }
    static boolean mining() { return active != null && active.kind.equals("mine"); }
    private static WalkPath.Point point(BlockPos pos) { return new WalkPath.Point(pos.getX(), pos.getY(), pos.getZ()); }
    private static Vec3d bottom(WalkPath.Point point) { return new Vec3d(point.x() + 0.5, point.y(), point.z() + 0.5); }
    private static int count(MinecraftClient client, String item) {
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            var stack = client.player.getInventory().getStack(slot);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(item)) count += stack.getCount();
        }
        return count;
    }
    private static JsonObject observation(MinecraftClient client) {
        JsonObject result = new JsonObject();
        if (client.player != null && client.world != null) {
            result.add("state", JsonParser.parseString(GameSceneTools.state()));
            result.add("inventory", JsonParser.parseString(GameSceneTools.inventory()));
        } else result.addProperty("disconnected", true);
        return result;
    }
    private static JsonObject describe(Job job) {
        if (job.done.isDone()) return JsonParser.parseString(job.done.join()).getAsJsonObject();
        JsonObject result = new JsonObject();
        result.addProperty("actionId", job.id); result.addProperty("kind", job.kind); result.addProperty("status", "running");
        result.addProperty("ticks", job.ticks); result.addProperty("timeoutTicks", job.limit);
        if (job.target != null) result.add("target", GameWorldTools.blockPos(job.target));
        result.add("before", job.before);
        return result;
    }
    private static void finish(Job job, String status, String message) {
        synchronized (job) {
            if (job.done.isDone()) {
                if (active == job) active = null;
                release(job);
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("actionId", job.id);
            result.addProperty("kind", job.kind);
            result.addProperty("status", status);
            result.addProperty("message", message);
            result.addProperty("ticks", job.ticks);
            result.addProperty("timeoutTicks", job.limit);
            if (job.target != null) result.add("target", GameWorldTools.blockPos(job.target));
            if (job.before != null) result.add("before", job.before);
            var client = MinecraftClient.getInstance();
            try {
                if (client != null && client.isOnThread()) result.add("after", observation(client));
            } catch (RuntimeException error) {
                result.addProperty("observationError", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            }
            result.addProperty("evidence", "Observed Minecraft client world/inventory, not a claim of server acknowledgement or task-level success");
            job.done.complete(result.toString());
            if (active == job) active = null;
        }
        if (!status.equals("succeeded")) java.util.logging.Logger.getLogger("ardion.agent").info(job.kind + " " + job.id + " " + status + ": " + message);
        release(job);
    }

    private static void release(Job job) {
        try {
            var client = MinecraftClient.getInstance();
            if (client == null) return;
            if (!client.isOnThread()) { client.execute(() -> release(job)); return; }
            GameMovement.release();
            if (client.interactionManager != null) {
                if (job.kind.equals("mine")) client.interactionManager.cancelBlockBreaking();
                if (job.kind.equals("use") && client.player != null) client.interactionManager.stopUsingItem(client.player);
            }
        } catch (RuntimeException error) {
            java.util.logging.Logger.getLogger("ardion.agent").warning("action release failed: " + error.getMessage());
        }
    }

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final String kind;
        final ClientWorld world;
        final long started = System.nanoTime();
        final CompletableFuture<String> done = new CompletableFuture<>();
        JsonObject before;
        BlockPos target;
        List<WalkPath.Point> route = List.of();
        Vec3d last;
        String block, item;
        Hand hand = Hand.MAIN_HAND;
        double range;
        volatile int ticks;
        int limit, duration, stuck, settle, recipe, syncId, count;
        boolean collected, all;
        Job(String kind, ClientWorld world) { this.kind = kind; this.world = world; }
    }
}
