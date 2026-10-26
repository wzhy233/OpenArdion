package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.impl.render.UpdateHeldItemEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.util.rotation.RotationUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class AutoMLG extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final float PITCH_ONLY_YAW_NUDGE = 0.2F;
    private static final int OFFHAND_SLOT = -2;

    private final NumberValue triggerDistance = this.registerValue(new NumberValue("fall-distance", this, 3.0D, 3.0D, 5.0D, true));
    private final NumberValue predictTicks = this.registerValue(new NumberValue("predict-ticks", this, 2.0D, 1.0D, 5.0D, true));
    private final BooleanValue solidCheck = this.registerValue(new BooleanValue("solid-check", this, true));
    private final BooleanValue recovery = this.registerValue(new BooleanValue("recovery", this, true));

    private double lastY;
    private float accumulatedFall;
    private Integer slotToRestore;
    private boolean waterPlaced;
    private boolean recoveryActive;
    private int recoveryDelay;
    private int recoveryCountdown;
    private Integer bucketSlot;
    private BlockPos placedWaterPos;
    private int postPlaceCooldown;
    private int postActionCooldown;

    public AutoMLG() {
        super("auto-mlg", ModuleCategory.PLAYER);
    }

    @Override
    protected void onEnable() {
        this.reset();
        this.lastY = mc.player == null ? 0.0D : mc.player.getY();
    }

    @Override
    protected void onDisable() {
        RotationManager.get().setActive(false);
        this.restoreSlot();
        this.reset();
    }

    @Listener(Priority.HIGHEST)
    private void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.player.isGliding() || mc.player.getAbilities().flying || mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }
        if (this.isHoldingMace()) {
            RotationManager.get().setActive(false);
            this.restoreSlot();
            this.reset();
            this.lastY = mc.player.getY();
            return;
        }

        this.tickFallDistance();
        this.tickCooldowns();
        this.restoreSlot();

        if (mc.player.isOnGround() || this.accumulatedFall <= 0.0F) {
            this.waterPlaced = false;
        }

        if (this.handleRecovery()) {
            return;
        }

        if (this.tryFillEmptyBucket()) {
            return;
        }

        if (this.waterPlaced || this.postPlaceCooldown > 0 || this.accumulatedFall < this.triggerDistance.getValue()) {
            return;
        }

        int slot = this.findHotbarItem(Items.WATER_BUCKET);
        if (slot == -1 || this.ticksUntilGround() > this.predictTicks.getValue().intValue()) {
            return;
        }

        BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (this.solidCheck.getValue() && !this.hasSolidBelow(playerPos)) {
            return;
        }

        Rotation rotation = this.getDownRotation();
        BlockHitResult hit = this.raycastSolid(rotation, 5.0D);
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }

        this.placeWater(slot, rotation);
    }

    @Listener
    private void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (event.getHand() != Hand.MAIN_HAND || mc.player == null || this.slotToRestore == null) {
            return;
        }

        event.setItemStack(mc.player.getInventory().getStack(this.slotToRestore));
    }

    private Rotation getDownRotation() {
        float yaw = mc.player.getYaw();
        Rotation lastServerRotation = RotationManager.get().getLastServerRotations();
        if (lastServerRotation != null && Math.abs(MathHelper.wrapDegrees(yaw - lastServerRotation.getYaw())) < 1.0E-4F) {
            yaw += PITCH_ONLY_YAW_NUDGE;
        }
        return new Rotation(yaw, 90.0F);
    }

    public boolean isRotationPriorityActive() {
        return !this.isHoldingMace()
                && (this.waterPlaced || this.recoveryActive || this.postPlaceCooldown > 0 || this.postActionCooldown > 0
                || this.shouldPlaceWaterNow() || this.shouldRecoverWaterNow());
    }

    private boolean isHoldingMace() {
        return mc.player != null
                && (mc.player.getMainHandStack().isOf(Items.MACE) || mc.player.getOffHandStack().isOf(Items.MACE));
    }

    private boolean shouldPlaceWaterNow() {
        return mc.player != null && mc.world != null && !this.waterPlaced && this.postPlaceCooldown <= 0
                && this.accumulatedFall >= this.triggerDistance.getValue()
                && this.findHotbarItem(Items.WATER_BUCKET) != -1
                && this.ticksUntilGround() <= this.predictTicks.getValue().intValue();
    }

    private boolean shouldRecoverWaterNow() {
        return mc.player != null && mc.world != null && this.recoveryActive;
    }

    private boolean handleRecovery() {
        if (!this.recoveryActive) {
            return false;
        }
        if (this.recoveryDelay > 0) {
            this.recoveryDelay--;
            return true;
        }
        if (this.recoveryCountdown-- <= 0) {
            this.stopRecovery();
            return false;
        }

        if (this.bucketSlot == null) {
            int slot = this.findHotbarItem(Items.BUCKET);
            if (slot == -1) {
                this.stopRecovery();
                return false;
            }
            this.bucketSlot = slot;
        }

        if (this.getStack(this.bucketSlot).isOf(Items.WATER_BUCKET)) {
            this.stopRecovery();
            this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 1);
            return false;
        }
        if (this.placedWaterPos == null || !this.isWaterSource(this.placedWaterPos)) {
            this.stopRecovery();
            return false;
        }

        Rotation rotation = RotationUtil.calculate(this.placedWaterPos);
        BlockHitResult hit = this.raycastFluid(rotation, 4.5D);
        if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(this.placedWaterPos)) {
            this.stopRecovery();
            return false;
        }

        this.selectSlot(this.bucketSlot);
        this.useItem(rotation, this.handForSlot(this.bucketSlot));
        return true;
    }

    private boolean tryFillEmptyBucket() {
        if (this.waterPlaced || this.recoveryActive || this.placedWaterPos != null || this.postPlaceCooldown > 0
                || this.postActionCooldown > 0 || this.accumulatedFall > 0.5F || this.findHotbarItem(Items.WATER_BUCKET) != -1) {
            return false;
        }

        int slot = this.findHotbarItem(Items.BUCKET);
        BlockPos waterPos = slot == -1 ? null : this.findBucketPos();
        if (waterPos == null) {
            return false;
        }

        Rotation rotation = RotationUtil.calculate(waterPos);
        BlockHitResult hit = this.raycastFluid(rotation, 4.5D);
        if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(waterPos)) {
            return false;
        }

        this.selectSlot(slot);
        this.useItem(rotation, this.handForSlot(slot));
        this.postActionCooldown = 8;
        this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 1);
        return true;
    }

    private void placeWater(int slot, Rotation rotation) {
        this.selectSlot(slot);
        this.useItem(rotation, this.handForSlot(slot));
        this.waterPlaced = true;
        this.recoveryActive = this.recovery.getValue();
        this.recoveryDelay = 3;
        this.recoveryCountdown = this.recoveryActive ? 2 : 0;
        this.bucketSlot = null;
        this.placedWaterPos = this.getPlacementBlockPos(rotation);
        this.postPlaceCooldown = Math.max(this.postPlaceCooldown, 1);
    }

    private void useItem(Rotation rotation, Hand hand) {
        RotationManager rotationManager = RotationManager.get();
        rotationManager.setRotations(rotation, 180.0D, true, false);
        Rotation packetRotation = rotationManager.getRotations();
        if (packetRotation == null) {
            packetRotation = rotation;
        }

        float oldYaw = mc.player.getYaw();
        float oldPitch = mc.player.getPitch();
        mc.player.setYaw(packetRotation.getYaw());
        mc.player.setPitch(packetRotation.getPitch());
        ActionResult result = mc.interactionManager.interactItem(mc.player, hand);
        if (result.isAccepted()) {
            mc.player.swingHand(hand);
        }
        mc.player.setYaw(oldYaw);
        mc.player.setPitch(oldPitch);
    }

    private int ticksUntilGround() {
        if (mc.player.getVelocity().y >= 0.0D) {
            return 999;
        }

        double distance = this.distanceToGround(30.0D);
        if (distance == Double.POSITIVE_INFINITY) {
            return 999;
        }

        double simulatedDrop = 0.0D;
        double simulatedVelocity = mc.player.getVelocity().y;
        for (int tick = 1; tick <= 20; tick++) {
            simulatedDrop += simulatedVelocity;
            simulatedVelocity = (simulatedVelocity - 0.08D) * 0.98D;
            if (Math.abs(simulatedDrop) >= distance) {
                return tick;
            }
        }
        return 999;
    }

    private BlockPos findBucketPos() {
        BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (int y = -1; y <= 1; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!this.isWaterSource(pos)) {
                        continue;
                    }

                    double distance = mc.player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                    if (distance >= closestDistance) {
                        continue;
                    }

                    Rotation rotation = RotationUtil.calculate(pos);
                    BlockHitResult hit = this.raycastFluid(rotation, 4.5D);
                    if (hit.getType() != HitResult.Type.MISS && hit.getBlockPos().equals(pos)) {
                        closest = pos;
                        closestDistance = distance;
                    }
                }
            }
        }

        return closest;
    }

    private BlockPos getPlacementBlockPos(Rotation rotation) {
        BlockHitResult hit = this.raycastSolid(rotation, 4.5D);
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        return hit.getBlockPos().offset(hit.getSide());
    }

    private BlockHitResult raycastSolid(Rotation rotation, double range) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d direction = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());
        Vec3d end = eye.add(direction.multiply(range));
        return mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }

    private BlockHitResult raycastFluid(Rotation rotation, double range) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d direction = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());
        Vec3d end = eye.add(direction.multiply(range));
        return mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));
    }

    private double distanceToGround(double maxDistance) {
        Vec3d start = new Vec3d(mc.player.getX(), mc.player.getBoundingBox().minY, mc.player.getZ());
        Vec3d end = start.add(0.0D, -maxDistance, 0.0D);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        return start.y - hit.getPos().y;
    }

    private void tickFallDistance() {
        if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava()) {
            this.accumulatedFall = 0.0F;
        } else {
            double deltaY = mc.player.getY() - this.lastY;
            if (deltaY < 0.0D) {
                this.accumulatedFall -= (float) deltaY;
            }
        }
        this.lastY = mc.player.getY();
    }

    private void tickCooldowns() {
        if (this.postPlaceCooldown > 0) {
            this.postPlaceCooldown--;
        }
        if (this.postActionCooldown > 0) {
            this.postActionCooldown--;
        }
    }

    private boolean hasSolidBelow(BlockPos pos) {
        return this.isSolidNonMenu(pos.down()) || this.isSolidNonMenu(pos.down(2));
    }

    private boolean isSolidNonMenu(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.getCollisionShape(mc.world, pos).isEmpty() && state.createScreenHandlerFactory(mc.world, pos) == null;
    }

    private boolean isWaterSource(BlockPos pos) {
        FluidState state = mc.world.getFluidState(pos);
        return state.isOf(Fluids.WATER) && state.isStill();
    }

    private int findHotbarItem(net.minecraft.item.Item item) {
        if (mc.player.getOffHandStack().isOf(item)) {
            return OFFHAND_SLOT;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private void selectSlot(int slot) {
        if (slot == OFFHAND_SLOT) {
            return;
        }
        if (slot < 0 || slot > 8 || mc.player.getInventory().getSelectedSlot() == slot) {
            return;
        }
        this.slotToRestore = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void restoreSlot() {
        if (mc.player != null && this.slotToRestore != null) {
            mc.player.getInventory().setSelectedSlot(this.slotToRestore);
            this.slotToRestore = null;
        }
    }

    private ItemStack getStack(int slot) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        return slot == OFFHAND_SLOT ? mc.player.getOffHandStack() : mc.player.getInventory().getStack(slot);
    }

    private Hand handForSlot(int slot) {
        return slot == OFFHAND_SLOT ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    private void stopRecovery() {
        this.recoveryActive = false;
        this.bucketSlot = null;
        this.placedWaterPos = null;
    }

    private void reset() {
        this.slotToRestore = null;
        this.waterPlaced = false;
        this.recoveryActive = false;
        this.recoveryDelay = 0;
        this.recoveryCountdown = 0;
        this.bucketSlot = null;
        this.placedWaterPos = null;
        this.postPlaceCooldown = 0;
        this.postActionCooldown = 0;
        this.accumulatedFall = 0.0F;
    }
}
