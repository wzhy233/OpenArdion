package io.github.wzhy233.ardion.music;

import io.github.humbleui.skija.Image;
import io.github.wzhy233.ardion.util.render.NativeSkiaLoader;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class MusicArtworkCache {
    private static final int MAX_IMAGES = 48;
    private static final int MAX_PENDING = 8;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final LinkedHashMap<String, Image> IMAGES = new LinkedHashMap<>(64, .75F, true);
    private static final LinkedHashMap<String, Long> RETRY = new LinkedHashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final ExecutorService DOWNLOADS = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "Ardion-Music-Artwork");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean closed;

    private MusicArtworkCache() {}

    public static Image get(String url) {
        return load(url, null);
    }

    public static Image load(String key, byte[] encoded) {
        if (closed || key == null || key.isBlank()) return null;
        Image cached = IMAGES.get(key);
        if (cached != null) return cached;
        if (PENDING.size() >= MAX_PENDING || PENDING.contains(key)
                || RETRY.getOrDefault(key, 0L) > System.currentTimeMillis()) return null;
        PENDING.add(key);
        DOWNLOADS.execute(() -> {
            byte[] result = null;
            try {
                result = thumbnail(encoded == null ? download(key) : encoded);
            } catch (IOException | RuntimeException ignored) {
            }
            byte[] ready = result;
            MinecraftClient.getInstance().execute(() -> {
                try {
                    if (closed) return;
                    if (ready == null || !NativeSkiaLoader.ensureLoaded()) {
                        failed(key);
                        return;
                    }
                    Image image = Image.makeDeferredFromEncodedBytes(ready);
                    Image old = IMAGES.put(key, image);
                    if (old != null) old.close();
                    while (IMAGES.size() > MAX_IMAGES) {
                        var iterator = IMAGES.entrySet().iterator();
                        Image evicted = iterator.next().getValue();
                        iterator.remove();
                        evicted.close();
                    }
                    RETRY.remove(key);
                } catch (RuntimeException | LinkageError ignored) {
                    failed(key);
                } finally {
                    PENDING.remove(key);
                }
            });
        });
        return null;
    }

    private static void failed(String key) {
        RETRY.put(key, System.currentTimeMillis() + 30_000L);
        while (RETRY.size() > 128) RETRY.remove(RETRY.keySet().iterator().next());
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(6_000);
        connection.setReadTimeout(10_000);
        try (InputStream input = connection.getInputStream()) {
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Artwork too large");
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Artwork too large");
            return bytes;
        } finally {
            connection.disconnect();
        }
    }

    static byte[] thumbnail(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Artwork too large");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported artwork");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000L)
                    throw new IOException("Artwork dimensions too large");
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + 511) / 512);
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage image = reader.read(0, params);
                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(image, "png", output)) throw new IOException("Cannot encode artwork");
                    return output.toByteArray();
                } finally {
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    public static void shutdown() {
        closed = true;
        DOWNLOADS.shutdownNow();
        IMAGES.values().forEach(Image::close);
        IMAGES.clear();
        RETRY.clear();
        PENDING.clear();
    }
}
