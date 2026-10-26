package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.impl.Managed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class FontHelper {
    private static final Map<String, Typeface> TYPEFACES = new HashMap<>();
    private static final Map<String, Font> FONTS = new HashMap<>();

    private FontHelper() {
    }

    public static Font load(String font, float size) {
        float actualSize = size * 0.5F;
        String key = font + "@" + actualSize;
        Font cached = FONTS.get(key);
        if (cached != null) return cached;
        Font created = new Font(typeface(font), actualSize);
        created.setSubpixel(true);
        created.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
        FONTS.put(key, created);
        return created;
    }

    public static void preloadFonts(String... fonts) {
        for (String font : fonts) {
            typeface(font);
        }
    }

    public static void clearCache() {
        FONTS.values().forEach(Managed::close);
        FONTS.clear();
        TYPEFACES.values().forEach(Managed::close);
        TYPEFACES.clear();
    }

    private static Typeface typeface(String font) {
        return TYPEFACES.computeIfAbsent(font, FontHelper::loadTypeface);
    }

    private static Typeface loadTypeface(String font) {
        try (InputStream input = openResource("fonts/" + font);
             Data data = Data.makeFromBytes(input.readAllBytes())) {
            Typeface typeface = FontMgr.getDefault().makeFromData(data);
            if (typeface == null) throw new IOException("Skija FontMgr returned null for " + font);
            return typeface;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Font not found: " + font, exception);
        }
    }

    private static InputStream openResource(String path) throws IOException {
        InputStream stream = FontHelper.class.getClassLoader().getResourceAsStream("assets/ardion/" + path);
        if (stream != null) return stream;
        Identifier id = Identifier.of("ardion", path.toLowerCase(java.util.Locale.ROOT));
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) return resource.get().getInputStream();
        throw new IOException("Missing resource: " + id);
    }
}
