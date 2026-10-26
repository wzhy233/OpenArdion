package io.github.wzhy233.ardion.module.impl.world;

import io.github.wzhy233.ardion.event.impl.client.ClickEvent;
import io.github.wzhy233.ardion.event.impl.player.MotionEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.impl.render.Render3DEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.mixin.client.ClientPlayerInteractionManagerAccessor;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.setting.ThemeSetting;
import io.github.wzhy233.ardion.util.network.PacketUtil;
import io.github.wzhy233.ardion.util.player.InventoryUtil;
import io.github.wzhy233.ardion.util.render.Render3DUtil;
import io.github.wzhy233.ardion.util.rotation.RayTraceUtil;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.util.rotation.RotationUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BedNuker extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Direction[] COVER_SIDES = {
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final long BED_DELAY_MS = 500L;
    private static final long PROGRESS_LINGER_MS = 400L;

    private final NumberValue range = this.registerValue(new NumberValue("range", this, 4.5D, 3.0D, 6.0D));
    private final BooleanValue swing = this.registerValue(new BooleanValue("swing", this, true));
    private final BooleanValue showProgress = this.registerValue(new BooleanValue("show-progress", this, true));
    private final BooleanValue showTarget = this.registerValue(new BooleanValue("show-target", this, true));

    private BlockPos target;
    private int stage;
    private float breakProgress;
    private boolean bed;
    private boolean breaking;
    private int savedSlot = -1;
    private int breakingSlot = -1;
    private int lastSwingAge = -100;
    private long lastBedAt;
    private BlockPos progressBed;
    private boolean progressExposed;
    private float lastOverall;
    private long lastBreakingAt;

    static {
        if (phased(false, false, 1.0F) != 0.5F
                || phased(true, true, 1.0F) != 1.0F
                || phased(true, false, 0.5F) != 0.5F) {
            throw new IllegalStateException("bed nuker progress");
        }
    }

    public BedNuker() {
        super("bed-nuker", ModuleCategory.WORLD);
    }

    public boolean isBusy() {
        return this.target != null;
    }

    public boolean showIslandProgress() {
        if (!this.isEnabled() || !this.showProgress.getValue()) {
            return false;
        }
        if (this.breaking) {
            return true;
        }
        return this.lastBreakingAt > 0L && System.currentTimeMillis() - this.lastBreakingAt < PROGRESS_LINGER_MS;
    }

    public float islandProgress() {
        return this.overall();
    }

    @Override
    protected void onDisable() {
        this.resetBreaking();
        this.clearProgress();
        RotationManager.get().setActive(false);
    }

    @Listener
    private void onWorld(WorldLoadEvent event) {
        this.resetBreaking();
        this.clearProgress();
    }

    @Listener(Priority.HIGH)
    private void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE || mc.player == null || mc.world == null) {
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        if (this.target != null) {
            boolean air = mc.world.getBlockState(this.target).isAir();
            if (air || !this.inReach(this.target, eye)) {
                if (air && this.bed) {
                    this.markBedBroken();
                }
                this.resetBreaking();
            } else if (!this.bed) {
                BlockPos nearest = this.findTarget(eye);
                if (nearest != null && mc.world.getBlockState(nearest).getBlock() instanceof BedBlock) {
                    this.resetBreaking();
                }
            }
        }

        if (this.target == null
                && mc.player.getAbilities().allowModifyWorld
                && System.currentTimeMillis() - this.lastBedAt >= BED_DELAY_MS) {
            this.target = this.findTarget(eye);
            this.stage = 0;
            this.breakProgress = 0.0F;
            this.breaking = false;
            this.bed = this.target != null && mc.world.getBlockState(this.target).getBlock() instanceof BedBlock;
            if (this.target != null && !this.bed) {
                BlockPos bedPos = this.canonical(this.nearestBed(eye));
                if (bedPos != null) {
                    this.progressBed = bedPos;
                    this.progressExposed = true;
                }
            }
        }

        if (this.target == null) {
            return;
        }

        Vec3d center = Vec3d.ofCenter(this.target);
        RotationManager.get().setRotations(RotationUtil.calculate(eye, center), 180.0D, true, false);
    }

    @Listener
    private void onMotion(MotionEvent event) {
        if (event.getStage() != MotionEvent.Stage.POST || this.target == null
                || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.world.getBlockState(this.target).isAir()) {
            return;
        }

        this.equipTool();
        Direction face = this.hitFacing(this.target);
        if (this.stage == 0) {
            if (mc.player.isUsingItem()) {
                return;
            }
            this.swing(true);
            if (!mc.interactionManager.attackBlock(this.target, face)) {
                return;
            }
            this.swing(true);
            if (mc.world.getBlockState(this.target).isAir()) {
                if (this.bed) {
                    this.markBedBroken();
                }
                this.stage = 2;
            } else {
                this.stage = 1;
                this.breaking = true;
                this.lastBreakingAt = System.currentTimeMillis();
            }
        } else if (this.stage == 1) {
            this.breaking = true;
            this.lastBreakingAt = System.currentTimeMillis();
            BlockState state = mc.world.getBlockState(this.target);
            this.breakProgress += state.calcBlockBreakingDelta(mc.player, mc.world, this.target);
            mc.interactionManager.updateBlockBreakingProgress(this.target, face);
            this.swing(false);
            if (mc.world.getBlockState(this.target).isAir()) {
                if (this.bed) {
                    this.markBedBroken();
                }
                this.breaking = false;
                this.stage = 2;
            }
        }

        if (this.stage == 2) {
            this.resetBreaking();
        }
    }

    @Listener
    private void onClick(ClickEvent event) {
        if (this.target != null) {
            event.setCancelled(true);
        }
    }

    @Listener
    private void onRender3D(Render3DEvent event) {
        if (!this.showTarget.getValue() || mc.world == null || mc.player == null || this.target == null
                || mc.world.getBlockState(this.target).isAir()) {
            return;
        }

        Color color = new Color(ThemeSetting.getColor(0), true);
        double height = this.bed ? 0.5625D : 1.0D;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        Box box = new Box(this.target.getX(), this.target.getY(), this.target.getZ(),
                this.target.getX() + 1.0D, this.target.getY() + height, this.target.getZ() + 1.0D)
                .offset(-camera.x, -camera.y, -camera.z);
        Render3DUtil.drawBox(event.getMatrices(), event.getConsumers(), box,
                color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F,
                63.0F / 255.0F, true, false);
    }

    private void equipTool() {
        PlayerInventory inventory = mc.player.getInventory();
        if (this.savedSlot == -1) {
            this.savedSlot = inventory.getSelectedSlot();
            this.breakingSlot = this.bestSlot(mc.world.getBlockState(this.target));
        }
        if (this.breakingSlot >= 0 && inventory.getSelectedSlot() != this.breakingSlot) {
            inventory.setSelectedSlot(this.breakingSlot);
        }
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).ardion$syncSelectedSlot();
    }

    private void restoreSlot() {
        if (this.savedSlot == -1 || mc.player == null) {
            this.savedSlot = -1;
            this.breakingSlot = -1;
            return;
        }
        mc.player.getInventory().setSelectedSlot(this.savedSlot);
        if (mc.interactionManager != null) {
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).ardion$syncSelectedSlot();
        }
        this.savedSlot = -1;
        this.breakingSlot = -1;
    }

    private void resetBreaking() {
        this.restoreSlot();
        if (this.target != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        this.target = null;
        this.stage = 0;
        this.breakProgress = 0.0F;
        this.bed = false;
        this.breaking = false;
    }

    private void markBedBroken() {
        this.lastBedAt = System.currentTimeMillis();
        this.lastOverall = 1.0F;
    }

    private void clearProgress() {
        this.progressBed = null;
        this.progressExposed = false;
        this.lastOverall = 0.0F;
        this.lastBreakingAt = 0L;
    }

    private void swing(boolean force) {
        if (!force && mc.player.age - this.lastSwingAge < 6) {
            return;
        }
        this.lastSwingAge = mc.player.age;
        if (this.swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else if (mc.getNetworkHandler() != null) {
            PacketUtil.sendQueued(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    private BlockPos findTarget(Vec3d eye) {
        int x = MathHelper.floor(eye.x);
        int y = MathHelper.floor(eye.y);
        int z = MathHelper.floor(eye.z);
        List<BlockPos> beds = new ArrayList<>();
        for (int i = x - 6; i <= x + 6; i++) {
            for (int j = y - 6; j <= y + 6; j++) {
                for (int k = z - 6; k <= z + 6; k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    if (mc.world.getBlockState(pos).getBlock() instanceof BedBlock && this.inReach(pos, eye)) {
                        beds.add(pos);
                    }
                }
            }
        }
        beds.sort(Comparator.comparingDouble(pos -> pos.toCenterPos().squaredDistanceTo(eye)));
        for (BlockPos pos : beds) {
            BlockPos cover = this.cover(pos, eye);
            if (cover != null) {
                if (this.needsPick(mc.world.getBlockState(cover)) && !this.hasPickaxe()) {
                    continue;
                }
                return cover;
            }
            return pos;
        }
        return null;
    }

    private BlockPos cover(BlockPos bedPos, Vec3d eye) {
        BlockState state = mc.world.getBlockState(bedPos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        Direction facing = state.get(BedBlock.FACING);
        BlockPos other = bedPos.offset(state.get(BedBlock.PART) == BedPart.HEAD ? facing.getOpposite() : facing);
        List<BlockPos> covers = new ArrayList<>();
        for (BlockPos half : List.of(bedPos, other)) {
            for (Direction side : COVER_SIDES) {
                BlockPos neighbor = half.offset(side);
                BlockState neighborState = mc.world.getBlockState(neighbor);
                if (neighborState.isReplaceable()) {
                    return null;
                }
                if (!(neighborState.getBlock() instanceof BedBlock)) {
                    covers.add(neighbor);
                }
            }
        }
        if (covers.isEmpty()) {
            return null;
        }
        covers.sort((a, b) -> {
            int order = Float.compare(this.breakRank(b), this.breakRank(a));
            return order != 0 ? order : Double.compare(a.toCenterPos().squaredDistanceTo(eye), b.toCenterPos().squaredDistanceTo(eye));
        });
        return covers.getFirst();
    }

    private BlockPos nearestBed(Vec3d eye) {
        int x = MathHelper.floor(eye.x);
        int y = MathHelper.floor(eye.y);
        int z = MathHelper.floor(eye.z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = x - 6; i <= x + 6; i++) {
            for (int j = y - 6; j <= y + 6; j++) {
                for (int k = z - 6; k <= z + 6; k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    if (!(mc.world.getBlockState(pos).getBlock() instanceof BedBlock) || !this.inReach(pos, eye)) {
                        continue;
                    }
                    double distance = pos.toCenterPos().squaredDistanceTo(eye);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private BlockPos canonical(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return null;
        }
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        if (state.get(BedBlock.PART) == BedPart.HEAD) {
            return pos.offset(state.get(BedBlock.FACING).getOpposite());
        }
        return pos;
    }

    private float overall() {
        if (mc.player == null || mc.world == null || this.target == null) {
            return this.lastOverall;
        }
        BlockPos bedPos = this.canonical(this.bed ? this.target : this.nearestBed(mc.player.getEyePos()));
        if (bedPos == null) {
            return this.lastOverall;
        }
        if (!bedPos.equals(this.progressBed)) {
            this.progressBed = bedPos;
            this.progressExposed = false;
        }
        float overall = phased(this.bed, this.progressExposed, this.blockProgress());
        if (!this.bed) {
            this.progressExposed = true;
        }
        this.lastOverall = overall;
        return overall;
    }

    private float blockProgress() {
        return this.target == null ? 0.0F : Math.min(1.0F, Math.max(0.0F, this.breakProgress));
    }

    private static float phased(boolean bedBlock, boolean exposed, float fraction) {
        float value = bedBlock ? (exposed ? 0.5F + 0.5F * fraction : fraction) : 0.5F * fraction;
        return Math.min(1.0F, Math.max(0.0F, value));
    }

    private boolean inReach(BlockPos pos, Vec3d eye) {
        double reach = this.range.getValue();
        return pos.toCenterPos().squaredDistanceTo(eye) < reach * reach;
    }

    private boolean needsPick(BlockState state) {
        return state.isToolRequired();
    }

    private boolean hasPickaxe() {
        PlayerInventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (InventoryUtil.isPickaxe(inventory.getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private int bestSlot(BlockState state) {
        PlayerInventory inventory = mc.player.getInventory();
        int best = inventory.getSelectedSlot();
        float bestScore = this.toolScore(inventory.getStack(best), state);
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            float score = this.toolScore(inventory.getStack(slot), state);
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private float breakRank(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        float hardness = state.getHardness(mc.world, pos);
        if (hardness <= 0.0F) {
            return hardness < 0.0F ? 0.0F : 1000.0F;
        }
        ItemStack stack = mc.player.getInventory().getStack(this.bestSlot(state));
        boolean harvest = !state.isToolRequired() || stack.isSuitableFor(state);
        return this.toolScore(stack, state) / hardness / (harvest ? 30.0F : 100.0F);
    }

    private float toolScore(ItemStack stack, BlockState state) {
        if (stack == null || stack.isEmpty()) {
            return 1.0F;
        }
        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1.0F) {
            int efficiency = this.efficiency(stack);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1.0F;
            }
        }
        return speed;
    }

    private int efficiency(ItemStack stack) {
        if (mc.world == null) {
            return 0;
        }
        RegistryEntry<Enchantment> enchantment = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
        return EnchantmentHelper.getLevel(enchantment, stack);
    }

    private Direction hitFacing(BlockPos pos) {
        Vec3d point = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D);
        Rotation rotation = RotationUtil.calculate(mc.player.getEyePos(), point);
        HitResult hit = RayTraceUtil.rayCast(8.0D, rotation.getYaw(), rotation.getPitch());
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getSide();
        }
        return Direction.UP;
    }
}
