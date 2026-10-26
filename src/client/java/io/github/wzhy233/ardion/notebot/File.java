package io.github.wzhy233.ardion.notebot;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class File {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".nbs", ".txt", ".mid", ".midi");

    private File() {
    }

    public static Path getBaseDir() {
        return FabricLoader.getInstance().getGameDir().resolve("ardion").resolve("notebot");
    }

    public static Path getSongsDir() {
        return getBaseDir().resolve("songs");
    }

    public static void ensureDirectories() throws IOException {
        java.nio.file.Files.createDirectories(getSongsDir());
    }

    public static List<String> listSongs() throws IOException {
        ensureDirectories();
        try (Stream<Path> stream = java.nio.file.Files.list(getSongsDir())) {
            return stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(File::isSupportedSong)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    public static Path resolveSong(String filename) {
        return getSongsDir().resolve(filename).normalize();
    }

    public static boolean isSupportedSong(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        for (String extension : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
