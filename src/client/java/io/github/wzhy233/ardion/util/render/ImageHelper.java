package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.Image;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ImageHelper {
    private static final int MAX_IMAGES = 128;
    private final Map<String, Image> images = new HashMap<>();

    public boolean load(String filePath) {
        if (!images.containsKey(filePath)) {
            Optional<byte[]> encodedBytes = SkiaUtils.convertToBytes(filePath);
            if (encodedBytes.isEmpty()) return false;
            trim();
            images.put(filePath, Image.makeDeferredFromEncodedBytes(encodedBytes.get()));
        }
        return true;
    }

    public boolean load(File file) {
        if (!images.containsKey(file.getName())) {
            try (FileInputStream input = new FileInputStream(file)) {
                trim();
                images.put(file.getName(), Image.makeDeferredFromEncodedBytes(input.readAllBytes()));
            } catch (IOException exception) {
                return false;
            }
        }
        return true;
    }

    public Image get(String path) {
        return images.get(path);
    }

    public void clear() {
        images.values().forEach(Image::close);
        images.clear();
    }

    private void trim() {
        if (images.size() < MAX_IMAGES) return;
        String first = images.keySet().iterator().next();
        Image removed = images.remove(first);
        if (removed != null) removed.close();
    }
}
