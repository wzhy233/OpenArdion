package io.github.wzhy233.ardion.util.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

public final class GuiRenderUtil {
    private GuiRenderUtil() {
    }

    public static void fillBound(DrawContext context, float left, float top, float width, float height, int color) {
        context.fill(Math.round(left), Math.round(top), Math.round(left + width), Math.round(top + height), color);
    }

    public static void drawRoundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        radius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        if (radius <= 0.0F) {
            fillBound(context, x, y, width, height, color);
            return;
        }

        int r = Math.max(1, Math.round(radius));
        fillBound(context, x + r, y, width - r * 2.0F, height, color);
        fillBound(context, x, y + r, r, height - r * 2.0F, color);
        fillBound(context, x + width - r, y + r, r, height - r * 2.0F, color);

        for (int i = 0; i < r; i++) {
            float dy = r - i - 0.5F;
            int dx = Math.round((float) Math.sqrt(Math.max(0.0F, r * r - dy * dy)));
            fillBound(context, x + r - dx, y + i, width - (r - dx) * 2.0F, 1.0F, color);
            fillBound(context, x + r - dx, y + height - i - 1.0F, width - (r - dx) * 2.0F, 1.0F, color);
        }
    }

    public static void drawTracer(DrawContext context, float x, float y, float size, float widthDiv, float heightDiv, int color) {
        float leftX = x - size / widthDiv;
        float midY = y + size / heightDiv;
        float rightX = x + size / widthDiv;
        float bottomY = y + size;

        drawTriangle(context, x, y, leftX, bottomY, x, midY, color);
        drawTriangle(context, x, y, x, midY, rightX, bottomY, color);
    }

    private static void drawTriangle(DrawContext context, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));
        for (int y = minY; y <= maxY; y++) {
            float[] xs = new float[2];
            int count = 0;
            count = intersect(xs, count, y + 0.5F, x1, y1, x2, y2);
            count = intersect(xs, count, y + 0.5F, x2, y2, x3, y3);
            count = intersect(xs, count, y + 0.5F, x3, y3, x1, y1);
            if (count == 2) {
                fillBound(context, Math.min(xs[0], xs[1]), y, Math.abs(xs[0] - xs[1]) + 1.0F, 1.0F, color);
            }
        }
    }

    private static int intersect(float[] xs, int count, float y, float x1, float y1, float x2, float y2) {
        if ((y >= y1 && y < y2) || (y >= y2 && y < y1)) {
            xs[count++] = x1 + (y - y1) * (x2 - x1) / (y2 - y1);
        }
        return count;
    }

    public static void drawScaledText(DrawContext context, String text, float x, float y, int color, boolean shadow, float scale) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        context.drawText(renderer, Text.literal(text), 0, 0, color, shadow);
        matrices.popMatrix();
    }

    public static float getScaledTextWidth(String text, float scale) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text) * scale;
    }
}
