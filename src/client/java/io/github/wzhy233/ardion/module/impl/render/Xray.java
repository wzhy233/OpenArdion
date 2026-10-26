package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.render.Render3DEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.render.Render3DUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Xray extends Module {
    private static final float[] DIAMOND_COLOR = {0.0F, 1.0F, 1.0F};
    private static final float[] GOLD_COLOR = {1.0F, 215.0F / 255.0F, 0.0F};
    private static final float[] IRON_COLOR = {216.0F / 255.0F, 216.0F / 255.0F, 216.0F / 255.0F};
    private static final float[] COPPER_COLOR = {184.0F / 255.0F, 115.0F / 255.0F, 51.0F / 255.0F};
    private static final float[] COAL_COLOR = {30.0F / 255.0F, 30.0F / 255.0F, 30.0F / 255.0F};
    private static final float[] ANCIENT_DEBRIS_COLOR = {93.0F / 255.0F, 61.0F / 255.0F, 43.0F / 255.0F};
    private static final float[] FAKE_ORE_COLOR = {1.0F, 0.0F, 0.0F};
    private static final float[] UNSAFE_ORE_COLOR = {1.0F, 0.5F, 0.0F};

    private static final Block[] STONE_VARIANTS = {
            Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.BLACKSTONE, Blocks.NETHERRACK
    };

    private final BooleanValue diamond = this.registerValue(new BooleanValue("diamond", this, true));
    private final BooleanValue gold = this.registerValue(new BooleanValue("gold", this, true));
    private final BooleanValue iron = this.registerValue(new BooleanValue("iron", this, true));
    private final BooleanValue copper = this.registerValue(new BooleanValue("copper", this, true));
    private final BooleanValue coal = this.registerValue(new BooleanValue("coal", this, true));
    private final BooleanValue ancientDebris = this.registerValue(new BooleanValue("ancient-debris", this, true));
    private final NumberValue alpha = this.registerValue(new NumberValue("alpha", this, 0.25D, 0.05D, 1.0D));
    private final BooleanValue antiFakeOre = this.registerValue(new BooleanValue("anti-fake-ore", this, true, () -> false));
    private final NumberValue rangeXZ = this.registerValue(new NumberValue("range-xz", this, 8, 2, 32, true));
    private final NumberValue rangeY = this.registerValue(new NumberValue("range-y", this, 16, 4, 64, true));
    private final NumberValue updateDelay = this.registerValue(new NumberValue("update-delay", this, 500, 100, 3000, true));

    private final Map<BlockPos, OreInfo> oreInfoMap = new LinkedHashMap<>();
    private final Map<BlockPos, Box> boxes = new LinkedHashMap<>();
    private final Map<BlockPos, ChunkScan> chunkScans = new LinkedHashMap<>();
    private long lastUpdateTime;
    private int lastScanSignature = Integer.MIN_VALUE;

    public Xray() {
        super("xray", ModuleCategory.RENDER);
    }

    @Override
    protected void onDisable() {
        this.clearState();
    }

    @Listener
    public void onWorldLoad(WorldLoadEvent event) {
        this.clearState();
    }

    @Listener
    public void onWorldTick(WorldTickEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            this.clearState();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastUpdateTime < this.updateDelay.getValue().longValue()) {
            return;
        }
        this.lastUpdateTime = now;
        this.collectOres(client.world, client);
    }

    @Listener
    public void onRender3D(Render3DEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getCameraEntity() == null || this.boxes.isEmpty()) {
            return;
        }

        float alphaValue = this.alpha.getValue().floatValue();
        var matrices = event.getMatrices();
        var cameraPos = client.gameRenderer.getCamera().getCameraPos();

        matrices.push();
        try {
            for (Map.Entry<BlockPos, Box> entry : this.boxes.entrySet()) {
                float[] color = this.getColor(entry.getKey());
                Box shiftedBox = entry.getValue().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Render3DUtil.drawBox(matrices, event.getConsumers(), shiftedBox, color[0], color[1], color[2], alphaValue, true, false);
            }
        } finally {
            matrices.pop();
        }

        Render3DUtil.flushOutline();
    }

    private void collectOres(ClientWorld world, MinecraftClient client) {
        int scanSignature = this.getScanSignature();
        if (scanSignature != this.lastScanSignature) {
            this.clearScanCache();
            this.lastScanSignature = scanSignature;
        }

        BlockPos playerPos = client.player.getBlockPos();
        int xzRadius = this.rangeXZ.getValue().intValue();
        int yRadius = this.rangeY.getValue().intValue();

        int minX = playerPos.getX() - xzRadius;
        int maxX = playerPos.getX() + xzRadius;
        int minZ = playerPos.getZ() - xzRadius;
        int maxZ = playerPos.getZ() + xzRadius;
        int minY = Math.max(world.getBottomY(), playerPos.getY() - yRadius);
        int maxY = Math.min(world.getTopYInclusive() - 1, playerPos.getY() + yRadius);

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        Set<BlockPos> requiredChunks = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                BlockPos chunkKey = this.chunkKey(cx, cz);
                requiredChunks.add(chunkKey);

                Chunk chunk = world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                this.replaceChunkScan(chunkKey, this.scanChunk(world, chunk, minX, maxX, minZ, maxZ, minY, maxY));
            }
        }

        this.pruneChunks(requiredChunks);
    }

    private Map<BlockPos, OreInfo> scanChunk(ClientWorld world, Chunk chunk, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        Map<BlockPos, OreInfo> found = new LinkedHashMap<>();
        int chunkStartX = chunk.getPos().x << 4;
        int chunkStartZ = chunk.getPos().z << 4;

        int loopMinX = Math.max(minX, chunkStartX);
        int loopMaxX = Math.min(maxX, chunkStartX + 15);
        int loopMinZ = Math.max(minZ, chunkStartZ);
        int loopMaxZ = Math.min(maxZ, chunkStartZ + 15);

        for (int x = loopMinX; x <= loopMaxX; x++) {
            for (int z = loopMinZ; z <= loopMaxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;

                    OreType type = this.getOreType(state);
                    if (type == null) continue;

                    OreCheckResult checkResult = OreCheckResult.REAL;
                    if (this.antiFakeOre.getValue()) {
                        checkResult = this.checkOreAuthenticity(world, pos, type);
                    }

                    found.put(pos, new OreInfo(type, checkResult));
                }
            }
        }

        return found;
    }

    private void replaceChunkScan(BlockPos chunkKey, Map<BlockPos, OreInfo> found) {
        ChunkScan previous = this.chunkScans.remove(chunkKey);
        if (previous != null) {
            for (BlockPos pos : previous.oreInfo().keySet()) {
                this.oreInfoMap.remove(pos);
                this.boxes.remove(pos);
            }
        }

        if (found.isEmpty()) {
            return;
        }

        Map<BlockPos, OreInfo> snapshot = Map.copyOf(found);
        this.chunkScans.put(chunkKey, new ChunkScan(snapshot));
        for (Map.Entry<BlockPos, OreInfo> entry : snapshot.entrySet()) {
            this.oreInfoMap.put(entry.getKey(), entry.getValue());
            this.boxes.put(entry.getKey(), new Box(entry.getKey()));
        }
    }

    private void pruneChunks(Set<BlockPos> requiredChunks) {
        var iterator = this.chunkScans.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ChunkScan> entry = iterator.next();
            if (requiredChunks.contains(entry.getKey())) {
                continue;
            }

            for (BlockPos pos : entry.getValue().oreInfo().keySet()) {
                this.oreInfoMap.remove(pos);
                this.boxes.remove(pos);
            }
            iterator.remove();
        }
    }

    private BlockPos chunkKey(int chunkX, int chunkZ) {
        return new BlockPos(chunkX, 0, chunkZ);
    }

    private int getScanSignature() {
        int result = Boolean.hashCode(this.diamond.getValue());
        result = 31 * result + Boolean.hashCode(this.gold.getValue());
        result = 31 * result + Boolean.hashCode(this.iron.getValue());
        result = 31 * result + Boolean.hashCode(this.copper.getValue());
        result = 31 * result + Boolean.hashCode(this.coal.getValue());
        result = 31 * result + Boolean.hashCode(this.ancientDebris.getValue());
        result = 31 * result + Boolean.hashCode(this.antiFakeOre.getValue());
        result = 31 * result + this.rangeXZ.getValue().intValue();
        result = 31 * result + this.rangeY.getValue().intValue();
        return result;
    }

    private OreCheckResult checkOreAuthenticity(ClientWorld world, BlockPos pos, OreType type) {
        if (type == OreType.ANCIENT_DEBRIS) {
            return checkNetherOre(world, pos);
        }
        return checkOverworldOre(world, pos, type);
    }

    private OreCheckResult checkOverworldOre(ClientWorld world, BlockPos pos, OreType type) {
        int stoneNeighbors = 0;
        int validNeighbors = 0;
        int airCount = 0;
        boolean hasBlockEntity = false;

        BlockPos.Mutable neighborPos = new BlockPos.Mutable();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    neighborPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState neighborState = world.getBlockState(neighborPos);
                    Block neighborBlock = neighborState.getBlock();

                    if (neighborBlock == Blocks.AIR || neighborBlock == Blocks.CAVE_AIR || neighborBlock == Blocks.VOID_AIR) {
                        airCount++;
                        continue;
                    }

                    if (hasBlockEntity(neighborBlock, neighborState)) {
                        hasBlockEntity = true;
                    }

                    boolean isStone = isStoneBlock(neighborBlock);
                    if (isStone) {
                        stoneNeighbors++;
                    }
                    validNeighbors++;
                }
            }
        }

        if (airCount >= 18) {
            return OreCheckResult.FAKE_SUSPICIOUS;
        }

        if (stoneNeighbors < 2) {
            return OreCheckResult.FAKE_NO_SURROUNDING;
        }

        if (stoneNeighbors >= 6) {
            return OreCheckResult.REAL;
        }

        return OreCheckResult.UNSAFE;
    }

    private OreCheckResult checkNetherOre(ClientWorld world, BlockPos pos) {
        int netherBlockNeighbors = 0;
        BlockPos.Mutable neighborPos = new BlockPos.Mutable();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    neighborPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Block neighborBlock = world.getBlockState(neighborPos).getBlock();

                    if (neighborBlock == Blocks.NETHERRACK || neighborBlock == Blocks.BASALT || 
                        neighborBlock == Blocks.BLACKSTONE || neighborBlock == Blocks.SOUL_SAND || 
                        neighborBlock == Blocks.SOUL_SOIL) {
                        netherBlockNeighbors++;
                    }
                }
            }
        }

        if (netherBlockNeighbors < 3) {
            return OreCheckResult.FAKE_NO_SURROUNDING;
        }

        return OreCheckResult.REAL;
    }

    private boolean isStoneBlock(Block block) {
        return block == Blocks.STONE || block == Blocks.DEEPSLATE ||
               block == Blocks.GRANITE || block == Blocks.DIORITE ||
               block == Blocks.ANDESITE || block == Blocks.TUFF ||
               block == Blocks.BLACKSTONE;
    }

    private boolean hasBlockEntity(Block block, BlockState state) {
        return block == Blocks.SPAWNER || block == Blocks.CHEST ||
               block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST ||
               block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE ||
               block == Blocks.SMOKER || block == Blocks.HOPPER ||
               block == Blocks.DROPPER || block == Blocks.DISPENSER ||
               block == Blocks.BARREL || block == Blocks.SHULKER_BOX ||
               block == Blocks.BEACON || block == Blocks.BREWING_STAND ||
               block == Blocks.JUKEBOX || block == Blocks.NOTE_BLOCK ||
               block == Blocks.COMMAND_BLOCK || block == Blocks.REPEATING_COMMAND_BLOCK ||
               block == Blocks.CHAIN_COMMAND_BLOCK || 
               block == Blocks.CONDUIT || block == Blocks.BELL ||
               block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE ||
               block == Blocks.LECTERN || block == Blocks.CARTOGRAPHY_TABLE ||
               block == Blocks.FLETCHING_TABLE || block == Blocks.SMITHING_TABLE ||
               block == Blocks.LOOM || block == Blocks.GRINDSTONE ||
               block == Blocks.STONECUTTER || block == Blocks.COMPOSTER ||
               block == Blocks.REDSTONE_WIRE || block == Blocks.REPEATER || 
               block == Blocks.COMPARATOR || block == Blocks.REDSTONE_TORCH ||
               block == Blocks.REDSTONE_WALL_TORCH;
    }

    private OreType getOreType(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();
        if (this.diamond.getValue() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return OreType.DIAMOND;
        if (this.gold.getValue() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)) return OreType.GOLD;
        if (this.iron.getValue() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) return OreType.IRON;
        if (this.copper.getValue() && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)) return OreType.COPPER;
        if (this.coal.getValue() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) return OreType.COAL;
        if (this.ancientDebris.getValue() && block == Blocks.ANCIENT_DEBRIS) return OreType.ANCIENT_DEBRIS;
        return null;
    }

    private float[] getColor(BlockPos pos) {
        OreInfo info = this.oreInfoMap.get(pos);
        if (info == null) return DIAMOND_COLOR;
        
        OreType oreType = info.type;
        
        return switch (info.checkResult) {
            case FAKE_SUSPICIOUS, FAKE_NO_SURROUNDING -> FAKE_ORE_COLOR;
            case UNSAFE -> UNSAFE_ORE_COLOR;
            case REAL -> switch (oreType) {
                case DIAMOND -> DIAMOND_COLOR;
                case GOLD -> GOLD_COLOR;
                case IRON -> IRON_COLOR;
                case COPPER -> COPPER_COLOR;
                case COAL -> COAL_COLOR;
                case ANCIENT_DEBRIS -> ANCIENT_DEBRIS_COLOR;
            };
        };
    }

    private void clearState() {
        this.clearScanCache();
        this.lastUpdateTime = 0;
        this.lastScanSignature = Integer.MIN_VALUE;
    }

    private void clearScanCache() {
        this.oreInfoMap.clear();
        this.boxes.clear();
        this.chunkScans.clear();
    }

    private enum OreType {
        DIAMOND, GOLD, IRON, COPPER, COAL, ANCIENT_DEBRIS
    }

    private enum OreCheckResult {
        REAL,
        UNSAFE,
        FAKE_NO_SURROUNDING,
        FAKE_SUSPICIOUS
    }

    private static class OreInfo {
        OreType type;
        OreCheckResult checkResult;

        OreInfo(OreType type, OreCheckResult checkResult) {
            this.type = type;
            this.checkResult = checkResult;
        }
    }

    private record ChunkScan(Map<BlockPos, OreInfo> oreInfo) {
    }
}
