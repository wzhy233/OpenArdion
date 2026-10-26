package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.render.CustomSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void ardion$forceCape(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        CustomSkin module = CustomSkin.get();
        if (module == null || !module.isEnabled() || !module.capeEnabled()) {
            return;
        }
        if (entity != MinecraftClient.getInstance().player || state.skinTextures == null || state.skinTextures.cape() == null) {
            return;
        }
        state.capeVisible = true;
    }
}
