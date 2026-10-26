package io.github.wzhy233.ardion.mixin.network;

import io.github.wzhy233.ardion.module.impl.render.CustomSkin;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class MixinPlayerListEntry {
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void ardion$customSkin(CallbackInfoReturnable<SkinTextures> cir) {
        var profile = ((PlayerListEntry) (Object) this).getProfile();
        SkinTextures textures = CustomSkin.textures(profile.id(), profile.name());
        if (textures != null) {
            cir.setReturnValue(textures);
        }
    }
}
