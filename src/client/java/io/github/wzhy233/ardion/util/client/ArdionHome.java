package io.github.wzhy233.ardion.util.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArdionHome {
    private static final Path ROOT = Path.of(System.getProperty("user.home"), ".ardion");

    private ArdionHome() {
    }

    public static Path root() {
        return ROOT;
    }

    public static Path musicPlayer() {
        return ROOT.resolve("music-player");
    }

    public static void migrateIfMissing(Path oldFile, Path newFile) throws IOException {
        if (Files.exists(newFile) || !Files.exists(oldFile)) return;
        Files.createDirectories(newFile.getParent());
        Files.copy(oldFile, newFile);
    }
}
