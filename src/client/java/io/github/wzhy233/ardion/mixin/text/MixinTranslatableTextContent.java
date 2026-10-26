package io.github.wzhy233.ardion.mixin.text;

import io.github.wzhy233.ardion.util.client.SpooferProtectionUtil;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslatableTextContent.class)
public class MixinTranslatableTextContent {
    @Shadow
    @Final
    private String key;

    @Shadow
    @Final
    private String fallback;

    @Unique
    private boolean ardion$fromRemotePacket;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void markRemoteTranslation(String key, String fallback, Object[] args, CallbackInfo ci) {
        this.ardion$fromRemotePacket = SpooferProtectionUtil.isDecodingRemotePacket();
    }

    @Redirect(method = "updateTranslations", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;)Ljava/lang/String;"))
    private String blockRemoteTranslation(Language language, String key) {
        if (this.ardion$fromRemotePacket && SpooferProtectionUtil.protectsTranslationKeys()) {
            return this.key;
        }
        return language.get(key);
    }

    @Redirect(method = "updateTranslations", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Language;get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
    private String blockRemoteTranslationWithFallback(Language language, String key, String fallback) {
        if (this.ardion$fromRemotePacket && SpooferProtectionUtil.protectsTranslationKeys()) {
            return this.fallback == null ? this.key : this.fallback;
        }
        return language.get(key, fallback);
    }
}
