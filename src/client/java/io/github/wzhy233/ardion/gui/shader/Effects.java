package io.github.wzhy233.ardion.gui.shader;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.util.render.FilterCache;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;

public final class Effects {
    private Effects() {
    }

    public static void blurBackdrop(Canvas canvas, Rect bounds, float sigma) {
        Surface backdrop = SkiaRenderManager.get().getBackdropSurface();
        if (backdrop != null && blurBackdropSnapshot(canvas, backdrop, bounds, sigma)) return;
        ImageFilter blur = FilterCache.imageBlur(sigma, sigma, FilterTileMode.CLAMP);
        canvas.saveLayer(new SaveLayerRec(bounds, null, blur));
        canvas.restore();
    }

    public static void blurBackdropSKSL(Canvas canvas, Surface surface, Rect bounds, float radius) {
        blurBackdropSKSL(canvas, surface, bounds, radius, "gaussian-blur");
    }

    public static void blurBackdropSKSL(Canvas canvas, Surface surface, Rect bounds, float radius, String shaderName) {
        Surface backdrop = SkiaRenderManager.get().getBackdropSurface();
        if (backdrop != null) surface = backdrop;
        if (surface == null) return;
        float scale = SkiaRenderManager.get().getScaleFactor();
        int dx = Math.round(bounds.getLeft() * scale);
        int dy = Math.round(bounds.getTop() * scale);
        int dw = Math.round(bounds.getWidth() * scale);
        int dh = Math.round(bounds.getHeight() * scale);
        if (dw <= 0 || dh <= 0) return;
        IRect deviceRect = IRect.makeXYWH(dx, dy, dw, dh);
        try (Image snapshot = surface.makeImageSnapshot(deviceRect)) {
            if (snapshot == null) return;
            float w = bounds.getWidth();
            float h = bounds.getHeight();
            Matrix33 localMatrix = Matrix33.makeScale(1.0F / scale, 1.0F / scale);
            try (Shader src = snapshot.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, localMatrix);
                 Shader blurred = skslEffect(src, w, h, radius, shaderName);
                 Paint paint = new Paint()) {
                paint.setShader(blurred);
                canvas.save();
                canvas.translate(bounds.getLeft(), bounds.getTop());
                canvas.drawRect(Rect.makeWH(w, h), paint);
                canvas.restore();
            }
        }
    }

    private static boolean blurBackdropSnapshot(Canvas canvas, Surface backdrop, Rect bounds, float sigma) {
        float scale = SkiaRenderManager.get().getScaleFactor();
        int dx = Math.round(bounds.getLeft() * scale);
        int dy = Math.round(bounds.getTop() * scale);
        int dw = Math.round(bounds.getWidth() * scale);
        int dh = Math.round(bounds.getHeight() * scale);
        if (dw <= 0 || dh <= 0) return false;
        IRect deviceRect = IRect.makeXYWH(dx, dy, dw, dh);
        try (Image snapshot = backdrop.makeImageSnapshot(deviceRect)) {
            if (snapshot == null) return false;
            ImageFilter blur = FilterCache.imageBlur(sigma, sigma, FilterTileMode.CLAMP);
            try (Paint paint = new Paint()) {
                paint.setImageFilter(blur);
                canvas.save();
                canvas.translate(bounds.getLeft(), bounds.getTop());
                canvas.scale(1.0F / scale, 1.0F / scale);
                canvas.drawImage(snapshot, 0.0F, 0.0F, paint);
                canvas.restore();
            }
            return true;
        }
    }

    private static Shader skslEffect(Shader source, float width, float height, float radius, String shaderName) {
        return switch (shaderName) {
            case "bloom" -> bloom(source, width, height, radius);
            case "threshold" -> threshold(source, 0.55F, 0.18F);
            default -> gaussianBlur(source, width, height, radius);
        };
    }

    private static Shader threshold(Shader source, float threshold, float knee) {
        return Shaders.shader(Shaders.THRESHOLD)
                .child("src", source)
                .uniform("threshold", threshold)
                .uniform("knee", knee)
                .make();
    }

    private static Shader bloom(Shader source, float width, float height, float radius) {
        try (Shader bright = threshold(source, 0.55F, 0.20F);
             Shader glow = gaussianBlur(bright, width, height, radius)) {
            return Shaders.shader(Shaders.BLOOM)
                    .child("src", source)
                    .child("glow", glow)
                    .uniform("intensity", 1.15F)
                    .make();
        }
    }

    public static Shader gaussianBlur(Shader source, float width, float height, float radius) {
        try (Shader horizontal = Shaders.shader(Shaders.GAUSSIAN_BLUR)
                .child("src", source)
                .uniform("direction", 1.0F, 0.0F)
                .uniform("radius", radius)
                .make()) {
            return Shaders.shader(Shaders.GAUSSIAN_BLUR)
                    .child("src", horizontal)
                    .uniform("direction", 0.0F, 1.0F)
                    .uniform("radius", radius)
                    .make();
        }
    }
}
