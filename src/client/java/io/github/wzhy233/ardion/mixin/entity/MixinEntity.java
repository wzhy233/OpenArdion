package io.github.wzhy233.ardion.mixin.entity;

import io.github.wzhy233.ardion.access.CameraEntityAccess;
import io.github.wzhy233.ardion.event.impl.player.StrafeEvent;
import io.github.wzhy233.ardion.event.impl.player.StuckInBlockEvent;
import io.github.wzhy233.ardion.event.impl.render.EventGlowing;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.module.impl.movement.NoSlow;
import io.github.wzhy233.ardion.module.impl.render.FreeLook;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.util.rotation.RayTraceUtil;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity implements CameraEntityAccess {
    @Unique
    private float ardion$cameraPitch;

    @Unique
    private float ardion$cameraYaw;

    @Shadow
    public abstract Vec3d getVelocity();

    @Shadow
    public abstract void setVelocity(Vec3d velocity);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    protected static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        throw new AssertionError();
    }

    @Inject(method = "isGlowing()Z", at = @At("HEAD"), cancellable = true)
    private void onIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        EventGlowing event = new EventGlowing((Entity) (Object) this, false);
        EventManager.post(event);
        if (event.isGlowing()) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "slowMovement", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3d modifySlowMovementMultiplier(Vec3d multiplier, BlockState state) {
        StuckInBlockEvent event = new StuckInBlockEvent((Entity) (Object) this, state, multiplier);
        EventManager.post(event);
        return event.getMultiplier();
    }

    @Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
    private void onSlowMovement(BlockState state, Vec3d multiplier, CallbackInfo ci) {
        NoSlow noSlow = ModuleManager.getModule(NoSlow.class);
        if (noSlow != null && noSlow.isEnabled() && noSlow.shouldCancelSlow()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateVelocity", at = @At("HEAD"), cancellable = true)
    private void onUpdateVelocity(float speed, Vec3d movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity)) {
            return;
        }

        if (GlobalSetting.isMoveFixDisabled()) {
            return;
        }

        StrafeEvent event = EventManager.post(new StrafeEvent((float) movementInput.z, (float) movementInput.x, this.getYaw(), speed));
        Vec3d velocity = movementInputToVelocity(
                new Vec3d(event.getStrafe(), movementInput.y, event.getForward()),
                event.getFriction(),
                event.getYaw()
        );
        this.setVelocity(this.getVelocity().add(velocity));
        ci.cancel();
    }

    @Inject(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void onGetRotationVec(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!((Object) this instanceof ClientPlayerEntity)) {
            return;
        }

        RotationManager manager = RotationManager.get();
        Rotation rotations = manager.getRotations();
        if (rotations != null) {
            cir.setReturnValue(RayTraceUtil.getVectorForRotation(rotations.getYaw(), rotations.getPitch()));
        }
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void changeFreelookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        FreeLook freelook = ModuleManager.getModule(FreeLook.class);
        if (freelook == null || !freelook.isEnabled() || !((Object) this instanceof ClientPlayerEntity)) {
            return;
        }

        this.ardion$cameraPitch = MathHelper.clamp(this.ardion$cameraPitch + (float) (cursorDeltaY * 0.15D), -90.0F, 90.0F);
        this.ardion$cameraYaw += (float) (cursorDeltaX * 0.15D);
        ci.cancel();
    }

    @Override
    @Unique
    public float ardion$getCameraPitch() {
        return this.ardion$cameraPitch;
    }

    @Override
    @Unique
    public float ardion$getCameraYaw() {
        return this.ardion$cameraYaw;
    }

    @Override
    @Unique
    public void ardion$setCameraPitch(float pitch) {
        this.ardion$cameraPitch = pitch;
    }

    @Override
    @Unique
    public void ardion$setCameraYaw(float yaw) {
        this.ardion$cameraYaw = yaw;
    }
}
