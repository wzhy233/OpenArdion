package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.render.Render3DEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.setting.ThemeSetting;
import io.github.wzhy233.ardion.util.render.Render3DUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ColorValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BedESP extends Module {
    private static final Direction[] OBSIDIAN_DIRECTIONS = {
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Color OBSIDIAN_COLOR = new Color(170, 0, 170);

    private final ModeValue mode = this.registerValue(new ModeValue("mode", this, "default",
            "default", "full"));
    private final ModeValue colorMode = this.registerValue(new ModeValue("color", this, "custom",
            "custom", "hud"));
    private final ColorValue customColor = this.registerValue(new ColorValue("custom-color", this,
            new Color(70, 215, 255).getRGB(), () -> this.colorMode.is("custom")));
    private final NumberValue opacity = this.registerValue(new NumberValue("opacity", this,
            0.25D, 0.05D, 1.0D));
    private final BooleanValue filled = this.registerValue(new BooleanValue("filled", this, true));
    private final BooleanValue outline = this.registerValue(new BooleanValue("outline", this, false));
    private final BooleanValue obsidian = this.registerValue(new BooleanValue("obsidian", this, true));
    private final NumberValue updateDelay = this.registerValue(new NumberValue("update-delay", this,
            750.0D, 100.0D, 3000.0D, true));
    private final Set<BlockPos> beds = new LinkedHashSet<>();
    private long lastUpdateTime;

    public BedESP() {
        super("bed-esp", ModuleCategory.RENDER);
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
        if (event.getWorld() == null || client.player == null) {
            this.clearState();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastUpdateTime < this.updateDelay.getValue().longValue()) {
            return;
        }
        this.lastUpdateTime = now;
        this.collectBeds(event.getWorld(), client);
    }

    @Listener
    public void onRender3D(Render3DEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getCameraEntity() == null || this.beds.isEmpty()) {
            return;
        }

        var matrices = event.getMatrices();
        var cameraPos = client.gameRenderer.getCamera().getCameraPos();
        Color bedColor = this.getBedColor();
        float alpha = this.opacity.getValue().floatValue();
        boolean flushOutline = this.outline.getValue();

        matrices.push();
        try {
            for (BlockPos headPos : new ArrayList<>(this.beds)) {
                BedPair pair = this.getValidBedPair(client.world, headPos);
                if (pair == null) {
                    this.beds.remove(headPos);
                    continue;
                }

                if (this.obsidian.getValue()) {
                    this.drawObsidian(client.world, event, pair, cameraPos);
                }
                this.drawBox(event, pair.bedBox(), cameraPos, bedColor, alpha, this.filled.getValue(), this.outline.getValue());
            }
        } finally {
            matrices.pop();
        }

        if (flushOutline) {
            Render3DUtil.flushOutline();
        }
    }

    private void collectBeds(ClientWorld world, MinecraftClient client) {
        Set<BlockPos> found = new LinkedHashSet<>();
        int viewDistance = client.options.getViewDistance().getValue();
        ChunkPos center = client.player.getChunkPos();

        for (int chunkX = center.x - viewDistance; chunkX <= center.x + viewDistance; chunkX++) {
            for (int chunkZ = center.z - viewDistance; chunkZ <= center.z + viewDistance; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk != null) {
                    this.collectChunkBeds(world, chunk, found);
                }
            }
        }

        this.beds.clear();
        this.beds.addAll(found);
    }

    private void collectChunkBeds(ClientWorld world, WorldChunk chunk, Set<BlockPos> found) {
        ChunkSection[] sections = chunk.getSectionArray();
        int chunkStartX = chunk.getPos().x << 4;
        int chunkStartZ = chunk.getPos().z << 4;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty() || !section.hasAny(BedESP::isBedState)) {
                continue;
            }

            int baseY = world.sectionIndexToCoord(sectionIndex) << 4;
            for (int localX = 0; localX < 16; localX++) {
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (isBedHead(state)) {
                            found.add(new BlockPos(chunkStartX + localX, baseY + localY, chunkStartZ + localZ));
                        }
                    }
                }
            }
        }
    }

    private BedPair getValidBedPair(ClientWorld world, BlockPos headPos) {
        BlockState headState = world.getBlockState(headPos);
        if (!isBedHead(headState)) {
            return null;
        }

        Direction facing = headState.get(BedBlock.FACING);
        BlockPos footPos = headPos.offset(facing.getOpposite());
        BlockState footState = world.getBlockState(footPos);
        if (!isBedFoot(footState)) {
            return null;
        }

        return new BedPair(headPos, footPos, this.getBedBox(headPos, footPos));
    }

    private Box getBedBox(BlockPos headPos, BlockPos footPos) {
        double minX = Math.min(headPos.getX(), footPos.getX());
        double minZ = Math.min(headPos.getZ(), footPos.getZ());
        double maxX = Math.max(headPos.getX() + 1.0D, footPos.getX() + 1.0D);
        double maxZ = Math.max(headPos.getZ() + 1.0D, footPos.getZ() + 1.0D);
        double y = headPos.getY();
        return new Box(minX, y, minZ, maxX, y + this.getHeight(), maxZ);
    }

    private double getHeight() {
        return this.mode.is("full") ? 1.0D : 0.5625D;
    }

    private void drawObsidian(ClientWorld world, Render3DEvent event, BedPair pair, Vec3d cameraPos) {
        for (Direction direction : OBSIDIAN_DIRECTIONS) {
            BlockPos headOffset = pair.head().offset(direction);
            BlockPos footOffset = pair.foot().offset(direction);
            boolean headObsidian = isObsidian(world.getBlockState(headOffset));
            boolean footObsidian = isObsidian(world.getBlockState(footOffset));

            if (headObsidian && footObsidian) {
                this.drawBox(event, new Box(headOffset).union(new Box(footOffset)), cameraPos,
                        OBSIDIAN_COLOR, this.opacity.getValue().floatValue(), true, this.outline.getValue());
            } else if (headObsidian) {
                this.drawBox(event, new Box(headOffset), cameraPos, OBSIDIAN_COLOR,
                        this.opacity.getValue().floatValue(), true, this.outline.getValue());
            } else if (footObsidian) {
                this.drawBox(event, new Box(footOffset), cameraPos, OBSIDIAN_COLOR,
                        this.opacity.getValue().floatValue(), true, this.outline.getValue());
            }
        }
    }

    private void drawBox(Render3DEvent event, Box box, Vec3d cameraPos, Color color, float alpha,
                         boolean filled, boolean outline) {
        if (!filled && !outline) {
            return;
        }

        Box shiftedBox = box.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Render3DUtil.drawBox(event.getMatrices(), event.getConsumers(), shiftedBox,
                color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F,
                alpha, filled, outline);
    }

    private Color getBedColor() {
        if (this.colorMode.is("hud")) {
            return new Color(ThemeSetting.getColor((int) (System.currentTimeMillis() * 0.01F)), true);
        }
        return this.customColor.getValue();
    }

    private void clearState() {
        this.beds.clear();
        this.lastUpdateTime = 0L;
    }

    private static boolean isBedState(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    private static boolean isBedHead(BlockState state) {
        return state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean isBedFoot(BlockState state) {
        return state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.FOOT;
    }

    private static boolean isObsidian(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN);
    }

    private record BedPair(BlockPos head, BlockPos foot, Box bedBox) {
    }
}
