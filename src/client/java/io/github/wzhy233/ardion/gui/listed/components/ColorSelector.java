package io.github.wzhy233.ardion.gui.listed.components;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.value.impl.ColorValue;

import java.awt.Color;

public final class ColorSelector {
    private final ColorValue colorValue;
    private int lastArea = -1;

    public ColorSelector(ColorValue colorValue) {
        this.colorValue = colorValue;
    }

    public void draw(Canvas canvas, float x, float y, float width, float height, float mouseX, float mouseY,
                     boolean isButtonDown) {
        drawGradientLR(canvas, x, y, x + width, y + height, -1, Color.HSBtoRGB(colorValue.getHue(), 1, 1));
        drawGradientTB(canvas, x, y, x + width, y + height, new Color(255, 255, 255, 0).getRGB(), new Color(0, 0, 0, 255).getRGB());

        for (int i = 4; i >= 0; i--) {
            drawGradientTB(canvas, x + 42, y + height / 5 * (4 - i), x + 48, y + height / 5 * (4 - i + 1),
                    Color.HSBtoRGB(1 - 0.2F * (i + 1), 1, 1), Color.HSBtoRGB(1 - 0.2F * i, 1, 1));
        }
        Skia.drawRect(x + 42, y + colorValue.getHue() * height, 6, 1, new Color(255, 255, 255, 255));

        drawGradientTB(canvas, x + 50, y, x + 56, y + height, reAlpha(colorValue.getColor(), 1), reAlpha(colorValue.getColor(), 0));
        Skia.drawRect(x + 50, y + (1.0F - colorValue.getAlpha()) * height, 6, 1, new Color(255, 255, 255, 255));

        double sX = colorValue.getSaturation() * width;
        double bY = height - colorValue.getBrightness() * height;
        drawCircle(canvas, x + sX, y + bY, 2.4, new Color(0, 0, 0, 255).getRGB());
        drawCircle(canvas, x + sX, y + bY, 2, new Color(255, 255, 255, 255).getRGB());

        if (isButtonDown) {
            if ((isHovered(x, y, x + width, y + height, mouseX, mouseY) && lastArea == -1) || lastArea == 0) {
                colorValue.setSaturation(Math.max(Math.min((mouseX - x) / width, 1), 0));
                colorValue.setBrightness(Math.max(Math.min(1 - (mouseY - y) / height, 1), 0));
                lastArea = 0;
            } else {
                float max = Math.max(Math.min((mouseY - y) / height, 1), 0);
                if ((isHovered(x + 42, y, x + 48, y + height, mouseX, mouseY) && lastArea == -1) || lastArea == 1) {
                    colorValue.setHue(max);
                    lastArea = 1;
                } else if ((isHovered(x + 50, y, x + 56, y + height, mouseX, mouseY) && lastArea == -1) || lastArea == 2) {
                    colorValue.setAlpha(1.0F - max);
                    lastArea = 2;
                }
            }
        } else {
            lastArea = -1;
        }
    }

    private void drawGradientTB(Canvas canvas, float x, float y, float x1, float y1, int topColor, int bottomColor) {
        try (Paint paint = new Paint();
             Shader shader = Shader.makeLinearGradient(x, y, x, y1, new int[]{topColor, bottomColor})) {
            paint.setShader(shader);
            canvas.drawRect(Rect.makeLTRB(x, y, x1, y1), paint);
        }
    }

    private void drawGradientLR(Canvas canvas, float x, float y, float x1, float y1, int leftColor, int rightColor) {
        try (Paint paint = new Paint();
             Shader shader = Shader.makeLinearGradient(x, y, x1, y, new int[]{leftColor, rightColor})) {
            paint.setShader(shader);
            canvas.drawRect(Rect.makeLTRB(x, y, x1, y1), paint);
        }
    }

    private void drawCircle(Canvas canvas, double x, double y, double radius, int color) {
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            canvas.drawCircle((float) x, (float) y, (float) radius, paint);
        }
    }

    private boolean isHovered(float x, float y, float x2, float y2, float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2;
    }

    private int reAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
    }
}
