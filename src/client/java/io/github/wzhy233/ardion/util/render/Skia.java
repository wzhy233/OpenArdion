package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Skia {
    private static final ThreadLocal<Paint> SCRATCH_PAINT = ThreadLocal.withInitial(Paint::new);
    private static final ImageHelper IMAGE_HELPER = new ImageHelper();

    private Skia() {
    }

    public static ImageHelper getImageHelper() {
        return IMAGE_HELPER;
    }

    public static void query(Consumer<Canvas> drawingLogic) {
        SkiaRenderManager.get().draw(drawingLogic);
    }

    public static void drawRect(float x, float y, float width, float height, Color color) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        if (width <= 0.0F || height <= 0.0F || !Float.isFinite(x + y + width + height)) return;
        getCanvas().drawRect(Rect.makeXYWH(x, y, width, height), getPaint(color));
    }

    public static void drawCircle(float x, float y, float radius, Color color) {
        getCanvas().drawCircle(x, y, radius, getPaint(color));
    }

    public static void drawCircle(float x, float y, float radius, float strokeWidth, Color color) {
        Paint paint = getPaint(color);
        paint.setMode(PaintMode.STROKE);
        paint.setStrokeWidth(strokeWidth);
        getCanvas().drawCircle(x, y, radius, paint);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        if (width <= 0.0F || height <= 0.0F || !Float.isFinite(x + y + width + height + radius)) return;
        radius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), getPaint(color));
    }

    public static void drawRoundedRectVarying(float x, float y, float width, float height, float topLeft,
                                              float topRight, float bottomRight, float bottomLeft, Color color) {
        float[] corners = new float[]{topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft};
        getCanvas().drawRRect(RRect.makeComplexXYWH(x, y, width, height, corners), getPaint(color));
    }

    public static void drawShadow(float x, float y, float width, float height, float radius) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        if (width <= 0.0F || height <= 0.0F || !Float.isFinite(x + y + width + height + radius)) return;
        radius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        Paint paint = scratchPaint();
        paint.setImageFilter(FilterCache.dropShadowOnly(0F, 0F, 1F, 1F, 0x78000000));
        getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
    }

    public static void drawCustomOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                         Color color, Shader shader) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        float halfStroke = strokeWidth / 2.0F;
        Paint paint = getPaint(color);
        paint.setShader(shader);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);
        try (Path path = Path.makeRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke, width - strokeWidth,
                height - strokeWidth, radius - halfStroke))) {
            getCanvas().drawPath(path, paint);
        }
    }

    public static void drawOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                   Color color) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        float halfStroke = strokeWidth / 2.0F;
        Paint paint = getPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);
        try (Path path = Path.makeRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke, width - strokeWidth,
                height - strokeWidth, radius - halfStroke))) {
            getCanvas().drawPath(path, paint);
        }
    }

    public static void drawRoundedRectOutline(float x, float y, float width, float height, float radius, Color color, float strokeWidth) {
        drawOutline(x, y, width, height, radius, strokeWidth, color);
    }

    public static void drawSmoothOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                         Color color) {
        Paint paint = getPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);
        paint.setMaskFilter(FilterCache.blur(FilterBlurMode.NORMAL, 0.5F));
        float halfStroke = strokeWidth / 2.0F;
        try (Path path = Path.makeRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke, width - strokeWidth,
                height - strokeWidth, radius - halfStroke))) {
            getCanvas().drawPath(path, paint);
        }
    }

    public static void drawDashedOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                         Color color, float dashLength, float dashGap) {
        Paint paint = getPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);
        float halfStroke = strokeWidth / 2.0F;
        try (Path path = Path.makeRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke, width - strokeWidth,
                height - strokeWidth, radius - halfStroke));
             PathEffect dashEffect = PathEffect.makeDash(new float[]{dashLength, dashGap}, 0.0F)) {
            paint.setPathEffect(dashEffect);
            getCanvas().drawPath(path, paint);
        }
    }

    public static void drawImage(String path, float x, float y, float width, float height) {
        path = "imgs/" + path;
        if (IMAGE_HELPER.load(path)) {
            getCanvas().drawImageRect(IMAGE_HELPER.get(path), Rect.makeXYWH(x, y, width, height));
        }
    }

    public static void drawImage(File file, float x, float y, float width, float height) {
        if (IMAGE_HELPER.load(file)) {
            getCanvas().drawImageRect(IMAGE_HELPER.get(file.getName()), Rect.makeXYWH(x, y, width, height));
        }
    }

    public static void drawRoundedImage(String filePath, float x, float y, float width, float height, float radius) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(filePath, x, y, width, height);
            restore();
        }
    }

    public static void drawRoundedImage(File file, float x, float y, float width, float height, float radius) {
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            drawImage(file, x, y, width, height);
            restore();
        }
    }

    public static void drawArc(float x, float y, float radius, float startAngle, float endAngle, float strokeWidth, Color color) {
        Paint paint = getPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);
        getCanvas().drawArc(x - radius, y - radius, x + radius, y + radius, startAngle - 90, endAngle, false, paint);
    }

    public static void drawLine(float x, float y, float endX, float endY, float width, Color color) {
        Paint paint = getPaint(color);
        paint.setStroke(true);
        paint.setStrokeWidth(width);
        getCanvas().drawLine(x, y, endX, endY, paint);
    }

    public static void drawGradientRoundedRect(float x, float y, float width, float height, float radius,
                                               Color color1, Color color2) {
        long currentTime = System.nanoTime();
        double speed = 0.0000000006D;
        double tick = (currentTime * speed) % (2 * Math.PI);
        float max = Math.max(width, height);
        float startX = x + width / 2.0F - (max / 2.0F) * (float) Math.cos(tick);
        float startY = y + height / 2.0F - (max / 2.0F) * (float) Math.sin(tick);
        float endX = x + width / 2.0F + (max / 2.0F) * (float) Math.cos(tick);
        float endY = y + height / 2.0F + (max / 2.0F) * (float) Math.sin(tick);
        int skColor1 = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(), color1.getRed(), color1.getGreen(), color1.getBlue());
        int skColor2 = io.github.humbleui.skija.Color.makeARGB(color2.getAlpha(), color2.getRed(), color2.getGreen(), color2.getBlue());
        int skColorMid = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(),
                (color1.getRed() + color2.getRed()) / 2, (color1.getGreen() + color2.getGreen()) / 2,
                (color1.getBlue() + color2.getBlue()) / 2);
        Paint paint = scratchPaint();
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius));
             Shader shader = Shader.makeLinearGradient(new Point(startX, startY), new Point(endX, endY),
                     new int[]{skColor1, skColorMid, skColor2}, new float[]{0.0F, 0.5F, 1.0F})) {
            paint.setShader(shader);
            getCanvas().drawPath(path, paint);
        }
    }

    public static void clipPath(Path path, ClipMode mode, boolean antialias) {
        getCanvas().clipPath(path, mode, antialias);
    }

    public static void clipPath(Path path) {
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
    }

    public static void clip(float x, float y, float width, float height, float radius, ClipMode mode) {
        if (width <= 0.0F || height <= 0.0F || !Float.isFinite(x + y + width + height + radius)) return;
        radius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            clipPath(path, mode, true);
        }
    }

    public static void clip(float x, float y, float width, float height, float radius) {
        clip(x, y, width, height, radius, ClipMode.INTERSECT);
    }

    public static void clip(float x, float y, float width, float height, float topLeft, float topRight,
                            float bottomRight, float bottomLeft) {
        float[] corners = new float[]{topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft};
        try (Path path = Path.makeRRect(RRect.makeComplexXYWH(x, y, width, height, corners))) {
            clipPath(path, ClipMode.INTERSECT, true);
        }
    }

    public static void drawText(String text, float x, float y, Font font, Paint paint) {
        String safe = sanitizeText(text);
        Rect bounds = measureTextSafe(safe, font);
        drawString(safe, x - bounds.getLeft(), y - bounds.getTop(), font, paint);
    }

    public static void drawText(String text, float x, float y, Color color, Font font) {
        drawText(text, x, y, font, getPaint(color));
    }

    public static void drawRawText(String text, float x, float y, Font font, Paint paint) {
        String safe = sanitizeText(text, false);
        Rect bounds = measureTextSafe(safe, font);
        drawString(safe, x - bounds.getLeft(), y - bounds.getTop(), font, paint);
    }

    public static void drawCenteredText(String text, float x, float y, Color color, Font font) {
        String safe = sanitizeText(text);
        Rect bounds = measureTextSafe(safe, font);
        drawString(safe, x - bounds.getLeft() - bounds.getWidth() / 2.0F, y - bounds.getTop(), font, getPaint(color));
    }

    public static void drawHeightCenteredText(String text, float x, float y, Color color, Font font) {
        String safe = sanitizeText(text);
        FontMetrics metrics = font.getMetrics();
        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2.0F - metrics.getAscent();
        drawString(safe, x - measureTextSafe(safe, font).getLeft(), textCenterY, font, getPaint(color));
    }

    public static void drawFullCenteredText(String text, float x, float y, Color color, Font font) {
        String safe = sanitizeText(text);
        Rect bounds = measureTextSafe(safe, font);
        FontMetrics metrics = font.getMetrics();
        float textCenterX = x - bounds.getLeft() - bounds.getWidth() / 2.0F;
        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2.0F - metrics.getAscent();
        drawString(safe, textCenterX, textCenterY, font, getPaint(color));
    }

    public static Rect getTextBounds(String text, Font font) {
        return measureTextSafe(sanitizeText(text), font);
    }

    public static Rect getRawTextBounds(String text, Font font) {
        return measureTextSafe(sanitizeText(text, false), font);
    }

    public static List<String> splitTextToLines(String text, Font font, float width) {
        List<String> lines = new ArrayList<>();
        text = sanitizeText(text);
        if (text == null || text.isEmpty() || width <= 0) return lines;
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (getTextBounds(word, font).getWidth() > width) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
                StringBuilder longWordPart = new StringBuilder();
                for (char c : word.toCharArray()) {
                    longWordPart.append(c);
                    if (getTextBounds(longWordPart.toString(), font).getWidth() > width) {
                        longWordPart.deleteCharAt(longWordPart.length() - 1);
                        lines.add(longWordPart.toString());
                        longWordPart = new StringBuilder(String.valueOf(c));
                    }
                }
                currentLine = longWordPart;
                continue;
            }
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (getTextBounds(testLine, font).getWidth() <= width) {
                if (!currentLine.isEmpty()) currentLine.append(' ');
                currentLine.append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (!currentLine.isEmpty()) lines.add(currentLine.toString());
        return lines;
    }

    public static String getLimitTextWithDot(String text, Font font, float width) {
        text = sanitizeText(text);
        boolean removed = false;
        while (!text.isEmpty() && getTextBounds(text, font).getWidth() > width - getTextBounds("...", font).getWidth()) {
            text = text.substring(0, text.length() - 1);
            removed = true;
        }
        return text + (removed ? "..." : "");
    }

    public static Paint scratchPaint() {
        Paint paint = SCRATCH_PAINT.get();
        paint.reset();
        paint.setAntiAlias(true);
        return paint;
    }

    private static Paint getPaint(Color color) {
        Paint paint = scratchPaint();
        paint.setARGB(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
        return paint;
    }

    private static void drawString(String text, float x, float y, Font font, Paint paint) {
        if (text == null || text.isEmpty()) return;
        try {
            getCanvas().drawString(text, x, y, font, paint);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static Rect measureTextSafe(String text, Font font) {
        if (text == null || text.isEmpty()) return Rect.makeLTRB(0.0F, 0.0F, 0.0F, 0.0F);
        try {
            return font.measureText(text);
        } catch (IllegalArgumentException ignored) {
            return Rect.makeLTRB(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    public static String sanitizeText(String text) {
        return sanitizeText(text, true);
    }

    private static String sanitizeText(String text, boolean replaceNames) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00A7') {
                if (i + 1 < text.length() && isLegacyFormattingCode(text.charAt(i + 1))) {
                    i++;
                }
                continue;
            }
            builder.append(ch);
        }
        String spoofed = replaceNames ? Spoofer.spoofPlain(builder.toString()) : builder.toString();
        if (spoofed == null || spoofed.isEmpty()) return "";
        StringBuilder safe = new StringBuilder(spoofed.length());
        for (int i = 0; i < spoofed.length(); i++) {
            char ch = spoofed.charAt(i);
            safe.append(Character.isSurrogate(ch) ? '?' : ch);
        }
        return safe.toString();
    }

    private static boolean isLegacyFormattingCode(char code) {
        char lower = Character.toLowerCase(code);
        return lower >= '0' && lower <= '9'
                || lower >= 'a' && lower <= 'f'
                || lower >= 'k' && lower <= 'o'
                || lower == 'r'
                || lower == 'x';
    }

    public static void save() {
        getCanvas().save();
    }

    public static void restore() {
        getCanvas().restore();
    }

    public static void scale(float scale) {
        getCanvas().scale(scale, scale);
    }

    public static void scale(float x, float y, float scale) {
        getCanvas().translate(x, y);
        getCanvas().scale(scale, scale);
        getCanvas().translate(-x, -y);
    }

    public static void scale(float x, float y, float width, float height, float scale) {
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        getCanvas().translate(centerX, centerY);
        getCanvas().scale(scale, scale);
        getCanvas().translate(-centerX, -centerY);
    }

    public static void translate(float x, float y) {
        getCanvas().translate(x, y);
    }

    public static void rotate(float x, float y, float width, float height, float rotate) {
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        getCanvas().translate(centerX, centerY);
        getCanvas().rotate(rotate);
        getCanvas().translate(-centerX, -centerY);
    }

    public static void setAlpha(int alpha) {
        Paint paint = scratchPaint();
        paint.setAlpha(alpha);
        getCanvas().saveLayer(null, paint);
    }

    public static Canvas getCanvas() {
        return SkiaRenderManager.get().getCanvas();
    }

    public static io.github.humbleui.skija.Surface getSurface() {
        return SkiaRenderManager.get().getSurface();
    }

    public static io.github.humbleui.skija.Surface getBackdropSurface() {
        return SkiaRenderManager.get().getBackdropSurface();
    }

    public static void drawLeftRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        drawRoundedRectVarying(x, y, width, height, radius, 0.0F, 0.0F, radius, color);
    }

    public static void drawVerticalGradientRail(float x, float y, float width, float height,
                                                int startColor, int midColor, int endColor,
                                                float animationProgress) {
        float gradientStart = y + height * animationProgress * 0.5F;
        float gradientEnd = gradientStart + height * 1.5F;
        Paint paint = scratchPaint();
        try (Shader shader = Shader.makeLinearGradient(new Point(x + width / 2.0F, gradientStart),
                new Point(x + width / 2.0F, gradientEnd),
                new int[]{startColor, midColor, endColor},
                new float[]{0.0F, 0.5F, 1.0F})) {
            paint.setShader(shader);
            getCanvas().drawRect(Rect.makeXYWH(x, y, width, height), paint);
        }
    }

    public static void drawRailGuardLines(float x, float y, float width, float height, Color guardColor) {
        drawRect(x - 1.0F, y, 1.0F, height, guardColor);
        drawRect(x + width, y, 1.0F, height, guardColor);
    }

    public static Shader createWebStyleTextGradient(float textX, float textY, float textWidth, float textHeight,
                                                    int moduleIndex) {
        int[] colors = {0xFF8E55E9, 0xFFCFB2FF, 0xFFB77BFF, 0xFFCFB2FF, 0xFF8E55E9};
        float staggerDelay = moduleIndex * 100.0F;
        float animationProgress = (float) (((System.currentTimeMillis() + staggerDelay) % 2400) / 2400.0D);
        float gradientStart = textX + textWidth * (animationProgress - 0.5F);
        float gradientEnd = gradientStart + textWidth * 2.0F;
        return Shader.makeLinearGradient(new Point(gradientStart, textY + textHeight / 2.0F),
                new Point(gradientEnd, textY + textHeight / 2.0F), colors,
                new float[]{0.0F, 0.25F, 0.5F, 0.75F, 1.0F});
    }

    public static void drawWebStyleTextGlow(String text, float textX, float textY, Font font,
                                            Shader colorShader, float glowSize, float glowIntensity) {
        Paint glow1 = scratchPaint();
        glow1.setShader(colorShader);
        glow1.setAlphaf(glowIntensity * 0.35F);
        glow1.setMaskFilter(FilterCache.blur(FilterBlurMode.NORMAL, glowSize * 0.5F));
        drawText(text, textX, textY, font, glow1);

        Paint glow2 = scratchPaint();
        glow2.setColor(0xFFBA55FF);
        glow2.setAlphaf(0.3F);
        glow2.setMaskFilter(FilterCache.blur(FilterBlurMode.NORMAL, glowSize));
        drawText(text, textX, textY, font, glow2);

        Paint glow3 = scratchPaint();
        glow3.setColor(0xFFBA55FF);
        glow3.setAlphaf(0.1F);
        glow3.setMaskFilter(FilterCache.blur(FilterBlurMode.NORMAL, glowSize * 1.5F));
        drawText(text, textX, textY, font, glow3);
    }

    public static void drawWebStyleArrayListRail(float x, float y, float width, float height) {
        float animationProgress = (float) ((System.currentTimeMillis() % 3200) / 3200.0D);
        drawVerticalGradientRail(x, y, width, height, 0xFF6E2FA8, 0xFFE7BFFF, 0xFF6E2FA8, animationProgress);
        drawRailGuardLines(x, y, width, height, new Color(83, 33, 101, 115));
    }
}
