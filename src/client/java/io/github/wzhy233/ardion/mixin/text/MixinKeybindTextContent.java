package io.github.wzhy233.ardion.mixin.text;

import io.github.wzhy233.ardion.util.client.SpooferProtectionUtil;
import net.minecraft.text.KeybindTextContent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeybindTextContent.class)
public class MixinKeybindTextContent {
    @Shadow
    @Final
    private String key;

    private boolean ardion$fromRemotePacket;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void markRemoteKeybind(String key, CallbackInfo ci) {
        this.ardion$fromRemotePacket = SpooferProtectionUtil.isDecodingRemotePacket();
    }

    @Inject(method = "getTranslated", at = @At("HEAD"), cancellable = true)
    private void blockRemoteKeybindResolution(CallbackInfoReturnable<Text> cir) {
        if (this.ardion$fromRemotePacket && SpooferProtectionUtil.protectsTranslationKeys()) {
            cir.setReturnValue(Text.literal(this.key));
        }
    }
}
