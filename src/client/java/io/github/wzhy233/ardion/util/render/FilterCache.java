package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.MaskFilter;

import java.util.HashMap;
import java.util.Map;

public final class FilterCache {
    private static final int MAX_MASK_FILTERS = 32;
    private static final int MAX_IMAGE_FILTERS = 64;
    private static final Map<String, MaskFilter> MASK = new HashMap<>();
    private static final Map<String, ImageFilter> IMAGE = new HashMap<>();

    private FilterCache() {
    }

    public static MaskFilter blur(FilterBlurMode mode, float sigma) {
        String key = mode.name() + "@" + sigma;
        MaskFilter cached = MASK.get(key);
        if (cached != null) return cached;
        MaskFilter created = MaskFilter.makeBlur(mode, sigma);
        trimMaskCache();
        MASK.put(key, created);
        return created;
    }

    public static ImageFilter dropShadowOnly(float dx, float dy, float sigmaX, float sigmaY, int color) {
        String key = "dso:" + dx + ":" + dy + ":" + sigmaX + ":" + sigmaY + ":" + color;
        ImageFilter cached = IMAGE.get(key);
        if (cached != null) return cached;
        ImageFilter created = ImageFilter.makeDropShadowOnly(dx, dy, sigmaX, sigmaY, color);
        trimImageCache();
        IMAGE.put(key, created);
        return created;
    }

    public static ImageFilter imageBlur(float sigmaX, float sigmaY, FilterTileMode tile) {
        String key = "blur:" + sigmaX + ":" + sigmaY + ":" + tile.name();
        ImageFilter cached = IMAGE.get(key);
        if (cached != null) return cached;
        ImageFilter created = ImageFilter.makeBlur(sigmaX, sigmaY, tile);
        trimImageCache();
        IMAGE.put(key, created);
        return created;
    }

    public static void clear() {
        MASK.values().forEach(MaskFilter::close);
        MASK.clear();
        IMAGE.values().forEach(ImageFilter::close);
        IMAGE.clear();
    }

    private static void trimMaskCache() {
        if (MASK.size() < MAX_MASK_FILTERS) return;
        String first = MASK.keySet().iterator().next();
        MaskFilter removed = MASK.remove(first);
        if (removed != null) removed.close();
    }

    private static void trimImageCache() {
        if (IMAGE.size() < MAX_IMAGE_FILTERS) return;
        String first = IMAGE.keySet().iterator().next();
        ImageFilter removed = IMAGE.remove(first);
        if (removed != null) removed.close();
    }
}
