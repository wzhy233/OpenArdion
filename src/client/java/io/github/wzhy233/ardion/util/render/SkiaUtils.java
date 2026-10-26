package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.Data;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class SkiaUtils {
    private SkiaUtils() {
    }

    public static Optional<byte[]> convertToBytes(String path) {
        try (InputStream inputStream = NativeSkiaLoader.openResource(path)) {
            return Optional.of(inputStream.readAllBytes());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public static Optional<Data> convertToData(String path) {
        return convertToBytes(path).map(Data::makeFromBytes);
    }
}
