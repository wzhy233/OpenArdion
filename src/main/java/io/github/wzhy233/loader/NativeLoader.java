package io.github.wzhy233.loader;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class NativeLoader {
    private static boolean loaded;

    private NativeLoader() {
    }

    public static synchronized void loadAndroidBridge() {
        if (!loaded) {
            loadPlatformLibrary();
        }
    }

    private static void loadPlatformLibrary() {
        try {
            Path library = locateLibrary();
            System.load(library.toAbsolutePath().toString());
            loaded = true;
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to load natives: " + t.getMessage(), t);
        }
    }

    private static Path locateLibrary() throws IOException {
        String override = System.getProperty("ardion.native.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("ARDION_NATIVE_PATH");
        }
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);
            if (Files.isRegularFile(path)) {
                return path;
            }
            throw new IOException("native override does not exist: " + path);
        }

        String resourceName = platformResourceName();
        Path extracted = extractResource(resourceName);
        if (extracted != null) {
            return extracted;
        }

        throw new IOException("native library not found for " + resourceName);
    }

    private static Path extractResource(String resourceName) throws IOException {
        try (InputStream in = openNativeResource(resourceName)) {
            if (in == null) {
                return null;
            }

            Path dir = FabricLoader.getInstance().getConfigDir().resolve("ardion").resolve("native");
            Files.createDirectories(dir);
            Path out = dir.resolve(resourceName);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        }
    }

    private static InputStream openNativeResource(String resourceName) throws IOException {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("ardion");
        if (container.isPresent()) {
            Optional<Path> path = container.get().findPath("assets/ardion/native/" + resourceName);
            if (path.isPresent()) {
                return Files.newInputStream(path.get());
            }
        }

        return NativeLoader.class.getResourceAsStream("/assets/ardion/native/" + resourceName);
    }

    private static String platformResourceName() {
        return "libardion_android.so";
    }
}
