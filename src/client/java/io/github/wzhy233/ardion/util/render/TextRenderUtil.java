package io.github.wzhy233.ardion.util.render;

import io.github.wzhy233.ardion.util.text.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class TextRenderUtil {
    private TextRenderUtil() {
    }

    public static TextRenderer getTextRenderer() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static int getTextWidth(String text) {
        return getTextRenderer().getWidth(TextUtil.safe(text));
    }

    public static void drawText(DrawContext context, String text, int x, int y, int color, boolean shadow) {
        context.drawText(getTextRenderer(), Text.literal(TextUtil.safe(text)), x, y, color, shadow);
    }

    public static void drawText(DrawContext context, Text text, int x, int y, int color, boolean shadow) {
        context.drawText(getTextRenderer(), text, x, y, color, shadow);
    }

    public static void drawHorizontalLine(DrawContext context, int startX, int endX, int y, int color) {
        context.fill(Math.min(startX, endX), y, Math.max(startX, endX) + 1, y + 1, color);
    }
}
