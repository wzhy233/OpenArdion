package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.access.CameraEntityAccess;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.module.impl.combat.KillAura;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.module.impl.render.FreeLook;
import io.github.wzhy233.ardion.module.impl.render.ViewClip;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 1))
    private void interpolateLockedView(Args args, World area, Entity focusedEntity, boolean thirdPerson,
                                       boolean inverseView, float tickDelta) {
        if (!(focusedEntity instanceof ClientPlayerEntity)) return;
        KillAura aura = ModuleManager.getModule(KillAura.class);
        FreeLook freelook = ModuleManager.getModule(FreeLook.class);
        if (aura == null || !aura.isEnabled() || !aura.isViewLocked()
                || (freelook != null && freelook.isEnabled())) return;
        Rotation rotation = RotationManager.get().getRenderRotation(tickDelta);
        if (rotation != null) {
            args.set(0, rotation.getYaw());
            args.set(1, rotation.getPitch());
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void applyViewClip(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
        ViewClip module = ModuleManager.getModule(ViewClip.class);
        if (module != null && module.isEnabled()) {
            cir.setReturnValue(desiredCameraDistance * module.getCameraScale());
        }
    }

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 1, shift = At.Shift.AFTER))
    private void applyFreelookRotation(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        FreeLook freelook = ModuleManager.getModule(FreeLook.class);
        if (freelook == null || !freelook.isEnabled()
                || !(focusedEntity instanceof ClientPlayerEntity)
                || !(focusedEntity instanceof CameraEntityAccess access)) {
            return;
        }

        this.setRotation(access.ardion$getCameraYaw(), access.ardion$getCameraPitch());
    }
}
