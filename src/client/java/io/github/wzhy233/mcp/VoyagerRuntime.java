package io.github.wzhy233.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

final class VoyagerRuntime {
    static synchronized Path install(Path directory) throws IOException {
        String base = "assets/ardion/native/voyager-runtime.zip";
        var loader = VoyagerRuntime.class.getClassLoader();
        String hash;
        try (var in = loader.getResourceAsStream(base + ".sha256")) {
            if (in == null) throw new IOException("Voyager runtime is missing; rebuild the Ardion JAR");
            hash = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        }
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid Voyager checksum");
        Path target = directory.resolve("voyager-" + hash.substring(0, 16));
        if (Files.exists(target.resolve(".ready"))) return target;
        Files.createDirectories(target);
        try (var in = loader.getResourceAsStream(base)) {
            if (in == null) throw new IOException("Voyager archive is missing");
            byte[] bytes = in.readAllBytes();
            try {
                if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(hash)) throw new IOException("Voyager archive checksum mismatch");
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path file = target.resolve(entry.getName()).normalize();
                    if (!file.startsWith(target)) throw new IOException("Invalid Voyager archive path");
                    if (entry.isDirectory()) Files.createDirectories(file);
                    else {
                        Files.createDirectories(file.getParent());
                        Files.copy(zip, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        Files.writeString(target.resolve(".ready"), hash);
        return target;
    }
}
