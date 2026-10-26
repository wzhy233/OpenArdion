package io.github.wzhy233.ardion.mixin.entity;

import io.github.wzhy233.ardion.module.impl.render.CustomSkin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class MixinAbstractClientPlayerEntity {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void ardion$customSkin(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        SkinTextures textures = CustomSkin.textures(player.getUuid(), player.getGameProfile().name());
        if (textures != null) {
            cir.setReturnValue(textures);
        }
    }
}
