package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.GameMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class GameSceneTools {
    private GameSceneTools() {
    }

    static String state() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        return snapshot(client, player, client.world).toString();
    }

    private static JsonObject snapshot(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        JsonObject root = new JsonObject();
        root.addProperty("minecraft", FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
        root.addProperty("singleplayer", client.isInSingleplayer());
        var integrated = client.getServer();
        root.addProperty("lanPublished", integrated != null && integrated.isRemote());
        root.addProperty("clientControlRequiresLan", false);
        if (integrated != null && integrated.isRemote()) root.addProperty("botPort", integrated.getServerPort());
        Vec3d pos = player.getEntityPos();
        Vec3d velocity = player.getVelocity();
        root.add("position", GameToolSupport.vec(pos));
        root.add("velocity", GameToolSupport.vec(velocity));
        root.addProperty("yaw", GameToolSupport.round(player.getYaw()));
        root.addProperty("pitch", GameToolSupport.round(player.getPitch()));
        root.addProperty("onGround", player.isOnGround());
        root.addProperty("health", GameToolSupport.round(player.getHealth()));
        root.addProperty("maxHealth", GameToolSupport.round(player.getMaxHealth()));
        root.addProperty("food", player.getHungerManager().getFoodLevel());
        root.addProperty("saturation", GameToolSupport.round(player.getHungerManager().getSaturationLevel()));
        root.addProperty("experience", player.experienceLevel);
        root.addProperty("experienceProgress", GameToolSupport.round(player.experienceProgress));
        GameMode mode = client.interactionManager == null ? null : client.interactionManager.getCurrentGameMode();
        root.addProperty("gamemode", mode == null ? "" : mode.getId());
        root.addProperty("dimension", String.valueOf(world.getRegistryKey().getValue()));
        root.addProperty("timeOfDay", world.getTimeOfDay());
        root.addProperty("raining", world.isRaining());
        root.addProperty("thundering", world.isThundering());
        PlayerAbilities abilities = player.getAbilities();
        root.addProperty("flying", abilities.flying);
        root.addProperty("allowFlying", abilities.allowFlying);
        root.addProperty("sneaking", player.isSneaking());
        root.addProperty("sprinting", player.isSprinting());
        root.add("held", GameToolSupport.stack(player.getMainHandStack(), player.getInventory().getSelectedSlot()));
        root.add("effects", effects(player));
        root.add("crosshair", crosshair(client));
        root.add("screen", GameClientTools.screen(client));
        BlockPos feet = player.getBlockPos();
        root.add("block", GameWorldTools.blockPos(feet));
        root.addProperty("biome", GameWorldTools.biomeId(world, feet));
        root.add("light", GameWorldTools.light(world, feet, BlockPos.ofFloored(player.getEyePos())));
        root.addProperty("pose", pose(player));
        root.addProperty("air", player.getAir());
        root.addProperty("maxAir", player.getMaxAir());
        root.addProperty("armor", player.getArmor());
        root.addProperty("absorption", GameToolSupport.round(player.getAbsorptionAmount()));
        root.addProperty("fallDistance", GameToolSupport.round(player.fallDistance));
        root.addProperty("gliding", player.isGliding());
        root.addProperty("swimming", player.isSwimming());
        root.addProperty("touchingWater", player.isTouchingWater());
        root.addProperty("submerged", player.isSubmergedInWater());
        root.addProperty("climbing", player.isClimbing());
        root.addProperty("blocking", player.isBlocking());
        root.addProperty("usingItem", player.isUsingItem());
        WorldProperties properties = world.getLevelProperties();
        root.addProperty("difficulty", properties.getDifficulty().getName());
        root.addProperty("hardcore", properties.isHardcore());
        return root;
    }

    static String debug(double radius, int blockLimit, int entityLimit) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        ClientWorld world = client.world;
        JsonObject root = new JsonObject();
        root.add("player", playerView(client, player));
        root.add("world", GameWorldTools.worldView(client, player, world));
        root.add("target", crosshair(client));
        root.add("nearby", GameWorldTools.nearby(world, player.getBlockPos(), radius, blockLimit));
        root.add("entities", entityRows(client, player, 32, Math.max(1, Math.min(entityLimit, 48))));
        root.add("screen", GameClientTools.screen(client));
        root.add("hud", GameClientTools.hud(client, player));
        root.add("chat", GameChatTools.chatLines(client, 20));
        root.add("client", GameClientTools.clientView(client));
        return root.toString();
    }

    private static JsonObject playerView(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject root = snapshot(client, player, client.world);
        root.remove("crosshair");
        root.remove("screen");
        JsonArray armor = new JsonArray();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getEquippedStack(slot);
            if (piece.isEmpty()) continue;
            JsonObject item = GameToolSupport.stack(piece, slot.getEntitySlotId());
            item.addProperty("slotName", slot.getName());
            armor.add(item);
        }
        root.add("armor", armor);
        ItemStack offhand = player.getOffHandStack();
        if (!offhand.isEmpty()) root.add("offhand", GameToolSupport.stack(offhand, 40));
        if (player.isUsingItem()) root.add("activeItem", GameToolSupport.stack(player.getActiveItem(), -1));
        root.addProperty("attackCooldown", GameToolSupport.round(player.getAttackCooldownProgress(0.0F)));
        if (player.hasVehicle() && player.getVehicle() != null) root.addProperty("vehicle", player.getVehicle().getId());
        GameMode mode = client.interactionManager == null ? null : client.interactionManager.getCurrentGameMode();
        root.addProperty("gamemode", mode == null ? "" : mode.getId());
        return root;
    }

    static String inventory() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        JsonObject root = new JsonObject();
        PlayerInventory inventory = player.getInventory();
        JsonArray items = new JsonArray();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            items.add(GameToolSupport.stack(stack, slot));
        }
        root.addProperty("selectedSlot", inventory.getSelectedSlot());
        root.add("items", items);
        JsonArray container = new JsonArray();
        if (client.currentScreen instanceof HandledScreen<?> handled) {
            for (Slot slot : handled.getScreenHandler().slots) {
                if (slot.getStack().isEmpty()) continue;
                container.add(GameToolSupport.stack(slot.getStack(), slot.id));
            }
        }
        root.add("openContainer", container);
        root.add("handler", GameInventoryTools.container());
        return root.toString();
    }

    static String entities(double radius, int limit) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        JsonObject root = new JsonObject();
        root.add("entities", entityRows(client, player, radius, limit));
        return root.toString();
    }

    static String players() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameToolSupport.requirePlayer(client);
        JsonArray array = new JsonArray();
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() == null || entry.getProfile().name() == null) continue;
                JsonObject row = new JsonObject();
                row.addProperty("name", entry.getProfile().name());
                row.addProperty("latency", entry.getLatency());
                GameMode mode = entry.getGameMode();
                row.addProperty("gamemode", mode == null ? "" : mode.getId());
                array.add(row);
            }
        }
        JsonObject root = new JsonObject();
        root.add("players", array);
        return root.toString();
    }

    private static JsonArray entityRows(MinecraftClient client, ClientPlayerEntity player, double radius, int limit) {
        double reach = Math.max(1, Math.min(radius, 96));
        int cap = Math.max(1, Math.min(limit, 80));
        double reachSquared = reach * reach;
        Vec3d origin = player.getEntityPos();
        List<JsonObject> rows = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == player) continue;
            double distance = entity.squaredDistanceTo(origin);
            if (distance > reachSquared) continue;
            JsonObject row = entityRow(entity, distance);
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(row -> row.get("distance").getAsDouble()));
        if (rows.size() > cap) rows.subList(cap, rows.size()).clear();
        JsonArray array = new JsonArray();
        rows.forEach(array::add);
        return array;
    }

    private static JsonObject entityRow(Entity entity, double distanceSquared) {
        JsonObject row = new JsonObject();
        row.addProperty("id", entity.getId());
        row.addProperty("type", entity.getType().getName().getString());
        row.addProperty("typeId", String.valueOf(Registries.ENTITY_TYPE.getId(entity.getType())));
        row.addProperty("name", entity.getName().getString());
        row.add("position", GameToolSupport.vec(entity.getEntityPos()));
        row.add("velocity", GameToolSupport.vec(entity.getVelocity()));
        row.addProperty("yaw", GameToolSupport.round(entity.getYaw()));
        row.addProperty("pitch", GameToolSupport.round(entity.getPitch()));
        row.addProperty("distance", GameToolSupport.round(Math.sqrt(distanceSquared)));
        row.addProperty("onGround", entity.isOnGround());
        row.addProperty("onFire", entity.isOnFire());
        row.addProperty("pose", pose(entity));
        if (entity.hasVehicle() && entity.getVehicle() != null) row.addProperty("vehicle", entity.getVehicle().getId());
        if (entity instanceof LivingEntity living) {
            row.addProperty("health", GameToolSupport.round(living.getHealth()));
            row.addProperty("maxHealth", GameToolSupport.round(living.getMaxHealth()));
            ItemStack held = living.getMainHandStack();
            if (!held.isEmpty()) row.add("held", GameToolSupport.stack(held, -1));
        }
        if (entity instanceof ItemEntity item) row.add("item", GameToolSupport.stack(item.getStack(), -1));
        return row;
    }

    private static String pose(Entity entity) {
        return entity.getPose().name().toLowerCase(Locale.ROOT);
    }

    private static JsonObject crosshair(MinecraftClient client) {
        JsonObject row = new JsonObject();
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            row.addProperty("type", "miss");
            return row;
        }
        if (hit instanceof BlockHitResult block) {
            row.addProperty("type", "block");
            row.add("block", GameWorldTools.blockAt(client.world, block.getBlockPos(), true));
            row.addProperty("side", block.getSide().asString());
            return row;
        }
        if (hit instanceof EntityHitResult entity) {
            row.addProperty("type", "entity");
            row.add("entity", entityRow(entity.getEntity(), entity.getEntity().squaredDistanceTo(client.player)));
            return row;
        }
        row.addProperty("type", hit.getType().toString());
        return row;
    }

    private static JsonArray effects(ClientPlayerEntity player) {
        JsonArray array = new JsonArray();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            JsonObject row = new JsonObject();
            row.addProperty("effect", effect.getTranslationKey());
            row.addProperty("amplifier", effect.getAmplifier());
            row.addProperty("duration", effect.getDuration());
            array.add(row);
        }
        return array;
    }
}
