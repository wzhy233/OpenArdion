package io.github.wzhy233.ardion.module.impl.combat;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.player.AttackEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.impl.render.Render3DEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.manager.TargetManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.player.AutoMLG;
import io.github.wzhy233.ardion.module.impl.world.Scaffold;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.network.PacketUtil;
import io.github.wzhy233.ardion.util.render.Render3DUtil;
import io.github.wzhy233.ardion.util.rotation.RayTraceUtil;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class KillAura extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final ModeValue attackMode = this.registerValue(new ModeValue("attack-mode", this, "1.8", "1.8", "1.9+"));
    private final ModeValue targetMode = this.registerValue(new ModeValue("mode", this, "single", "single", "switch"));
    private final ModeValue sortMode = this.registerValue(new ModeValue("sort", this, "distance", "distance", "health", "hurt-time"));
    private final NumberValue attackRange = this.registerValue(new NumberValue("attack-range", this, 3.0D, 3.0D, 6.0D));
    private final NumberValue targetRange = this.registerValue(new NumberValue("target-range", this, 3.5D, 3.0D, 6.0D));
    private final NumberValue maxAps = this.registerValue(new NumberValue("max-aps", this, 14.0D, 1.0D, 20.0D, true,
            () -> this.attackMode.is("1.8")));
    private final NumberValue minAps = this.registerValue(new NumberValue("min-aps", this, 10.0D, 1.0D, 20.0D, true,
            () -> this.attackMode.is("1.8")));
    private final BooleanValue airUntilCritical = this.registerValue(new BooleanValue("air-until-critical", this, false,
            () -> this.attackMode.is("1.9+")));
    private final NumberValue switchDelay = this.registerValue(new NumberValue("switch-delay", this, 150.0D, 0.0D, 1000.0D, true,
            () -> this.targetMode.is("switch")));
    private final NumberValue rotationSpeed = this.registerValue(new NumberValue("rotation-speed", this, 180.0D, 1.0D, 180.0D, true));
    private final ModeValue fixMode = this.registerValue(new ModeValue("move-fix", this, "strict", "silent", "strict"));
    private final BooleanValue lockView = this.registerValue(new BooleanValue("lock-view", this, false));
    private final NumberValue fov = this.registerValue(new NumberValue("fov", this, 360.0D, 30.0D, 360.0D, true));
    private final BooleanValue aimClosestPoint = this.registerValue(new BooleanValue("aim-closest-point", this, false));
    private final BooleanValue advancedPrediction = this.registerValue(new BooleanValue("prediction", this, true));
    private final ModeValue advancedPredictionMode = this.registerValue(new ModeValue("prediction-mode", this, "smart",
            this.advancedPrediction::getValue, "simple", "smart"));
    private final BooleanValue nonLinearSpeed = this.registerValue(new BooleanValue("non-linear-speed", this, false));
    private final BooleanValue rotationStacking = this.registerValue(new BooleanValue("rotation-stacking", this, false));
    private final NumberValue stackingThreshold = this.registerValue(new NumberValue("stacking-threshold", this, 5.0D, 1.0D, 30.0D,
            this.rotationStacking::getValue));
    private final BooleanValue requirePress = this.registerValue(new BooleanValue("require-press", this, false));
    private final BooleanValue ignoreUsingItem = this.registerValue(new BooleanValue("ignore-using-item", this, false));
    private final BooleanValue rayTrace = this.registerValue(new BooleanValue("ray-trace", this, true));
    public final BooleanValue mark = this.registerValue(new BooleanValue("target-mark", this, true));
    private final BooleanValue randomization = this.registerValue(new BooleanValue("randomization", this, false));
    private final NumberValue yawRandomRangeMin = this.registerValue(new NumberValue("yaw-random-min", this, 20.0D, 0.0D, 100.0D, true,
            this.randomization::getValue));
    private final NumberValue yawRandomRangeMax = this.registerValue(new NumberValue("yaw-random-max", this, 30.0D, 0.0D, 100.0D, true,
            this.randomization::getValue));
    private final NumberValue pitchRandomRangeMin = this.registerValue(new NumberValue("pitch-random-min", this, 10.0D, 0.0D, 100.0D, true,
            this.randomization::getValue));
    private final NumberValue pitchRandomRangeMax = this.registerValue(new NumberValue("pitch-random-max", this, 20.0D, 0.0D, 100.0D, true,
            this.randomization::getValue));
    private final NumberValue noiseSpeed = this.registerValue(new NumberValue("noise-speed", this, 0.5D, 0.1D, 2.0D,
            this.randomization::getValue));
    private final NumberValue noiseDelay = this.registerValue(new NumberValue("noise-delay", this, 50.0D, 0.0D, 500.0D, true,
            this.randomization::getValue));

    private AttackData target;
    private int switchTick;
    private boolean hitRegistered;
    private long attackDelayMs;
    private long lastSwitchMs;
    private long noiseTimeOffset;
    private long lastNoiseUpdate;
    private float lastYawOffset;
    private float lastPitchOffset;
    private float cachedYawOffset;
    private float cachedPitchOffset;
    private Entity advancedLastSmartTarget;
    private Vec3d advancedLastSmartVelocity = Vec3d.ZERO;
    private long advancedLastSmartUpdateTime;
    private float stackingCachedYaw;
    private float stackingCachedPitch;
    private boolean stackingCacheValid;
    private boolean criticalJumpArmed;

    public KillAura() {
        super("kill-aura", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        TriggerBot triggerBot = ModuleManager.getModule(TriggerBot.class);
        if (triggerBot != null && triggerBot.isEnabled()) {
            triggerBot.setEnabled(false);
        }
        this.target = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMs = 0L;
        this.lastSwitchMs = 0L;
        this.noiseTimeOffset = System.currentTimeMillis();
        this.lastNoiseUpdate = 0L;
        this.lastYawOffset = 0.0F;
        this.lastPitchOffset = 0.0F;
        this.cachedYawOffset = 0.0F;
        this.cachedPitchOffset = 0.0F;
        this.advancedLastSmartTarget = null;
        this.advancedLastSmartVelocity = Vec3d.ZERO;
        this.advancedLastSmartUpdateTime = 0L;
        this.stackingCacheValid = false;
        this.stackingCachedYaw = 0.0F;
        this.stackingCachedPitch = 0.0F;
        this.criticalJumpArmed = false;
    }

    @Override
    protected void onDisable() {
        RotationManager.get().setActive(false);
        this.target = null;
    }

    @Listener
    private void onWorldChange(WorldLoadEvent event) {
        this.setEnabled(false);
    }

    @Listener(Priority.HIGH)
    private void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE || mc.player == null || mc.world == null) {
            return;
        }
        if (mc.player.isCreative() || mc.player.isSpectator()) {
            this.target = null;
            this.criticalJumpArmed = false;
            return;
        }
        this.updateCriticalJumpState(mc.player);

        if (this.target == null || !this.isInRange(this.target.getEntity())
                || !this.isInFov(this.target.getEntity()) || this.switchTimerPassed()) {
            this.lastSwitchMs = System.currentTimeMillis();
            this.updateTarget();
        } else if (this.rayTrace.getValue() && !this.canSeeTarget(this.target.getEntity())) {
            this.updateTarget();
        }

        Scaffold scaffold = ModuleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            this.target = null;
            return;
        }

        if (this.target != null) {
            this.target = new AttackData(this.target.getEntity());
        }
    }

    @Listener(Priority.HIGH)
    private void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE || mc.player == null || mc.world == null) {
            return;
        }

        if (this.attackDelayMs > 0L) {
            this.attackDelayMs = Math.max(0L, this.attackDelayMs - 50L);
        }

        if (this.target == null) {
            return;
        }

        if (this.shouldYieldToAutoMLG()) {
            return;
        }

        if (!this.isInTargetRange(this.target.entity)) {
            return;
        }

        float[] targetRotations = this.calculateAdvancedRotation(this.target.entity);
        float targetYaw = targetRotations[0];
        float targetPitch = MathHelper.clamp(targetRotations[1], -90.0F, 90.0F);
        double actualSpeed = this.nonLinearSpeed.getValue()
                ? this.calculateNonLinearSpeed(targetYaw, targetPitch)
                : this.rotationSpeed.getValue();

        RotationManager.get().setSmoothRotations(new Rotation(targetYaw, targetPitch), actualSpeed,
                true, this.fixMode.is("strict"));
        if (this.lockView.getValue()) {
            Rotation rotationsNow = RotationManager.get().getRotations();
            if (rotationsNow != null) {
                mc.player.setYaw(rotationsNow.getYaw());
                mc.player.setPitch(rotationsNow.getPitch());
            }
        }

        Rotation rotations = RotationManager.get().getRotations();
        float yaw = rotations == null ? mc.player.getYaw() : rotations.getYaw();
        float pitch = rotations == null ? mc.player.getPitch() : rotations.getPitch();
        if (this.canAttack()) {
            this.performAttack(yaw, pitch);
        }
    }

    @Listener
    private void onRender3D(Render3DEvent event) {
        if (!this.mark.getValue() || this.target == null || mc.world == null) {
            return;
        }

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Box box = this.target.getBox().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Color color = new Color(255, 80, 80);
        Render3DUtil.drawBox(event.getMatrices(), event.getConsumers(), box,
                color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F,
                0.18F, true, true);
        Render3DUtil.flushOutline();
    }

    public boolean isViewLocked() {
        return this.lockView.getValue() && this.target != null && !this.shouldYieldToAutoMLG();
    }

    private boolean shouldYieldToAutoMLG() {
        AutoMLG autoMLG = ModuleManager.getModule(AutoMLG.class);
        return autoMLG != null && autoMLG.isEnabled() && autoMLG.isRotationPriorityActive();
    }

    private boolean switchTimerPassed() {
        return System.currentTimeMillis() - this.lastSwitchMs >= this.switchDelay.getValue().longValue();
    }

    private long getAttackDelay() {
        int min = (int) Math.round(Math.min(this.minAps.getValue(), this.maxAps.getValue()));
        int max = (int) Math.round(Math.max(this.minAps.getValue(), this.maxAps.getValue()));
        int aps = randomInt(min, max);
        return 1000L / Math.max(1, aps);
    }

    private boolean performAttack(float yaw, float pitch) {
        if (this.isDigging() || this.isPlacing()) {
            return false;
        }

        if (!this.isInAttackRange(this.target.entity)) {
            return false;
        }

        Rotation serverRotations = RotationManager.get().getLastServerRotations();
        float attackYaw = serverRotations == null ? yaw : serverRotations.getYaw();
        float attackPitch = serverRotations == null ? pitch : serverRotations.getPitch();
        if (!this.canHitTargetWithRotation(this.target.entity, attackYaw, attackPitch)) {
            return false;
        }

        if (this.attackMode.is("1.8")) {
            if (this.attackDelayMs > 0L) {
                return false;
            }
            this.attackDelayMs += this.getAttackDelay();
        } else {
            if (mc.player.getAttackCooldownProgress(0.0F) < 1.0F) {
                return false;
            }
            if (this.airUntilCritical.getValue()
                    && this.shouldWaitForCritical(mc.player, this.target.entity)
                    && !this.canCriticalHit(mc.player, this.target.entity)) {
                return false;
            }
        }

        if (this.ignoreUsingItem.getValue() && mc.player.isUsingItem()) {
            EventManager.post(new AttackEvent(this.target.entity));
            PacketUtil.sendQueued(PlayerInteractEntityC2SPacket.attack(this.target.entity, mc.player.isSneaking()));
            PacketUtil.sendQueued(new HandSwingC2SPacket(Hand.MAIN_HAND));
        } else if (mc.interactionManager != null) {
            mc.interactionManager.attackEntity(mc.player, this.target.entity);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        this.hitRegistered = true;
        return true;
    }

    private void updateTarget() {
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity livingEntity
                    && TargetManager.isValidTarget(mc.player, entity)
                    && this.isInRange(livingEntity)
                    && this.isInFov(livingEntity)
                    && (!this.rayTrace.getValue() || this.canSeeTarget(livingEntity))) {
                targets.add(livingEntity);
            }
        }

        if (targets.isEmpty()) {
            this.target = null;
            return;
        }

        if (targets.stream().anyMatch(this::isInAttackRange)) {
            targets.removeIf(entity -> !this.isInAttackRange(entity));
        }

        Comparator<LivingEntity> comparator = switch (this.sortMode.getValue().toLowerCase()) {
            case "health" -> Comparator.comparingDouble(LivingEntity::getHealth);
            case "hurt-time" -> Comparator.comparingInt(entity -> entity.hurtTime);
            default -> Comparator.comparingDouble(this::getEntityBoxDistanceSq);
        };
        targets.sort(comparator.thenComparingDouble(this::getEntityBoxDistanceSq));

        if (this.targetMode.is("switch") && this.hitRegistered) {
            this.hitRegistered = false;
            this.switchTick++;
        }
        if (this.targetMode.is("single") || this.switchTick >= targets.size()) {
            this.switchTick = 0;
        }

        this.target = new AttackData(targets.get(this.switchTick));
    }

    private boolean canAttack() {
        if (this.isDigging() || (!this.ignoreUsingItem.getValue() && this.isEatingOrBow())) {
            return false;
        }
        if (this.requirePress.getValue() && !mc.options.attackKey.isPressed()) {
            return false;
        }
        return true;
    }

    private boolean isInRange(LivingEntity entity) {
        return this.isInTargetRange(entity);
    }

    private boolean isInTargetRange(LivingEntity entity) {
        return this.getEntityBoxDistanceSq(entity) <= this.square(this.getEffectiveTargetRange());
    }

    private boolean isInFov(LivingEntity entity) {
        if (mc.player == null || entity == null || this.fov.getValue() >= 360.0D) {
            return true;
        }
        float yaw = this.calculateAdvancedRotationAngles(this.getFovAimPosition(entity))[0];
        return Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw())) <= this.fov.getValue().floatValue() * 0.5F;
    }

    private Vec3d getFovAimPosition(Entity entity) {
        Box box = entity.getBoundingBox();
        return new Vec3d(entity.getX(), (box.minY + box.maxY) * 0.5D, entity.getZ());
    }

    private boolean isInAttackRange(LivingEntity entity) {
        return this.getEntityBoxDistanceSq(entity) <= this.square(this.getAttackRange());
    }

    private double getAttackRange() {
        return this.attackRange.getValue();
    }

    private double getEffectiveTargetRange() {
        double attack = this.getAttackRange();
        if (this.targetRange.getValue() < attack) {
            this.targetRange.setValue(attack);
        }
        return this.targetRange.getValue();
    }

    private double getEntityBoxDistanceSq(Entity entity) {
        if (mc.player == null || entity == null) return Double.MAX_VALUE;
        Vec3d eyes = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double x = eyes.x - MathHelper.clamp(eyes.x, box.minX, box.maxX);
        double y = eyes.y - MathHelper.clamp(eyes.y, box.minY, box.maxY);
        double z = eyes.z - MathHelper.clamp(eyes.z, box.minZ, box.maxZ);
        return x * x + y * y + z * z;
    }

    private double square(double value) {
        return value * value;
    }

    private boolean isEatingOrBow() {
        return mc.player != null && mc.player.isUsingItem();
    }

    private boolean canHitTargetWithRotation(Entity target, float yaw, float pitch) {
        if (mc.player == null || mc.world == null || target == null) {
            return false;
        }

        if (this.isOverlappingTarget(target)) {
            return true;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d view = RayTraceUtil.getVectorForRotation(yaw, pitch);
        Vec3d rayEnd = eyePos.add(view.multiply(this.getAttackRange()));
        Optional<Vec3d> entityHit = target.getBoundingBox().expand(0.1D).raycast(eyePos, rayEnd);
        if (entityHit.isEmpty()) {
            return false;
        }

        if (this.rayTrace.getValue()) {
            HitResult blockHit = RayTraceUtil.rayTraceBlocks(eyePos, rayEnd);
            if (blockHit != null
                    && blockHit.getType() == HitResult.Type.BLOCK
                    && blockHit.getPos().distanceTo(eyePos) < entityHit.get().distanceTo(eyePos)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOverlappingTarget(Entity target) {
        if (mc.player == null || target == null) {
            return false;
        }

        return mc.player.getBoundingBox().expand(0.01D).intersects(target.getBoundingBox().expand(0.01D));
    }

    private boolean isDigging() {
        return mc.interactionManager != null && mc.interactionManager.isBreakingBlock();
    }

    private boolean isPlacing() {
        return mc.player != null && mc.options.useKey.isPressed()
                && mc.player.getActiveItem().getItem() instanceof BlockItem;
    }

    private boolean canCriticalHit(PlayerEntity player, Entity target) {
        return player.fallDistance > 0.0F
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasBlindnessEffect()
                && !player.hasVehicle()
                && target instanceof LivingEntity;
    }

    private boolean shouldWaitForCritical(PlayerEntity player, Entity target) {
        return target instanceof LivingEntity
                && this.criticalJumpArmed
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isInLava()
                && !player.wasInPowderSnow
                && !player.isSubmergedInWater()
                && !player.isTouchingWater()
                && !player.hasBlindnessEffect()
                && !player.hasVehicle();
    }

    private void updateCriticalJumpState(PlayerEntity player) {
        if (player.hurtTime > 0
                || player.isClimbing()
                || player.isInLava()
                || player.wasInPowderSnow
                || player.isSubmergedInWater()
                || player.isTouchingWater()
                || player.hasVehicle()) {
            this.criticalJumpArmed = false;
            return;
        }
        if (player.isOnGround()) {
            this.criticalJumpArmed = mc.options.jumpKey.isPressed();
        }
    }

    private float[] calculateAdvancedRotation(LivingEntity entity) {
        Vec3d aimPos = this.getAdvancedAimPosition(entity);
        float[] rotation = this.calculateAdvancedRotationAngles(aimPos);
        return this.applySmoothAiming(rotation[0], rotation[1]);
    }

    private Vec3d getAdvancedAimPosition(Entity entity) {
        if (this.isOverlappingTarget(entity)) {
            Vec3d overlapAim = this.getOverlapAimPosition(entity);
            return this.randomization.getValue()
                    ? this.applyBoxRandomization(entity, overlapAim)
                    : overlapAim;
        }

        Vec3d basePos = this.aimClosestPoint.getValue()
                ? this.getAdvancedNearestPoint(entity)
                : this.getAdvancedCenterPosition(entity);
        if (!this.advancedPrediction.getValue()) {
            return this.randomization.getValue()
                    ? this.applyBoxRandomization(entity, basePos)
                    : basePos;
        }

        Vec3d velocity = this.getAdvancedVelocity(entity);
        double playerDistance = mc.player == null ? 0.0D : Math.sqrt(this.getEntityBoxDistanceSq(entity));
        Vec3d predicted = switch (this.advancedPredictionMode.getValue().toLowerCase()) {
            case "simple" -> basePos.add(this.multiplyVec(velocity, 1.5D));
            case "smart" -> this.predictAdvancedSmart(basePos, entity, playerDistance);
            default -> basePos;
        };
        return this.randomization.getValue()
                ? this.applyBoxRandomization(entity, predicted)
                : predicted;
    }

    private Vec3d getAdvancedCenterPosition(Entity entity) {
        if (mc.player == null || mc.world == null) {
            Box box = entity.getBoundingBox();
            return new Vec3d(entity.getX(), (box.minY + box.maxY) * 0.5D, entity.getZ());
        }

        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double centerX = entity.getX();
        double centerZ = entity.getZ();
        double height = box.maxY - box.minY;
        double[] sampleRatios = {0.5D, 0.65D, 0.35D, 0.8D, 0.2D};

        for (double ratio : sampleRatios) {
            Vec3d samplePos = new Vec3d(centerX, box.minY + height * ratio, centerZ);
            if (this.canSee(eyePos, samplePos)) {
                return samplePos;
            }
        }

        if (entity instanceof LivingEntity livingEntity) {
            return new Vec3d(centerX, entity.getY() + livingEntity.getEyeHeight(livingEntity.getPose()), centerZ);
        }
        return new Vec3d(centerX, (box.minY + box.maxY) * 0.5D, centerZ);
    }

    private Vec3d getAdvancedNearestPoint(Entity entity) {
        if (mc.player == null || mc.world == null) {
            return new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
        }

        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        if (this.isPointInsideBox(eyePos, box)) {
            return this.getOverlapAimPosition(entity);
        }

        double clampedX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double clampedZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        double height = box.maxY - box.minY;
        double[] sampleRatios = {0.85D, 0.7D, 0.5D, 0.3D, 0.15D};

        for (double ratio : sampleRatios) {
            Vec3d samplePos = new Vec3d(clampedX, box.minY + height * ratio, clampedZ);
            if (this.canSee(eyePos, samplePos)) {
                return samplePos;
            }
        }

        double fallbackY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        return new Vec3d(clampedX, fallbackY, clampedZ);
    }

    private Vec3d getOverlapAimPosition(Entity entity) {
        Box box = entity.getBoundingBox();
        Vec3d eyePos = mc.player == null ? new Vec3d(
                (box.minX + box.maxX) * 0.5D,
                (box.minY + box.maxY) * 0.5D,
                (box.minZ + box.maxZ) * 0.5D
        ) : mc.player.getEyePos();
        double epsilon = 1.0E-4D;

        double dxMin = Math.abs(eyePos.x - box.minX);
        double dxMax = Math.abs(box.maxX - eyePos.x);
        double dyMin = Math.abs(eyePos.y - box.minY);
        double dyMax = Math.abs(box.maxY - eyePos.y);
        double dzMin = Math.abs(eyePos.z - box.minZ);
        double dzMax = Math.abs(box.maxZ - eyePos.z);

        int face = 0;
        double minDistance = dxMin;
        if (dxMax < minDistance) { minDistance = dxMax; face = 1; }
        if (dyMin < minDistance) { minDistance = dyMin; face = 2; }
        if (dyMax < minDistance) { minDistance = dyMax; face = 3; }
        if (dzMin < minDistance) { minDistance = dzMin; face = 4; }
        if (dzMax < minDistance) { face = 5; }

        return switch (face) {
            case 0 -> new Vec3d(box.minX + epsilon, MathHelper.clamp(eyePos.y, box.minY, box.maxY), MathHelper.clamp(eyePos.z, box.minZ, box.maxZ));
            case 1 -> new Vec3d(box.maxX - epsilon, MathHelper.clamp(eyePos.y, box.minY, box.maxY), MathHelper.clamp(eyePos.z, box.minZ, box.maxZ));
            case 2 -> new Vec3d(MathHelper.clamp(eyePos.x, box.minX, box.maxX), box.minY + epsilon, MathHelper.clamp(eyePos.z, box.minZ, box.maxZ));
            case 3 -> new Vec3d(MathHelper.clamp(eyePos.x, box.minX, box.maxX), box.maxY - epsilon, MathHelper.clamp(eyePos.z, box.minZ, box.maxZ));
            case 4 -> new Vec3d(MathHelper.clamp(eyePos.x, box.minX, box.maxX), MathHelper.clamp(eyePos.y, box.minY, box.maxY), box.minZ + epsilon);
            default -> new Vec3d(MathHelper.clamp(eyePos.x, box.minX, box.maxX), MathHelper.clamp(eyePos.y, box.minY, box.maxY), box.maxZ - epsilon);
        };
    }

    private Vec3d predictAdvancedSmart(Vec3d basePos, Entity targetEntity, double distance) {
        long currentTime = System.currentTimeMillis();
        Vec3d currentVelocity = this.getAdvancedVelocity(targetEntity);
        Vec3d playerVelocity = mc.player == null ? Vec3d.ZERO : this.getAdvancedVelocity(mc.player);
        Vec3d relativeVelocity = currentVelocity.subtract(playerVelocity);
        double targetSpeed = this.horizontalLengthVec(currentVelocity);
        if (targetSpeed < 0.01D) {
            return basePos;
        }

        Vec3d acceleration = Vec3d.ZERO;
        if (this.advancedLastSmartTarget == targetEntity && this.advancedLastSmartUpdateTime > 0L) {
            long timeDiff = currentTime - this.advancedLastSmartUpdateTime;
            if (timeDiff > 0L && timeDiff < 500L) {
                acceleration = this.multiplyVec(currentVelocity.subtract(this.advancedLastSmartVelocity), 1000.0D / timeDiff);
            }
        }

        this.advancedLastSmartTarget = targetEntity;
        this.advancedLastSmartVelocity = currentVelocity;
        this.advancedLastSmartUpdateTime = currentTime;

        double aimTime = distance / 3.0D;
        double speedFactor = Math.min(targetSpeed / 0.2D, 2.0D);
        double predictionTicks = MathHelper.clamp(aimTime * (0.5D + speedFactor * 0.5D), 0.5D, 5.0D);
        double predictionTime = predictionTicks * 0.05D;
        Vec3d predictedOffset = this.multiplyVec(relativeVelocity, predictionTime);

        if (this.lengthSquaredVec(acceleration) > 0.0001D) {
            predictedOffset = predictedOffset.add(this.multiplyVec(acceleration, 0.5D * predictionTime * predictionTime));
        }

        return basePos.add(predictedOffset);
    }

    private Vec3d applyBoxRandomization(Entity entity, Vec3d basePos) {
        if (entity == null) return basePos;
        Box box = entity.getBoundingBox();
        Vec3d insideBase = this.clampInsideBox(basePos, box);
        if (mc.player == null) return insideBase;

        float[] offsets = this.calculateNoiseOffset();
        double horizontalPercent = offsets[0] / 100.0D;
        double verticalPercent = offsets[1] / 100.0D;

        Vec3d eye = mc.player.getEyePos();
        Vec3d toTarget = insideBase.subtract(eye);
        double flatLen = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double rightX = flatLen <= 1.0E-6D ? 1.0D : -toTarget.z / flatLen;
        double rightZ = flatLen <= 1.0E-6D ? 0.0D : toTarget.x / flatLen;

        double width = Math.max(0.0D, box.maxX - box.minX);
        double depth = Math.max(0.0D, box.maxZ - box.minZ);
        double height = Math.max(0.0D, box.maxY - box.minY);
        double horizontalRadius = Math.max(width, depth) * 0.5D;
        double verticalRadius = height * 0.5D;

        Vec3d randomized = insideBase.add(
                rightX * horizontalRadius * horizontalPercent,
                verticalRadius * verticalPercent,
                rightZ * horizontalRadius * horizontalPercent
        );
        return this.clampInsideBox(randomized, box);
    }

    private Vec3d clampInsideBox(Vec3d point, Box box) {
        double epsilon = 1.0E-4D;
        double minX = box.minX + epsilon;
        double maxX = box.maxX - epsilon;
        double minY = box.minY + epsilon;
        double maxY = box.maxY - epsilon;
        double minZ = box.minZ + epsilon;
        double maxZ = box.maxZ - epsilon;
        if (minX > maxX) {
            minX = maxX = (box.minX + box.maxX) * 0.5D;
        }
        if (minY > maxY) {
            minY = maxY = (box.minY + box.maxY) * 0.5D;
        }
        if (minZ > maxZ) {
            minZ = maxZ = (box.minZ + box.maxZ) * 0.5D;
        }
        return new Vec3d(
                MathHelper.clamp(point.x, minX, maxX),
                MathHelper.clamp(point.y, minY, maxY),
                MathHelper.clamp(point.z, minZ, maxZ)
        );
    }

    private float[] calculateAdvancedRotationAngles(Vec3d targetPos) {
        Vec3d diff = mc.player == null ? Vec3d.ZERO : targetPos.subtract(mc.player.getEyePos());
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0F, 89.0F)};
    }

    private float[] applySmoothAiming(float targetYaw, float targetPitch) {
        Rotation current = RotationManager.get().getRotations();
        float currentYaw = current == null ? mc.player.getYaw() : current.getYaw();
        float currentPitch = current == null ? mc.player.getPitch() : current.getPitch();

        if (this.rotationStacking.getValue()) {
            float yawDiffCheck = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float pitchDiffCheck = targetPitch - currentPitch;
            float totalDiff = (float) Math.sqrt(yawDiffCheck * yawDiffCheck + pitchDiffCheck * pitchDiffCheck);
            if (totalDiff >= this.stackingThreshold.getValue()) {
                this.stackingCachedYaw = targetYaw;
                this.stackingCachedPitch = targetPitch;
                this.stackingCacheValid = true;
            }
            if (this.stackingCacheValid) {
                targetYaw = this.stackingCachedYaw;
                targetPitch = this.stackingCachedPitch;
            } else {
                this.stackingCachedYaw = targetYaw;
                this.stackingCachedPitch = targetPitch;
                this.stackingCacheValid = true;
            }
        }

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        return new float[]{currentYaw + yawDiff, MathHelper.clamp(currentPitch + pitchDiff, -89.0F, 89.0F)};
    }

    private Vec3d getAdvancedVelocity(Entity entity) {
        return entity.getVelocity();
    }

    private Vec3d multiplyVec(Vec3d vec, double factor) {
        return new Vec3d(vec.x * factor, vec.y * factor, vec.z * factor);
    }

    private double horizontalLengthVec(Vec3d vec) {
        return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    private double lengthSquaredVec(Vec3d vec) {
        return vec.x * vec.x + vec.y * vec.y + vec.z * vec.z;
    }

    private double calculateNonLinearSpeed(float targetYaw, float targetPitch) {
        Rotation current = RotationManager.get().getRotations();
        float currentYaw = current == null ? mc.player.getYaw() : current.getYaw();
        float currentPitch = current == null ? mc.player.getPitch() : current.getPitch();
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float pitchDiff = Math.abs(targetPitch - currentPitch);
        float totalAngleDiff = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        double normalizedDiff = Math.min(totalAngleDiff / 90.0D, 1.0D);
        double speed = Math.max(0.1D, Math.sqrt(normalizedDiff) * this.rotationSpeed.getValue());
        return MathHelper.clamp(speed, 0.1D, 10.0D);
    }

    private float[] applyRandomization(float yaw, float pitch) {
        if (!this.randomization.getValue()) {
            return new float[]{yaw, pitch};
        }
        float[] noiseOffsets = this.calculateNoiseOffset();
        return new float[]{yaw + noiseOffsets[0], pitch + noiseOffsets[1]};
    }

    private float[] calculateNoiseOffset() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastNoiseUpdate >= this.noiseDelay.getValue().longValue()) {
            this.lastNoiseUpdate = currentTime;
            double elapsed = (currentTime - this.noiseTimeOffset) / 1000.0D * this.noiseSpeed.getValue();
            double yawNoise = this.perlinNoise1D(elapsed, 0);
            float yawRange = randomFloat(this.yawRandomRangeMin.getValue().floatValue(), this.yawRandomRangeMax.getValue().floatValue());
            this.cachedYawOffset = (float) (yawNoise * yawRange);
            double pitchNoise = this.perlinNoise1D(elapsed, 100);
            float pitchRange = randomFloat(this.pitchRandomRangeMin.getValue().floatValue(), this.pitchRandomRangeMax.getValue().floatValue());
            this.cachedPitchOffset = (float) (pitchNoise * pitchRange);
        }

        float yawOffset = this.lastYawOffset + (this.cachedYawOffset - this.lastYawOffset) * 0.3F;
        this.lastYawOffset = yawOffset;
        float pitchOffset = this.lastPitchOffset + (this.cachedPitchOffset - this.lastPitchOffset) * 0.3F;
        this.lastPitchOffset = pitchOffset;
        return new float[]{yawOffset, pitchOffset};
    }

    private double perlinNoise1D(double x, int seed) {
        x += seed;
        int xi = (int) Math.floor(x);
        double xf = x - xi;
        double u = this.fade(xf);
        double grad0 = this.pseudoRandomGradient(xi);
        double grad1 = this.pseudoRandomGradient(xi + 1);
        double n0 = grad0 * xf;
        double n1 = grad1 * (xf - 1.0D);
        return this.lerp(n0, n1, u);
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private double pseudoRandomGradient(int x) {
        int hash = x * 374761393;
        hash = (hash ^ (hash >> 13)) * 1274126177;
        hash ^= hash >> 16;
        return (hash & 0xFFFF) / 32768.0D - 1.0D;
    }

    public LivingEntity getTarget() {
        return this.target == null ? null : this.target.getEntity();
    }

    public boolean canSeeTarget(Entity target) {
        if (mc.player == null || mc.world == null || target == null) {
            return false;
        }
        if (target instanceof LivingEntity livingEntity) {
            Vec3d eyePos = mc.player.getEyePos();
            Box box = livingEntity.getBoundingBox();
            double height = box.maxY - box.minY;
            double centerX = livingEntity.getX();
            double centerZ = livingEntity.getZ();
            double[] sampleRatios = {0.5D, 0.8D, 0.35D, 0.15D};
            for (double ratio : sampleRatios) {
                Vec3d samplePos = new Vec3d(centerX, box.minY + height * ratio, centerZ);
                if (this.canSee(eyePos, samplePos)) {
                    return true;
                }
            }
        }
        return this.canSee(mc.player.getEyePos(), target.getEyePos());
    }

    private boolean canSee(Vec3d from, Vec3d to) {
        if (mc.world == null || mc.player == null) {
            return false;
        }
        HitResult result = mc.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result.getType() != HitResult.Type.BLOCK;
    }

    private boolean isPointInsideBox(Vec3d point, Box box) {
        return point.x >= box.minX && point.x <= box.maxX
                && point.y >= box.minY && point.y <= box.maxY
                && point.z >= box.minZ && point.z <= box.maxZ;
    }

    private static int randomInt(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + (int) Math.floor(Math.random() * (max - min + 1));
    }

    private static float randomFloat(float min, float max) {
        if (max <= min) {
            return min;
        }
        return min + (float) Math.random() * (max - min);
    }

    public static final class AttackData {
        private final LivingEntity entity;
        private final Box box;

        private AttackData(LivingEntity entity) {
            this.entity = entity;
            float borderSize = entity.getWidth() * 0.1F;
            this.box = entity.getBoundingBox().expand(borderSize, borderSize, borderSize);
        }

        public LivingEntity getEntity() {
            return this.entity;
        }

        public Box getBox() {
            return this.box;
        }
    }
}
