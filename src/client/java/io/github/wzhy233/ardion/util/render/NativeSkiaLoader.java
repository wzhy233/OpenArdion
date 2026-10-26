package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.impl.Library;
import io.github.wzhy233.ardion.util.client.ArdionHome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

public final class NativeSkiaLoader {
    private static boolean loaded;
    private static String failureReason = "Skija not initialized";

    private NativeSkiaLoader() {
    }

    public static synchronized boolean ensureLoaded() {
        if (loaded) return true;
        try {
            Path nativeLibrary = extractNative();
            Library._loadFromFile(nativeLibrary.toFile());
            loaded = true;
            failureReason = "";
            return true;
        } catch (Throwable throwable) {
            loaded = false;
            failureReason = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            return false;
        }
    }

    public static String failureReason() {
        return failureReason;
    }

    private static Path extractNative() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean android = AndroidGlBridge.isAndroidRuntime();
        String resource = android ? "skija/android-arm64/libskija.so"
                : os.contains("win") ? "skija/windows/libskija.dll" : "skija/linux/libskija.so";
        Path dir = ArdionHome.root().resolve("skija");
        Files.createDirectories(dir);
        if (os.contains("win") && !android) {
            extractResource("skija/windows/icudtl.dat", dir.resolve("icudtl.dat"));
        }
        Path target = dir.resolve(os.contains("win") && !android ? "libskija.dll" : "libskija.so");
        extractResource(resource, target);
        return target;
    }

    private static void extractResource(String resource, Path target) throws IOException {
        try (InputStream input = openResource(resource)) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static InputStream openResource(String path) throws IOException {
        InputStream stream = NativeSkiaLoader.class.getClassLoader().getResourceAsStream("assets/ardion/" + path);
        if (stream != null) return stream;
        Identifier id = Identifier.of("ardion", path.toLowerCase(Locale.ROOT));
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) return resource.get().getInputStream();
        throw new IOException("Missing resource: " + id);
    }
}
