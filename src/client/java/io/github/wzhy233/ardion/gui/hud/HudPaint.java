package io.github.wzhy233.ardion.gui.hud;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.render.FilterCache;
import io.github.wzhy233.ardion.util.render.Skia;

import java.awt.Color;

public final class HudPaint {
    private HudPaint() {
    }

    public static Shader createGradientShader(float textX, float textY, float textWidth, float textHeight,
                                              int startColor, int endColor) {
        return switch (HudSetting.gradientType()) {
            case "horizontal" -> Shader.makeLinearGradient(textX, textY, textX + textWidth, textY,
                    new int[]{startColor, endColor}, null, GradientStyle.DEFAULT);
            case "vertical" -> Shader.makeLinearGradient(textX, textY, textX, textY + textHeight,
                    new int[]{startColor, endColor}, null, GradientStyle.DEFAULT);
            case "diagonal" -> Shader.makeLinearGradient(textX, textY, textX + textWidth, textY + textHeight,
                    new int[]{startColor, endColor}, null, GradientStyle.DEFAULT);
            case "radial" -> Shader.makeRadialGradient(textX + textWidth / 2.0F, textY + textHeight / 2.0F,
                    Math.max(textWidth, textHeight) / 2.0F, new int[]{startColor, endColor}, null, GradientStyle.DEFAULT);
            default -> createDegGradient(textX, textY, textWidth, textHeight, startColor, endColor, HudSetting.gradientAngle());
        };
    }

    private static Shader createDegGradient(float textX, float textY, float textWidth, float textHeight,
                                            int startColor, int endColor, float angle) {
        double radian = Math.toRadians(angle);
        float gradientLength = (float) Math.sqrt(textWidth * textWidth + textHeight * textHeight);
        float startY = textY + textHeight;
        float endX = textX + gradientLength * (float) Math.cos(radian);
        float endY = startY - gradientLength * (float) Math.sin(radian);
        return Shader.makeLinearGradient(textX, startY, endX, endY,
                new int[]{startColor, endColor}, null, GradientStyle.DEFAULT);
    }

    public static void renderOuterGlow(String text, Font font, float textX, float textY, Shader colorShader) {
        if (!HudSetting.glow()) return;
        float glowSize = HudSetting.glowSize();
        float glowIntensity = HudSetting.glowIntensity();
        MaskFilter outer = FilterCache.blur(FilterBlurMode.OUTER, glowSize);
        MaskFilter inner = FilterCache.blur(FilterBlurMode.NORMAL, glowSize / 2.0F);

        Paint glowPaint = Skia.scratchPaint();
        glowPaint.setShader(colorShader);
        glowPaint.setAlphaf(glowIntensity);
        glowPaint.setMaskFilter(outer);
        Skia.drawText(text, textX, textY, font, glowPaint);

        Paint innerGlowPaint = Skia.scratchPaint();
        innerGlowPaint.setShader(colorShader);
        innerGlowPaint.setAlphaf(glowIntensity * 0.7F);
        innerGlowPaint.setMaskFilter(inner);
        Skia.drawText(text, textX, textY, font, innerGlowPaint);
    }

    public static void renderRectOuterGlow(RRect rect, Shader colorShader) {
        renderRectOuterGlow(rect, colorShader, 1.0F);
    }

    private static void renderRectOuterGlow(RRect rect, Shader colorShader, float opacity) {
        if (!HudSetting.glow() || opacity <= 0.0F) return;
        float glowSize = HudSetting.glowSize();
        float glowIntensity = HudSetting.glowIntensity() * opacity;
        MaskFilter outer = FilterCache.blur(FilterBlurMode.OUTER, glowSize);

        Paint glowPaint = Skia.scratchPaint();
        glowPaint.setShader(colorShader);
        glowPaint.setAlphaf(glowIntensity);
        glowPaint.setMaskFilter(outer);
        Canvas canvas = Skia.getCanvas();
        canvas.save();
        try {
            canvas.clipRRect(rect, ClipMode.DIFFERENCE, true);
            canvas.drawRRect(rect, glowPaint);
        } finally {
            canvas.restore();
        }
    }

    public static void renderHudCard(float x, float y, float width, float height, float radius, Color glowColor, int alpha) {
        if (alpha <= 0) return;
        if (HudSetting.glow()) {
            Color glowEnd = new Color(
                    Math.min(255, glowColor.getRed() + 48),
                    Math.min(255, glowColor.getGreen() + 48),
                    Math.min(255, glowColor.getBlue() + 48),
                    glowColor.getAlpha()
            );
            try (Shader glowShader = createGradientShader(x, y, width, height, glowColor.getRGB(), glowEnd.getRGB())) {
                renderRectOuterGlow(RRect.makeXYWH(x, y, width, height, radius), glowShader, alpha / 255.0F);
            }
        }
        Skia.drawRoundedRect(x, y, width, height, radius, new Color(0, 0, 0, alpha));
    }
}
