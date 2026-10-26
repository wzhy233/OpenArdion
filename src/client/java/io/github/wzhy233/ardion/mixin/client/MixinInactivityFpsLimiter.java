package io.github.wzhy233.ardion.mixin.client;

import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public class MixinInactivityFpsLimiter {
    @Shadow
    private int maxFps;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void ardion$disableInactiveFpsLimit(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(maxFps);
    }
}
