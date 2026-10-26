package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.border.WorldBorder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class GameWorldTools {
    private GameWorldTools() {
    }

    static String find(JsonObject args) {
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        var id = net.minecraft.util.Identifier.tryParse(GameToolSupport.text(args, "block"));
        if (id == null || !Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Unknown block registry ID");
        var block = Registries.BLOCK.get(id);
        int radius = args.has("radius") ? GameInventoryTools.integer(args, "radius", 1, 32) : 16;
        int limit = args.has("limit") ? GameInventoryTools.integer(args, "limit", 1, 128) : 32;
        BlockPos origin = player.getBlockPos();
        var nearest = new java.util.PriorityQueue<BlockPos>(Comparator.comparingDouble((BlockPos pos) -> pos.getSquaredDistance(origin)).reversed());
        int total = 0;
        boolean unloaded = false;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            cursor.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
            if (!client.world.isChunkLoaded(cursor)) { unloaded = true; continue; }
            for (int y = -radius; y <= radius; y++) {
                if (x * x + y * y + z * z > radius * radius) continue;
                cursor.setY(origin.getY() + y);
                if (cursor.getY() < client.world.getBottomY() || cursor.getY() > client.world.getTopYInclusive()) continue;
                if (!client.world.getBlockState(cursor).isOf(block)) continue;
                total++;
                if (nearest.size() < limit) nearest.add(cursor.toImmutable());
                else if (cursor.getSquaredDistance(origin) < nearest.peek().getSquaredDistance(origin)) {
                    nearest.remove(); nearest.add(cursor.toImmutable());
                }
            }
        }
        JsonArray found = new JsonArray();
        nearest.stream().sorted(Comparator.comparingDouble(pos -> pos.getSquaredDistance(origin))).forEach(pos -> {
            JsonObject row = blockAt(client.world, pos, false);
            row.addProperty("distance", GameToolSupport.round(Math.sqrt(pos.getSquaredDistance(origin))));
            found.add(row);
        });
        JsonObject result = new JsonObject();
        result.add("blocks", found); result.addProperty("totalMatches", total); result.addProperty("unloadedChunksSkipped", unloaded);
        return result.toString();
    }

    static String block(double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) throw new IllegalArgumentException("Block coordinates are required");
        MinecraftClient client = MinecraftClient.getInstance();
        GameToolSupport.requirePlayer(client);
        return blockAt(client.world, BlockPos.ofFloored(x, y, z), true).toString();
    }

    static JsonObject worldView(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        JsonObject view = new JsonObject();
        BlockPos feet = player.getBlockPos();
        view.addProperty("dimension", String.valueOf(world.getRegistryKey().getValue()));
        view.addProperty("biome", biomeId(world, feet));
        view.addProperty("time", world.getTime());
        view.addProperty("timeOfDay", world.getTimeOfDay());
        view.addProperty("night", world.isNight());
        view.addProperty("raining", world.isRaining());
        view.addProperty("thundering", world.isThundering());
        WorldProperties properties = world.getLevelProperties();
        view.addProperty("difficulty", properties.getDifficulty().getName());
        view.addProperty("hardcore", properties.isHardcore());
        view.addProperty("seaLevel", world.getSeaLevel());
        view.addProperty("bottom", world.getBottomY());
        view.addProperty("top", world.getTopYInclusive());
        ChunkPos chunk = player.getChunkPos();
        view.addProperty("chunkX", chunk.x);
        view.addProperty("chunkZ", chunk.z);
        view.addProperty("localX", feet.getX() & 15);
        view.addProperty("localZ", feet.getZ() & 15);
        view.addProperty("chunkLoaded", world.isChunkLoaded(feet));
        view.add("light", light(world, feet, BlockPos.ofFloored(player.getEyePos())));
        WorldBorder border = world.getWorldBorder();
        JsonObject edge = new JsonObject();
        edge.addProperty("size", GameToolSupport.round(border.getSize()));
        edge.addProperty("centerX", GameToolSupport.round(border.getCenterX()));
        edge.addProperty("centerZ", GameToolSupport.round(border.getCenterZ()));
        edge.addProperty("inside", GameToolSupport.round(border.getDistanceInsideBorder(player.getX(), player.getZ())));
        view.add("border", edge);
        view.addProperty("players", world.getPlayers().size());
        return view;
    }

    static JsonArray nearby(ClientWorld world, BlockPos origin, double radius, int limit) {
        int reach = (int) Math.max(1, Math.min(radius, 8));
        int cap = Math.max(1, Math.min(limit, 120));
        List<JsonObject> rows = new ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = -reach; y <= reach; y++) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!world.isChunkLoaded(cursor)) continue;
                    var state = world.getBlockState(cursor);
                    if (state.isAir() && state.getFluidState().getFluid() == Fluids.EMPTY) continue;
                    JsonObject row = blockAt(world, cursor, false);
                    row.addProperty("distance", GameToolSupport.round(Math.sqrt(x * x + y * y + z * z)));
                    rows.add(row);
                }
            }
        }
        rows.sort(Comparator.comparingDouble(row -> row.get("distance").getAsDouble()));
        if (rows.size() > cap) rows.subList(cap, rows.size()).clear();
        JsonArray array = new JsonArray();
        rows.forEach(array::add);
        return array;
    }

    static JsonObject blockAt(ClientWorld world, BlockPos pos, boolean detail) {
        JsonObject row = new JsonObject();
        row.addProperty("x", pos.getX());
        row.addProperty("y", pos.getY());
        row.addProperty("z", pos.getZ());
        if (!world.isChunkLoaded(pos)) {
            row.addProperty("loaded", false);
            return row;
        }
        var state = world.getBlockState(pos);
        row.addProperty("id", String.valueOf(Registries.BLOCK.getId(state.getBlock())));
        row.addProperty("name", state.getBlock().getName().getString());
        row.addProperty("air", state.isAir());
        JsonObject props = properties(state);
        if (props.size() > 0) row.add("properties", props);
        if (!state.isAir()) {
            row.addProperty("hardness", GameToolSupport.round(state.getHardness(world, pos)));
            row.addProperty("solid", state.isSolidBlock(world, pos));
            row.addProperty("luminance", state.getLuminance());
        }
        FluidState fluid = state.getFluidState();
        if (fluid.getFluid() != Fluids.EMPTY) {
            row.addProperty("fluid", String.valueOf(Registries.FLUID.getId(fluid.getFluid())));
            row.addProperty("fluidLevel", fluid.getLevel());
        }
        if (!detail) return row;
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity == null) return row;
        row.addProperty("blockEntity", String.valueOf(Registries.BLOCK_ENTITY_TYPE.getId(entity.getType())));
        if (entity instanceof SignBlockEntity sign) {
            JsonArray lines = new JsonArray();
            var text = sign.getFrontText();
            for (int line = 0; line < 4; line++) {
                String value = text.getMessage(line, false).getString();
                if (!value.isBlank()) lines.add(value);
            }
            if (!lines.isEmpty()) row.add("sign", lines);
        }
        if (entity instanceof Inventory inventory) {
            JsonArray contents = new JsonArray();
            int shown = 0;
            for (int slot = 0; slot < inventory.size() && shown < 40; slot++) {
                ItemStack item = inventory.getStack(slot);
                if (item.isEmpty()) continue;
                contents.add(GameToolSupport.stack(item, slot));
                shown++;
            }
            if (!contents.isEmpty()) row.add("contents", contents);
        }
        return row;
    }

    private static JsonObject properties(net.minecraft.block.BlockState state) {
        JsonObject props = new JsonObject();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            props.addProperty(entry.getKey().getName(), propertyName(entry.getKey(), entry.getValue()));
        }
        return props;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(Property property, Comparable value) {
        return property.name(value);
    }

    static JsonObject blockPos(BlockPos pos) {
        JsonObject row = new JsonObject();
        row.addProperty("x", pos.getX());
        row.addProperty("y", pos.getY());
        row.addProperty("z", pos.getZ());
        return row;
    }

    static String biomeId(ClientWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return "";
        return world.getBiome(pos).getKey().map(key -> key.getValue().toString()).orElse("");
    }

    static JsonObject light(ClientWorld world, BlockPos feet, BlockPos eyes) {
        JsonObject row = new JsonObject();
        if (!world.isChunkLoaded(feet)) return row;
        row.addProperty("sky", world.getLightLevel(LightType.SKY, feet));
        row.addProperty("block", world.getLightLevel(LightType.BLOCK, feet));
        if (world.isChunkLoaded(eyes)) row.addProperty("eye", world.getLightLevel(eyes));
        return row;
    }
}
