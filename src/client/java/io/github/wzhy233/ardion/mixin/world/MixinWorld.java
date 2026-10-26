package io.github.wzhy233.ardion.mixin.world;

import io.github.wzhy233.ardion.module.impl.render.WeatherChanger;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void overrideRain(float tickProgress, CallbackInfoReturnable<Float> cir) {
        WeatherChanger module = ModuleManager.getModule(WeatherChanger.class);
        if ((Object) this instanceof ClientWorld && module != null && module.isEnabled()) {
            cir.setReturnValue(module.getRainGradient());
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void overrideThunder(float tickProgress, CallbackInfoReturnable<Float> cir) {
        WeatherChanger module = ModuleManager.getModule(WeatherChanger.class);
        if ((Object) this instanceof ClientWorld && module != null && module.isEnabled()) {
            cir.setReturnValue(module.getThunderGradient());
        }
    }
}
