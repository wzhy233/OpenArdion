package io.github.wzhy233.ardion.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wzhy233.ardion.util.client.ArdionHome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

final class AgentModels {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OPENAI = "@opencode/ai/providers/openai-compatible";
    private static Path directory = ArdionHome.root().resolve("agent").resolve("config");
    private static volatile String readError = "";
    private static volatile String saveError = "";
    private static volatile long saveId;

    private AgentModels() {
    }

    static void use(Path configDirectory) {
        directory = configDirectory;
        readError = "";
        saveError = "";
        saveId = 0;
    }

    static String error() {
        return saveError.isEmpty() ? readError : saveError;
    }

    static long saveId() {
        return saveId;
    }

    static JsonObject view() {
        try {
            readError = "";
            return project(readRoot());
        } catch (RuntimeException exception) {
            readError = "Could not read ardion.json";
            JsonObject empty = new JsonObject();
            empty.addProperty("model", "");
            empty.add("providers", new JsonArray());
            return empty;
        }
    }

    static void save(JsonObject message) {
        saveId = message.has("save") && message.get("save").isJsonPrimitive() ? message.get("save").getAsLong() : 0;
        Path target = store();
        final JsonObject root;
        try {
            root = readRoot();
        } catch (RuntimeException exception) {
            saveError = "Could not read ardion.json";
            return;
        }
        try {
            apply(root, message);
            write(target, root);
            saveError = "";
        } catch (IllegalArgumentException exception) {
            saveError = exception.getMessage();
        } catch (RuntimeException | IOException exception) {
            saveError = "Could not write ardion.json";
        }
    }

    private static Path store() {
        Path json = directory.resolve("ardion.json");
        if (Files.isRegularFile(json)) return json;
        Path jsonc = directory.resolve("ardion.jsonc");
        if (Files.isRegularFile(jsonc)) return jsonc;
        return json;
    }

    private static JsonObject readRoot() {
        Path json = directory.resolve("ardion.json");
        Path jsonc = directory.resolve("ardion.jsonc");
        Path legacy = directory.resolve("ardion.config");
        Path source = Files.isRegularFile(json) ? json : Files.isRegularFile(jsonc) ? jsonc : Files.isRegularFile(legacy) ? legacy : null;
        if (source == null) return new JsonObject();
        try {
            String text = Files.readString(source);
            if (text.isBlank()) return new JsonObject();
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) throw new IllegalStateException("ardion.json is not an object");
            return parsed.getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void apply(JsonObject root, JsonObject message) {
        if (!message.has("providers") || !message.get("providers").isJsonArray()) throw new IllegalArgumentException("Providers are missing");
        JsonObject existing = object(root.get("providers"));
        JsonObject next = new JsonObject();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : message.getAsJsonArray("providers")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Provider is invalid");
            JsonObject item = element.getAsJsonObject();
            String id = text(item, "id").trim();
            if (id.isEmpty() || id.indexOf('/') >= 0 || id.indexOf('#') >= 0) throw new IllegalArgumentException("Provider id is invalid");
            if (!seen.add(id)) throw new IllegalArgumentException("Duplicate provider id");
            JsonObject provider = copyProvider(existing, text(item, "key").trim());
            String name = text(item, "name").trim();
            if (name.isEmpty()) provider.remove("name");
            else provider.addProperty("name", name);
            if (!provider.has("package")) provider.addProperty("package", OPENAI);
            JsonObject settings = object(provider.get("settings"));
            if (settings == null) {
                settings = new JsonObject();
                provider.add("settings", settings);
            }
            settings.addProperty("baseURL", text(item, "baseURL").trim());
            String apiKey = text(item, "apiKey").trim();
            if (apiKey.isEmpty()) settings.remove("apiKey");
            else settings.addProperty("apiKey", apiKey);
            provider.add("models", models(provider.get("models"), item.get("models")));
            next.add(id, provider);
        }
        if (existing != null) {
            for (var entry : existing.entrySet()) {
                if (!entry.getValue().isJsonObject() && !next.has(entry.getKey())) next.add(entry.getKey(), entry.getValue());
            }
        }
        root.add("providers", next);
        String model = text(message, "model").trim();
        if (model.isEmpty()) root.remove("model");
        else {
            int slash = model.indexOf('/');
            if (slash <= 0 || slash == model.length() - 1 || model.indexOf('#') >= 0) throw new IllegalArgumentException("Default model is invalid");
            root.addProperty("model", model);
        }
    }

    private static JsonObject models(JsonElement existingElement, JsonElement incomingElement) {
        if (incomingElement == null || !incomingElement.isJsonArray()) throw new IllegalArgumentException("Models are missing");
        JsonObject existing = object(existingElement);
        JsonObject next = new JsonObject();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : incomingElement.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Model is invalid");
            JsonObject item = element.getAsJsonObject();
            String id = text(item, "id").trim();
            String name = text(item, "name").trim();
            if (id.isEmpty() && name.isEmpty()) continue;
            if (id.isEmpty() || id.indexOf('#') >= 0) throw new IllegalArgumentException("Model id is invalid");
            if (!seen.add(id)) throw new IllegalArgumentException("Duplicate model id");
            JsonObject model = existing != null && existing.has(id) && existing.get(id).isJsonObject()
                    ? copy(existing.getAsJsonObject(id)) : new JsonObject();
            if (name.isEmpty()) model.remove("name");
            else model.addProperty("name", name);
            next.add(id, model);
        }
        if (existing != null) {
            for (var entry : existing.entrySet()) {
                if (!entry.getValue().isJsonObject() && !next.has(entry.getKey())) next.add(entry.getKey(), entry.getValue());
            }
        }
        return next;
    }

    private static JsonObject copyProvider(JsonObject existing, String key) {
        if (key.isEmpty() || existing == null || !existing.has(key) || !existing.get(key).isJsonObject()) return new JsonObject();
        return copy(existing.getAsJsonObject(key));
    }

    private static JsonObject copy(JsonObject object) {
        return JsonParser.parseString(object.toString()).getAsJsonObject();
    }

    private static JsonObject project(JsonObject root) {
        JsonObject view = new JsonObject();
        view.addProperty("model", text(root, "model"));
        JsonArray providers = new JsonArray();
        JsonObject map = object(root.get("providers"));
        if (map != null) {
            for (var entry : map.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject provider = entry.getValue().getAsJsonObject();
                JsonObject item = new JsonObject();
                item.addProperty("id", entry.getKey());
                item.addProperty("name", text(provider, "name"));
                JsonObject settings = object(provider.get("settings"));
                item.addProperty("baseURL", settings == null ? "" : text(settings, "baseURL"));
                item.addProperty("apiKey", settings == null ? "" : text(settings, "apiKey"));
                JsonArray models = new JsonArray();
                JsonObject modelMap = object(provider.get("models"));
                if (modelMap != null) {
                    for (var model : modelMap.entrySet()) {
                        if (!model.getValue().isJsonObject()) continue;
                        JsonObject row = new JsonObject();
                        row.addProperty("id", model.getKey());
                        row.addProperty("name", text(model.getValue().getAsJsonObject(), "name"));
                        models.add(row);
                    }
                }
                item.add("models", models);
                providers.add(item);
            }
        }
        view.add("providers", providers);
        return view;
    }

    private static void write(Path target, JsonObject root) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "ardion", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }
}
