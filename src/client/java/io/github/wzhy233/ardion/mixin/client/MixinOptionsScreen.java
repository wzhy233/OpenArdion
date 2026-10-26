package io.github.wzhy233.ardion.mixin.client;

import io.github.wzhy233.ardion.gui.screen.ClickGuiScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class MixinOptionsScreen extends Screen {
    protected MixinOptionsScreen() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Ardion"),
                btn -> this.client.setScreen(new ClickGuiScreen())
        ).dimensions(4, 5, 60, 20).build());
    }
}
