package io.github.wzhy233.ardion.mixin.client;

import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class MixinClientBrandRetriever {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void spoofClientBrand(CallbackInfoReturnable<String> cir) {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        if (spoofer != null && spoofer.isEnabled() && spoofer.spoofsClientBrand()) {
            cir.setReturnValue(spoofer.fakeBrand());
        }
    }
}
