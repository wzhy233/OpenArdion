package io.github.wzhy233.mcp;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

final class GameInteractionTools {
    static BlockPos position(JsonObject args) {
        return BlockPos.ofFloored(required(args, "x"), required(args, "y"), required(args, "z"));
    }

    static double required(JsonObject args, String name) {
        double value = GameToolSupport.number(args, name, Double.NaN);
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000) throw new IllegalArgumentException("Invalid coordinate: " + name);
        return value;
    }

    static Hand hand(JsonObject args) {
        String hand = GameToolSupport.text(args, "hand");
        if (hand.isEmpty() || hand.equals("main")) return Hand.MAIN_HAND;
        if (hand.equals("off")) return Hand.OFF_HAND;
        throw new IllegalArgumentException("hand must be main or off");
    }

    static void face(Vec3d target) {
        var player = GameToolSupport.requirePlayer(MinecraftClient.getInstance());
        Vec3d delta = target.subtract(player.getEyePos());
        player.setYaw((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
        player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }

    static BlockHitResult hit(BlockPos pos, String side) {
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        if (!client.world.isChunkLoaded(pos)) throw new IllegalArgumentException("Target chunk is not loaded");
        Vec3d eyes = player.getEyePos();
        Vec3d target = pos.toCenterPos();
        if (!side.isBlank()) {
            Direction direction = Direction.byId(side);
            if (direction == null) throw new IllegalArgumentException("Unknown block face");
            target = target.add(Vec3d.of(direction.getVector()).multiply(0.499));
        }
        if (eyes.distanceTo(target) > player.getBlockInteractionRange()) throw new IllegalArgumentException("Block is out of reach; navigate closer first");
        BlockHitResult hit = client.world.raycast(new RaycastContext(eyes, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) throw new IllegalArgumentException("Target face is obstructed or has no collision outline");
        if (!side.isBlank() && !hit.getSide().getId().equals(side)) throw new IllegalArgumentException("Requested block face is not visible");
        return hit;
    }

    static String block(JsonObject args) {
        GameActionTools.requireIdle();
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        BlockPos pos = position(args);
        BlockHitResult hit = hit(pos, GameToolSupport.text(args, "face"));
        face(hit.getPos());
        var result = client.interactionManager.interactBlock(player, hand(args), hit);
        if (result.isAccepted()) player.swingHand(hand(args));
        JsonObject response = new JsonObject();
        response.addProperty("submitted", result.isAccepted());
        response.addProperty("result", result.toString());
        response.addProperty("note", "Client interaction result, not server confirmation. Read block/inventory/screen after ticks to verify.");
        response.add("target", GameWorldTools.blockAt(client.world, pos, true));
        return response.toString();
    }

    static String aim(JsonObject args) {
        GameActionTools.requireIdle();
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        var entity = client.world.getEntityById(GameInventoryTools.integer(args, "entityId", 0, Integer.MAX_VALUE));
        if (entity == null || !entity.isAlive() || !player.canSee(entity)) throw new IllegalArgumentException("Target is absent or obstructed");
        face(entity.getBoundingBox().getCenter());
        return "{\"aimed\":true,\"note\":\"Aiming is not a confirmed hit\"}";
    }

    static String entity(JsonObject args) {
        GameActionTools.requireIdle();
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        int id = GameInventoryTools.integer(args, "entityId", 0, Integer.MAX_VALUE);
        Entity entity = client.world.getEntityById(id);
        if (entity == null || !entity.isAlive()) throw new IllegalArgumentException("Entity no longer exists");
        var box = entity.getBoundingBox();
        var eyes = player.getEyePos();
        var nearest = new Vec3d(Math.clamp(eyes.x, box.minX, box.maxX), Math.clamp(eyes.y, box.minY, box.maxY), Math.clamp(eyes.z, box.minZ, box.maxZ));
        if (eyes.squaredDistanceTo(nearest) > Math.pow(player.getEntityInteractionRange(), 2)
                || !player.canSee(entity)) throw new IllegalArgumentException("Entity is out of reach or obstructed");
        face(entity.getBoundingBox().getCenter());
        String action = GameToolSupport.text(args, "action");
        if (action.equals("attack")) {
            client.interactionManager.attackEntity(player, entity);
            player.swingHand(Hand.MAIN_HAND);
        } else if (action.isEmpty() || action.equals("interact")) {
            client.interactionManager.interactEntity(player, entity, hand(args));
            player.swingHand(hand(args));
        } else throw new IllegalArgumentException("action must be attack or interact");
        return "{\"submitted\":true,\"note\":\"Read entity and player state after ticks for server outcome\"}";
    }
}
