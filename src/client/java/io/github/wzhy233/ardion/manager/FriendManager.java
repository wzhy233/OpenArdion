package io.github.wzhy233.ardion.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wzhy233.ardion.util.client.ArdionHome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class FriendManager {
    private static final Path file = ArdionHome.root().resolve("friends.json");
    private static Map<UUID, String> friends = new LinkedHashMap<>();
    private static boolean loaded;
    private static IOException loadError;

    private FriendManager() {
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            Map<UUID, String> saved = new LinkedHashMap<>();
            for (var entry : json.entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IOException("Invalid friend name");
                }
                saved.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
            }
            friends = saved;
        } catch (IOException | RuntimeException exception) {
            loadError = new IOException("Could not load " + file + "; existing file was preserved", exception);
            System.getLogger(FriendManager.class.getName()).log(System.Logger.Level.ERROR, loadError.getMessage(), exception);
        }
    }

    public static boolean isFriend(UUID uuid) {
        load();
        return friends.containsKey(uuid);
    }

    public static Map<UUID, String> list() {
        load();
        if (loadError != null) throw new UncheckedIOException(loadError);
        return Collections.unmodifiableMap(new LinkedHashMap<>(friends));
    }

    public static boolean toggle(UUID uuid, String name) throws IOException {
        if (isFriend(uuid)) {
            remove(uuid);
            return false;
        }
        add(uuid, name);
        return true;
    }

    public static void add(UUID uuid, String name) throws IOException {
        Objects.requireNonNull(uuid, "Friend UUID");
        Map<UUID, String> next = new LinkedHashMap<>(list());
        next.put(uuid, name == null || name.isBlank() ? uuid.toString() : name.strip());
        save(next);
    }

    public static boolean remove(UUID uuid) throws IOException {
        Map<UUID, String> next = new LinkedHashMap<>(list());
        if (next.remove(uuid) == null) return false;
        save(next);
        return true;
    }

    public static void clear() throws IOException {
        list();
        save(new LinkedHashMap<>());
    }

    private static void save(Map<UUID, String> next) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject json = new JsonObject();
        next.forEach((uuid, name) -> json.addProperty(uuid.toString(), name));
        Path temp = Files.createTempFile(file.getParent(), "friends-", ".tmp");
        try {
            Files.writeString(temp, json.toString());
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            friends = next;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
