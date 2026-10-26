package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.event.impl.render.RenderTextEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.util.text.SpoofTextUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public class MixinTextRenderer {

    @ModifyVariable(method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText modifyDrawOrderedText(OrderedText text) {
        RenderTextEvent event = EventManager.post(new RenderTextEvent(text));
        return Spoofer.spoof(event.getText());
    }

    @ModifyVariable(method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyDrawString(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifyDrawText(Text text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "drawWithOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText modifyDrawWithOutlineText(OrderedText text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyPrepareString(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText modifyPrepareOrderedText(OrderedText text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyGetStringWidth(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyGetVisitableWidth(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private OrderedText modifyGetOrderedTextWidth(OrderedText text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "trimToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyTrimStringToWidthBackwards(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "trimToWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyTrimStringToWidth(String text) {
        return Spoofer.spoof(text);
    }

    @ModifyVariable(method = "trimToWidth(Lnet/minecraft/text/StringVisitable;I)Lnet/minecraft/text/StringVisitable;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyTrimVisitableToWidth(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "getWrappedLinesHeight", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyWrappedLinesHeight(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "wrapLines", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyWrapLines(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "wrapLinesWithoutLanguage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private StringVisitable modifyWrapLinesWithoutLanguage(StringVisitable text) {
        return SpoofTextUtil.spoof(text);
    }

    @ModifyVariable(method = "mirror", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyMirror(String text) {
        return Spoofer.spoof(text);
    }
}
