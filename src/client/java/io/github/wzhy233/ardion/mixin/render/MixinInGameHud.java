package io.github.wzhy233.ardion.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.impl.render.HideScoreBoard;
import io.github.wzhy233.ardion.module.impl.render.NoRender;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void hideScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        HideScoreBoard module = ModuleManager.getModule(HideScoreBoard.class);
        if (module != null && module.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NoRender module = ModuleManager.getModule(NoRender.class);
        if (module != null && module.isEnabled() && module.disableEffects()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "method_55439", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"))
    private MutableText modifyScoreboardName(MutableText name, Scoreboard scoreboard, NumberFormat numberFormat, ScoreboardEntry entry) {
        return spoofMutable(name);
    }

    @ModifyExpressionValue(method = "method_55439", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardEntry;formatted(Lnet/minecraft/scoreboard/number/NumberFormat;)Lnet/minecraft/text/MutableText;"))
    private MutableText modifyScoreboardScore(MutableText score, Scoreboard scoreboard, NumberFormat numberFormat, ScoreboardEntry entry) {
        return spoofMutable(score);
    }

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifyTitle(Text title) {
        return spoof(title);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifySubtitle(Text subtitle) {
        return spoof(subtitle);
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifyOverlayMessage(Text message) {
        return spoof(message);
    }

    private Text spoof(Text text) {
        return Spoofer.spoof(text);
    }

    private MutableText spoofMutable(MutableText text) {
        Text spoofed = spoof(text);
        return spoofed instanceof MutableText mutableText ? mutableText : text;
    }
}
