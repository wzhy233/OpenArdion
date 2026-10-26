package io.github.humbleui.skija.impl;

import io.github.wzhy233.ardion.util.render.NativeSkiaLoader;

import java.io.File;

public class Library {
    public static volatile boolean _loaded;
    public static final String LIBRARY_NAME = "skija";

    public static void staticLoad() {
        if (!_loaded && !"false".equals(System.getProperty("skija.staticLoad"))) load();
    }

    public static void load() {
        if (!_loaded && !NativeSkiaLoader.ensureLoaded()) {
            throw new UnsatisfiedLinkError(NativeSkiaLoader.failureReason());
        }
    }

    public static void _loadFromFile(File file) {
        System.load(file.getAbsolutePath());
        _loaded = true;
        _nAfterLoad();
    }

    public static native void _nAfterLoad();
}
