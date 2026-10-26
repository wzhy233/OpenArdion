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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerESP extends Module {
    private static final float[] CHEST_COLOR = {98.0F / 255.0F, 205.0F / 255.0F, 98.0F / 255.0F};
    private static final float[] ENDER_CHEST_COLOR = {161.0F / 255.0F, 0.0F, 1.0F};
    private static final float[] OPENED_COLOR = {1.0F, 0.0F, 80.0F / 255.0F};

    private final BooleanValue chests = this.registerValue(new BooleanValue("chests", this, true));
    private final BooleanValue enderChests = this.registerValue(new BooleanValue("ender-chests", this, true));
    private final BooleanValue filled = this.registerValue(new BooleanValue("filled", this, false));
    private final BooleanValue outline = this.registerValue(new BooleanValue("outline", this, true));
    private final NumberValue alpha = this.registerValue(new NumberValue("alpha", this, 0.25D, 0.05D, 1.0D));
    private final Map<BlockPos, ContainerType> containers = new LinkedHashMap<>();
    private final Map<BlockPos, Box> boxes = new LinkedHashMap<>();
    private final Map<BlockPos, Boolean> openedContainers = new ConcurrentHashMap<>();

    public ContainerESP() {
        super("container-esp", ModuleCategory.RENDER);
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
        ClientWorld world = event.getWorld();
        MinecraftClient client = MinecraftClient.getInstance();
        if (world == null || client.player == null) {
            this.clearState();
            return;
        }

        this.collectContainers(world, client);
        this.updateOpenedState(world, client);
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
                BlockPos pos = entry.getKey();
                float[] color = this.getColor(pos);
                Box shiftedBox = entry.getValue().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Render3DUtil.drawBox(matrices, event.getConsumers(), shiftedBox, color[0], color[1], color[2], alphaValue, this.filled.getValue(), this.outline.getValue());
            }
        } finally {
            matrices.pop();
        }

        if (this.outline.getValue()) {
            Render3DUtil.flushOutline();
        }
    }

    private void collectContainers(ClientWorld world, MinecraftClient client) {
        this.containers.clear();
        this.boxes.clear();

        int viewDistance = client.options.getViewDistance().getValue();
        ChunkPos center = client.player.getChunkPos();
        int minChunkX = center.x - viewDistance;
        int maxChunkX = center.x + viewDistance;
        int minChunkZ = center.z - viewDistance;
        int maxChunkZ = center.z + viewDistance;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    ContainerType type = this.getContainerType(blockEntity);
                    if (type == null) {
                        continue;
                    }

                    BlockPos pos = blockEntity.getPos();
                    this.containers.put(pos, type);
                    this.boxes.put(pos, this.getContainerBox(pos, type));
                }
            }
        }

        this.openedContainers.keySet().removeIf(pos -> !this.containers.containsKey(pos));
    }

    private void updateOpenedState(ClientWorld world, MinecraftClient client) {
        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        for (Map.Entry<BlockPos, ContainerType> entry : this.containers.entrySet()) {
            BlockEntity blockEntity = world.getBlockEntity(entry.getKey());
            boolean opened = switch (entry.getValue()) {
                case CHEST -> blockEntity instanceof ChestBlockEntity chestBlockEntity && chestBlockEntity.getAnimationProgress(tickDelta) > 0.1F;
                case ENDER_CHEST -> blockEntity instanceof EnderChestBlockEntity enderChestBlockEntity && enderChestBlockEntity.getAnimationProgress(tickDelta) > 0.1F;
            };

            if (opened) {
                this.openedContainers.put(entry.getKey(), Boolean.TRUE);
            }
        }
    }

    private ContainerType getContainerType(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        if (this.chests.getValue() && blockEntity instanceof ChestBlockEntity) {
            return ContainerType.CHEST;
        }
        if (this.enderChests.getValue() && blockEntity instanceof EnderChestBlockEntity) {
            return ContainerType.ENDER_CHEST;
        }
        return null;
    }

    private Box getContainerBox(BlockPos pos, ContainerType type) {
        return switch (type) {
            case CHEST, ENDER_CHEST -> this.getInsetBox(pos, 0.0625D, 0.0D, 0.875D);
        };
    }

    private Box getInsetBox(BlockPos pos, double horizontalInset, double minY, double maxY) {
        return new Box(
                pos.getX() + horizontalInset,
                pos.getY() + minY,
                pos.getZ() + horizontalInset,
                pos.getX() + 1.0D - horizontalInset,
                pos.getY() + maxY,
                pos.getZ() + 1.0D - horizontalInset
        );
    }

    private float[] getColor(BlockPos pos) {
        if (this.openedContainers.containsKey(pos)) {
            return OPENED_COLOR;
        }

        ContainerType type = this.containers.get(pos);
        if (type == ContainerType.ENDER_CHEST) {
            return ENDER_CHEST_COLOR;
        }
        return CHEST_COLOR;
    }

    private void clearState() {
        this.containers.clear();
        this.boxes.clear();
        this.openedContainers.clear();
    }

    private enum ContainerType {
        CHEST,
        ENDER_CHEST
    }
}
