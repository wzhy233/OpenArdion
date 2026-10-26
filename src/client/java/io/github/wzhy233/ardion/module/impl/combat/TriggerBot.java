package io.github.wzhy233.ardion.module.impl.combat;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.manager.TargetManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.time.SmoothAnimationTimer;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class TriggerBot extends Module {
    private final ModeValue attackMode = this.registerValue(new ModeValue("attack-mode", this, "1.9+", "1.8", "1.9+"));
    private final NumberValue minAps = this.registerValue(new NumberValue("min-aps", this, 8.0D, 1.0D, 20.0D, true,
            () -> this.attackMode.is("1.8")));
    private final NumberValue maxAps = this.registerValue(new NumberValue("max-aps", this, 12.0D, 1.0D, 20.0D, true,
            () -> this.attackMode.is("1.8")));

    private final BooleanValue airUntilCritical = this.registerValue(new BooleanValue("air-until-critical", this, false,
            () -> this.attackMode.is("1.9+")));

    private final SmoothAnimationTimer healthAnimation = new SmoothAnimationTimer(0.0F);
    private float hudAlpha = 0.0F;
    private float hudAlphaTarget = 0.0F;
    private long lastAlphaUpdate;

    private long lastAttack;
    private long nextAttackDelay;
    private Entity currentTarget;
    private boolean criticalJumpArmed;

    public TriggerBot() {
        super("trigger-bot", ModuleCategory.COMBAT);
        this.healthAnimation.speed = 0.65F;
    }

    @Override
    protected void onEnable() {
        KillAura killAura = ModuleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            killAura.setEnabled(false);
        }
        this.criticalJumpArmed = false;
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            this.currentTarget = null;
            this.criticalJumpArmed = false;
            return;
        }
        if (mc.player == null || mc.interactionManager == null || mc.world == null || mc.player.isSpectator()) {
            this.currentTarget = null;
            this.criticalJumpArmed = false;
            return;
        }
        this.updateCriticalJumpState(mc.player);
        if (mc.player.isUsingItem() || mc.player.isSpectator()) {
            this.currentTarget = null;
            return;
        }

        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            this.currentTarget = null;
            return;
        }

        Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (!TargetManager.isValidTarget(mc.player, entity)) {
            this.currentTarget = null;
            return;
        }
        this.currentTarget = entity;

        if (this.attackMode.is("1.8")) {
            if (System.currentTimeMillis() - lastAttack < nextAttackDelay) return;
        } else if (mc.player.getAttackCooldownProgress(0.0F) < 1.0F) {
            return;
        }
        if (!mc.player.canAttackEntityIn(entity.getBoundingBox(), 0.0D)) return;
        if (!this.isAimingAt(mc.player, entity)) return;

        if (this.attackMode.is("1.9+") && this.airUntilCritical.getValue() && this.shouldWaitForCritical(mc.player, entity)) {
            if (!this.canCriticalHit(mc.player, entity)) {
                return;
            }
        }
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = System.currentTimeMillis();
        if (this.attackMode.is("1.8")) {
            nextAttackDelay = this.getRandomAttackDelay();
        }
    }

    private boolean isAimingAt(PlayerEntity player, Entity target) {
        double reach = 3.0D;
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d rotation = player.getRotationVec(1.0F);
        Vec3d end = start.add(rotation.x * reach, rotation.y * reach, rotation.z * reach);
        Box box = player.getBoundingBox().stretch(rotation.multiply(reach)).expand(1.0D);
        EntityHitResult hitResult = ProjectileUtil.raycast(player, start, end, box, entity -> entity.canHit() && !entity.isSpectator(), reach * reach);

        return hitResult != null && hitResult.getEntity() == target;
    }

    private long getRandomAttackDelay() {
        double min = Math.min(this.minAps.getValue(), this.maxAps.getValue());
        double max = Math.max(this.minAps.getValue(), this.maxAps.getValue());
        double cps = Math.random() * (max - min + 1) + min;
        return Math.round(1000.0D / cps);
    }

    private boolean canCriticalHit(PlayerEntity player, Entity target) {
        return player.fallDistance > 0.0F
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasBlindnessEffect()
                && !player.hasVehicle()
                && !player.isSprinting()
                && target instanceof LivingEntity;
    }

    private boolean shouldWaitForCritical(PlayerEntity player, Entity target) {
        return target instanceof LivingEntity
                && this.criticalJumpArmed
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isSprinting()
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
            this.criticalJumpArmed = MinecraftClient.getInstance().options.jumpKey.isPressed();
        }
    }

    @Listener
    public void onWorldChange(WorldLoadEvent event) {
        this.setEnabled(false);
    }

    public Entity getCurrentTarget() {
        return this.currentTarget;
    }
}
