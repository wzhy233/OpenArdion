package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.render.NoRender;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatusEffectsDisplay.class)
public class MixinStatusEffectsDisplay {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffectsHud(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        NoRender module = ModuleManager.getModule(NoRender.class);
        if (module != null && module.isEnabled() && module.disableEffects()) {
            ci.cancel();
        }
    }
}
