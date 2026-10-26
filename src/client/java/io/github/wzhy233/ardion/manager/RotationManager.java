package io.github.wzhy233.ardion.manager;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.player.*;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

import java.util.function.Function;

public final class RotationManager {
    private static final RotationManager INSTANCE = new RotationManager();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private float offsetX;
    private float offsetY;
    private Rotation rotations = new Rotation(0.0F, 0.0F);
    private Rotation lastRotations = new Rotation(0.0F, 0.0F);
    private Rotation targetRotations = new Rotation(0.0F, 0.0F);
    private Rotation lastServerRotations = new Rotation(0.0F, 0.0F);
    private float movementYaw;
    private boolean moveFix;
    private boolean strict;
    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private Function<Vec2f, Boolean> raycast;
    private float randomAngle;
    private Rotation renderFrom;
    private boolean smoothSteps;
    private boolean renderTransition;

    private RotationManager() {
    }

    public static RotationManager get() {
        return INSTANCE;
    }

    public void setRotations(Rotation rotations, double rotationSpeed) {
        this.setRotations(rotations, rotationSpeed, null);
    }

    public void setRotations(Rotation rotations, double rotationSpeed, Function<Vec2f, Boolean> raycast) {
        this.requestRotations(rotations, rotationSpeed, raycast, false);
    }

    private void requestRotations(Rotation rotations, double rotationSpeed, Function<Vec2f, Boolean> raycast,
                                  boolean smoothSteps) {
        if (!this.active && mc.player != null) {
            this.renderFrom = new Rotation(mc.player);
        }
        if (smoothSteps && !this.active && mc.player != null) {
            this.rotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
            this.lastRotations = this.rotations.copy();
            this.lastServerRotations = this.rotations.copy();
            this.renderFrom = this.rotations.copy();
            this.smoothed = false;
        }
        this.smoothSteps = smoothSteps;
        this.renderTransition = true;
        this.targetRotations = rotations;
        this.rotationSpeed = rotationSpeed;
        this.raycast = raycast;
        this.active = true;
        this.smooth();
    }

    public void setSmoothRotations(Rotation rotations, double rotationSpeed, boolean moveFix, boolean strictMoveFix) {
        this.moveFix = moveFix && !GlobalSetting.isMoveFixDisabled();
        this.strict = strictMoveFix && !GlobalSetting.isMoveFixDisabled();
        this.requestRotations(rotations, rotationSpeed, null, true);
    }

    public void setRotations(Rotation rotations, double rotationSpeed, boolean moveFix, boolean strictMoveFix) {
        this.moveFix = moveFix && !GlobalSetting.isMoveFixDisabled();
        this.strict = strictMoveFix && !GlobalSetting.isMoveFixDisabled();
        this.setRotations(rotations, rotationSpeed, null);
    }

    @Listener(Priority.HIGHEST)
    private void onWorldChange(WorldLoadEvent event) {
        if (this.active) {
            this.setActive(false);
        }
        this.renderFrom = null;
        this.renderTransition = false;
        this.smoothSteps = false;
        this.smoothed = false;
    }

