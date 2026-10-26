package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.util.text.SpoofTextUtil;
import net.minecraft.client.font.TextHandler;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextHandler.class)
public class MixinTextHandler {
    @ModifyVariable(method = "getWidth(Ljava/lang/String;)F", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyGetStringWidth(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)F", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyGetVisitableWidth(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)F", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText modifyGetOrderedTextWidth(OrderedText text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "getTrimmedLength", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyGetTrimmedLength(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "trimToWidth(Ljava/lang/String;ILnet/minecraft/text/Style;)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyTrimStringToWidth(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "trimToWidthBackwards", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyTrimStringToWidthBackwards(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "trimToWidth(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;)Lnet/minecraft/text/StringVisitable;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyTrimVisitableToWidth(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "getEndingIndex", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyGetEndingIndex(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "wrapLines(Ljava/lang/String;ILnet/minecraft/text/Style;ZLnet/minecraft/client/font/TextHandler$LineWrappingConsumer;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyConsumerWrapLines(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "wrapLines(Ljava/lang/String;ILnet/minecraft/text/Style;)Ljava/util/List;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyStringWrapLines(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;)Ljava/util/List;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyVisitableWrapLines(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyBiConsumerWrapLines(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }
}
