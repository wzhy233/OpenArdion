package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.render.Rotations;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void ardion$applyRotationRenderState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) {
            return;
        }

        Rotations rotations = ModuleManager.getModule(Rotations.class);
        if (rotations == null || !rotations.isEnabled()) {
            return;
        }

        Rotation renderRotation = rotations.getRenderRotation(tickDelta);
        if (renderRotation == null) {
            return;
        }

        if (rotations.shouldRenderBody()) {
            state.bodyYaw = renderRotation.getYaw();
        }
        if (rotations.shouldRenderHead()) {
            state.relativeHeadYaw = MathHelper.wrapDegrees(renderRotation.getYaw() - state.bodyYaw);
            state.pitch = renderRotation.getPitch();
        } else if (rotations.shouldRenderBody()) {
            state.relativeHeadYaw = MathHelper.wrapDegrees(
                    MathHelper.lerpAngleDegrees(tickDelta, entity.lastHeadYaw, entity.headYaw) - state.bodyYaw);
        }
    }
}