    @Listener(Priority.HIGHEST)
    private void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE || mc.player == null || mc.world == null) return;
        this.movementYaw = mc.player.getYaw();
        this.renderFrom = this.active ? this.rotations.copy() : new Rotation(mc.player);
        this.renderTransition = this.active;
    }

    @Listener(Priority.LOWER)
    private void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE || mc.player == null || mc.world == null) return;
        if (!this.active || this.rotations == null || this.lastRotations == null
                || this.targetRotations == null || this.lastServerRotations == null) {
            Rotation current = new Rotation(mc.player.getYaw(), mc.player.getPitch());
            this.rotations = current;
            this.lastRotations = current.copy();
            this.targetRotations = current.copy();
            this.lastServerRotations = current.copy();
        }
        if (this.active) {
            this.smooth();
        }
    }

    @Listener(Priority.LOWER)
    public void onMotion(MotionEvent event) {
        if (event.getStage() != MotionEvent.Stage.PRE || mc.player == null || mc.world == null) return;
        if (this.active && this.rotations != null) {
            float yaw = this.rotations.getYaw();
            float pitch = this.rotations.getPitch();
            event.setYaw(yaw);
            event.setPitch(pitch);
            this.lastServerRotations = new Rotation(yaw, pitch);

            if (Math.abs(MathHelper.wrapDegrees(this.rotations.getYaw() - mc.player.getYaw())) < 1.0F
                    && Math.abs(this.rotations.getPitch() - mc.player.getPitch()) < 1.0F) {
                this.active = false;
                this.correctDisabledRotations();
            }
            this.lastRotations = this.rotations;
        } else {
            this.lastRotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        }

        this.targetRotations = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        this.smoothed = false;
    }

    @Listener(Priority.LOWER)
    private void onStrafe(StrafeEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (GlobalSetting.isMoveFixDisabled() || !this.moveFix || !this.active || this.rotations == null) return;
        event.setYaw(this.rotations.getYaw());
    }

    @Listener(Priority.LOWER)
    private void onJump(JumpEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (GlobalSetting.isMoveFixDisabled() || !this.moveFix || !this.active || this.rotations == null) return;
        event.setYaw(this.rotations.getYaw());
    }

    @Listener(Priority.LOWER)
    private void onMoveInput(MovementInputEvent event) {
        if (GlobalSetting.isMoveFixDisabled() || this.rotations == null || !this.active || !this.moveFix) return;
        if (this.strict) return;

        float forward = event.getForward();
        float strafe = event.getStrafe();
        double yaw = MathHelper.wrapDegrees((float) Math.toDegrees(this.direction(mc.player.getYaw(), forward, strafe)));
        if (forward == 0.0F && strafe == 0.0F) return;

        float closestForward = 0.0F;
        float closestStrafe = 0.0F;
        float closestDifference = Float.MAX_VALUE;
        for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
                if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;
                double predictedAngle = MathHelper.wrapDegrees((float) Math.toDegrees(this.direction(this.rotations.getYaw(), predictedForward, predictedStrafe)));
                float difference = Math.abs(this.getAngleDifference((float) yaw, (float) predictedAngle));
                if (difference < closestDifference) {
                    closestDifference = difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setStrafe(closestStrafe);
    }

    public double direction(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0F) rotationYaw += 180.0F;
        float forward = 1.0F;
        if (moveForward < 0.0F) forward = -0.5F;
        else if (moveForward > 0.0F) forward = 0.5F;
        if (moveStrafing > 0.0F) rotationYaw -= 90.0F * forward;
        if (moveStrafing < 0.0F) rotationYaw += 90.0F * forward;
        return Math.toRadians(rotationYaw);
    }

    private void correctDisabledRotations() {
        if (mc.player == null) return;
        Rotation rotations = new Rotation(this.getRotations().getYaw(), mc.player.getPitch());
        Rotation fixedRotations = this.resetRotation(this.applySensitivityPatch(rotations, this.lastRotations));
        mc.player.setYaw(fixedRotations.getYaw());
        mc.player.setPitch(fixedRotations.getPitch());
    }

    public Rotation getPreviousRotation() {
        return new Rotation(this.lastServerRotations.getYaw(), this.lastServerRotations.getPitch());
    }

    public void smooth() {
        if (!this.smoothed) {
            float targetYaw = this.targetRotations.getYaw();
            float targetPitch = this.targetRotations.getPitch();

            if (this.raycast != null && (Math.abs(targetYaw - this.rotations.getYaw()) > 5.0F
                    || Math.abs(targetPitch - this.rotations.getPitch()) > 5.0F) && mc.player != null) {
                Vec2f trueTargetRotations = new Vec2f(this.targetRotations.getYaw(), this.targetRotations.getPitch());
                double speed = Math.random() * Math.random() * Math.random() * 20.0D;
                this.randomAngle += (float) ((20.0F + (float) (Math.random() - 0.5D)
                        * (Math.random() * Math.random() * Math.random() * 360.0D))
                        * (mc.player.age / 10 % 2 == 0 ? -1.0F : 1.0F));

                this.offsetX += (float) (-MathHelper.sin((float) Math.toRadians(this.randomAngle)) * speed);
                this.offsetY += (float) (MathHelper.cos((float) Math.toRadians(this.randomAngle)) * speed);
                targetYaw += this.offsetX;
                targetPitch += this.offsetY;

                if (!this.raycast.apply(new Vec2f(targetYaw, targetPitch))) {
                    this.randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.x - targetYaw, targetPitch - trueTargetRotations.y)) - 180.0F;
                    targetYaw -= this.offsetX;
                    targetPitch -= this.offsetY;
                    this.offsetX += (float) (-MathHelper.sin((float) Math.toRadians(this.randomAngle)) * speed);
                    this.offsetY += (float) (MathHelper.cos((float) Math.toRadians(this.randomAngle)) * speed);
                    targetYaw += this.offsetX;
                    targetPitch += this.offsetY;
                }

                if (!this.raycast.apply(new Vec2f(targetYaw, targetPitch))) {
                    this.offsetX = 0.0F;
                    this.offsetY = 0.0F;
                    targetYaw = (float) (this.targetRotations.getYaw() + Math.random() * 2.0D);
                    targetPitch = (float) (this.targetRotations.getPitch() + Math.random() * 2.0D);
                }
            }

            if (this.smoothSteps) {
                Rotation target = new Rotation(targetYaw, MathHelper.clamp(targetPitch, -90.0F, 90.0F));
                Rotation delta = this.move(this.lastRotations, target, this.rotationSpeed);
                this.rotations = this.applySensitivityPatch(this.lastRotations.offset(delta.getYaw(), delta.getPitch()),
                        this.lastRotations);
            } else {
                this.rotations = this.smooth(new Rotation(targetYaw, targetPitch), this.rotationSpeed + Math.random());
            }
        }
        this.smoothed = true;
    }

    public Rotation smooth(Rotation targetRotation, double speed) {
        return this.smooth(this.lastRotations, targetRotation, speed);
    }

    public Rotation smooth(Rotation lastRotation, Rotation targetRotation, double speed) {
        float yaw = targetRotation.getYaw();
        float pitch = targetRotation.getPitch();
        float lastYaw = lastRotation.getYaw();
        float lastPitch = lastRotation.getPitch();

        if (speed != 0.0D) {
            Rotation move = this.move(targetRotation, speed);
            yaw = lastYaw + move.getYaw();
            pitch = lastPitch + move.getPitch();

            int iterations = Math.max(1, (int) (mc.getCurrentFps() / 20.0F + Math.random() * 10.0D));
            for (int i = 1; i <= iterations; i++) {
                if (Math.abs(move.getYaw()) + Math.abs(move.getPitch()) > 0.0001D) {
                    yaw += (float) ((Math.random() - 0.5D) / 1000.0D);
                    pitch -= (float) (Math.random() / 200.0D);
                }
                Rotation fixedRotations = this.applySensitivityPatch(new Rotation(yaw, pitch));
                yaw = fixedRotations.getYaw();
                pitch = MathHelper.clamp(fixedRotations.getPitch(), -90.0F, 90.0F);
            }
        }

        return new Rotation(yaw, pitch);
    }

    public float getAngleDifference(float a, float b) {
        return ((((a - b) % 360.0F) + 540.0F) % 360.0F) - 180.0F;
    }

    public double getRotationDifference(Rotation a, Rotation b) {
        return Math.hypot(this.getAngleDifference(a.getYaw(), b.getYaw()), a.getPitch() - b.getPitch());
    }

    public Rotation applySensitivityPatch(Rotation rotation) {
        return this.applySensitivityPatch(rotation, this.getPreviousRotation());
    }

    public Rotation applySensitivityPatch(Rotation rotation, Rotation previousRotation) {
        double sensitivity = mc.options.getMouseSensitivity().getValue() * (1.0D + Math.random() / 10000000.0D) * 0.6D + 0.2D;
        double multiplier = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
        float yaw = previousRotation.getYaw() + (float) (Math.round((rotation.getYaw() - previousRotation.getYaw()) / multiplier) * multiplier);
        float pitch = previousRotation.getPitch() + (float) (Math.round((rotation.getPitch() - previousRotation.getPitch()) / multiplier) * multiplier);
        return new Rotation(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F));
    }

    public Rotation resetRotation(Rotation rotation) {
        if (rotation == null || mc.player == null) return null;
        float yaw = rotation.getYaw() + MathHelper.wrapDegrees(mc.player.getYaw() - rotation.getYaw());
        float pitch = mc.player.getPitch();
        return new Rotation(yaw, pitch);
    }

    public Rotation move(Rotation lastRotation, Rotation targetRotation, double speed) {
        if (speed != 0.0D) {
            double deltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - lastRotation.getYaw());
            double deltaPitch = targetRotation.getPitch() - lastRotation.getPitch();
            double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            if (distance <= 1.0E-7D) return new Rotation(0.0F, 0.0F);
            double distributionYaw = Math.abs(deltaYaw / distance);
            double distributionPitch = Math.abs(deltaPitch / distance);
            double maxYaw = speed * distributionYaw;
            double maxPitch = speed * distributionPitch;
            float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
            float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);
            return new Rotation(moveYaw, movePitch);
        }
        return new Rotation(0.0F, 0.0F);
    }

    public Rotation move(Rotation targetRotation, double speed) {
        return this.move(this.lastRotations, targetRotation, speed);
    }

    public Rotation getRotations() {
        return this.rotations;
    }

    public Rotation getRenderRotation(float tickDelta) {
        if (mc.player == null || (!this.active && !this.renderTransition)) return null;
        if (this.renderFrom == null) return this.rotations.copy();
        Rotation to = this.active ? this.rotations : new Rotation(mc.player);
        float delta = MathHelper.clamp(tickDelta, 0.0F, 1.0F);
        float yawDelta = to.getYaw() - this.renderFrom.getYaw();
        if (!this.active) {
            if (Math.abs(MathHelper.wrapDegrees(to.getYaw() - this.rotations.getYaw())) < 1.0F) {
                yawDelta = this.rotations.getYaw() - this.renderFrom.getYaw();
            } else {
                yawDelta = MathHelper.wrapDegrees(yawDelta);
            }
        }
        if (Math.abs(yawDelta) > 180.5F) yawDelta = MathHelper.wrapDegrees(yawDelta);
        return new Rotation(this.renderFrom.getYaw()
                + yawDelta * delta,
                MathHelper.lerp(delta, this.renderFrom.getPitch(), to.getPitch()));
    }

    public void setRotationsDirect(Rotation rotations) {
        this.rotations = rotations;
    }

    public Rotation getLastRotations() {
        return this.lastRotations;
    }

    public Rotation getTargetRotations() {
        return this.targetRotations;
    }

    public Rotation getLastServerRotations() {
        return this.lastServerRotations;
    }

    public void setLastServerRotations(Rotation rotations) {
        this.lastServerRotations = rotations;
    }

    public float getMovementYaw() {
        return this.movementYaw;
    }

    public boolean isMoveFix() {
        return this.moveFix && !GlobalSetting.isMoveFixDisabled();
    }

    public void setMoveFix(boolean moveFix) {
        this.moveFix = moveFix && !GlobalSetting.isMoveFixDisabled();
    }

    public boolean isStrict() {
        return this.strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
