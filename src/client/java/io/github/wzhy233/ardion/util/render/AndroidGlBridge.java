package io.github.wzhy233.ardion.util.render;

import io.github.wzhy233.loader.NativeLoader;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AndroidGlBridge {
    private static boolean loaded;

    private AndroidGlBridge() {
    }

    public static boolean isAndroidRuntime() {
        String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
        String vendor = System.getProperty("java.vm.vendor", "").toLowerCase(Locale.ROOT);
        return vm.contains("dalvik") || runtime.contains("android") || vendor.contains("android")
                || Files.exists(Path.of("/system/build.prop"));
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        NativeLoader.loadAndroidBridge();
        loaded = true;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static native boolean androidGlInitShared();

    public static native boolean androidGlMakeSharedCurrent();

    public static native boolean androidGlRestoreCurrent();

    public static native int androidGlCreateOffscreen(int width, int height);

    public static native int androidGlGetOffscreenFbo();

    public static native void androidGlDestroyOffscreen();

    public static native boolean androidGlUploadColor(ByteBuffer pixels, int width, int height);

    public static native boolean androidGlReadColor(ByteBuffer pixels, int width, int height);

    public static native void androidGlDestroyShared();
}
