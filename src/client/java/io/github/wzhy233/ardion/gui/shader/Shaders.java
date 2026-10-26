package io.github.wzhy233.ardion.gui.shader;

import io.github.humbleui.skija.RuntimeEffect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class Shaders {
    public static final String GAUSSIAN_BLUR = "gaussian_blur";
    public static final String THRESHOLD = "threshold";
    public static final String BLOOM = "bloom";

    private static final ConcurrentHashMap<String, RuntimeEffect> CACHE = new ConcurrentHashMap<>();

    private Shaders() {
    }

    public static RuntimeShader shader(String name) {
        return new RuntimeShader(CACHE.computeIfAbsent(name, Shaders::compile));
    }

    public static void clearCache() {
        CACHE.values().forEach(RuntimeEffect::close);
        CACHE.clear();
    }

    private static RuntimeEffect compile(String name) {
        String path = "assets/ardion/shaders/" + name + ".sksl";
        try (InputStream input = Shaders.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing shader resource: " + path);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return RuntimeEffect.makeForShader(source);
        } catch (IOException exception) {
            throw new IllegalArgumentException("SKSL shader not found: " + name, exception);
        }
    }
}
