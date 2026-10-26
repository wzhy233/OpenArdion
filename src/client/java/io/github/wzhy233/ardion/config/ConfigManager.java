package io.github.wzhy233.ardion.config;

import com.google.gson.*;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.value.base.Value;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ConfigManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Path dir = FabricLoader.getInstance().getConfigDir().resolve("ardion");
    private static final String auto = "default.json";

    public ConfigManager() {
    }

    public static void save(String name) throws IOException {
        write(getPath(name));
    }

    public static void load(String name) throws IOException {
        Path path = getPath(name);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Config not found: " + name);
        }

        apply(path);
    }

    public static void save() {
        try {
            write(getPath());
        } catch (IOException e) {
            OutputUtil.error("Failed to save: " + e.getMessage());
        }
    }

    public static void load() {
        try {
            Path path = getPath();
            if (!Files.exists(path)) {
                write(path);
            }
            apply(path);
        } catch (IOException | IllegalArgumentException e) {
            OutputUtil.error("Failed to load: " + e.getMessage());
        }
    }

    public static List<String> list() throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(auto))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> names.add(strip(path.getFileName().toString())));
        }
        return names;
    }

    public static String describeBind(Module module) {
        return module.hasBind() ? String.valueOf(module.getBindKey()) : String.valueOf(GLFW.GLFW_KEY_UNKNOWN);
    }

    private static void write(Path path) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(path, gson.toJson(json()));
    }

    private static void apply(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonObject settings = root.getAsJsonObject("settings");
        applySettings(settings);
        JsonObject modules = root.getAsJsonObject("modules");
        applyLegacyTargetSettings(settings, modules);
        apply(modules);
        JsonObject hud = root.getAsJsonObject("hud");
        if (hud != null) {
            HudConfig.apply(hud);
        } else {
            HudConfig.applyLegacy(modules);
        }
    }

    private static JsonObject json() {
        JsonObject root = new JsonObject();
        JsonObject settings = new JsonObject();
        for (Value<?> value : GlobalSetting.getConfigValues()) {
            settings.addProperty(value.getName(), value.getValueAsString());
        }
        root.add("settings", settings);

        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.getModules()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            if (module.hasBind()) {
                entry.addProperty("bind", module.getBindKey());
            }

            JsonObject values = new JsonObject();
            for (Value<?> value : module.getValues()) {
                values.addProperty(value.getName(), value.getValueAsString());
            }
            entry.add("values", values);
            modules.add(module.getName(), entry);
        }
        root.add("modules", modules);
        root.add("hud", HudConfig.json());
        return root;
    }

    private static void applySettings(JsonObject settings) {
        if (settings == null) {
            return;
        }

        for (String valueName : settings.keySet()) {
            Value<?> value = GlobalSetting.getValue(valueName);
            if (value != null) {
                value.setValueFromString(settings.get(valueName).getAsString());
            }
        }
    }

    private static void applyLegacyTargetSettings(JsonObject settings, JsonObject modules) {
        if (modules == null || !modules.has("trigger-bot")) {
            return;
        }

        JsonObject triggerBot = modules.getAsJsonObject("trigger-bot");
        JsonObject values = triggerBot == null ? null : triggerBot.getAsJsonObject("values");
        if (values == null) {
            return;
        }

        applyLegacySetting(settings, values, "team-check", "team-check");
        applyLegacySetting(settings, values, "crystal", "crystals");
        applyLegacySetting(settings, values, "villager", "villagers");
    }

    private static void applyLegacySetting(JsonObject settings, JsonObject legacyValues, String settingName, String legacyName) {
        if (settings != null && settings.has(settingName)) {
            return;
        }
        if (!legacyValues.has(legacyName)) {
            return;
        }

        Value<?> value = GlobalSetting.getValue(settingName);
        if (value != null) {
            value.setValueFromString(legacyValues.get(legacyName).getAsString());
        }
    }

    private static void apply(JsonObject modules) {
        if (modules == null) {
            return;
        }

        for (String name : modules.keySet()) {
            Module module = ModuleManager.getModule(name);
            if (module == null) {
                continue;
            }

            JsonObject entry = modules.getAsJsonObject(name);
            JsonElement bind = entry.get("bind");
            if (bind == null || bind.isJsonNull()) {
                module.clearBindKey();
            } else {
                module.setBindKey(bind.getAsInt());
            }

            JsonObject values = entry.getAsJsonObject("values");
            if (values != null) {
                for (String valueName : values.keySet()) {
                    Value<?> value = module.getValue(valueName);
                    if (value != null) {
                        value.setValueFromString(values.get(valueName).getAsString());
                    }
                }
            }

            JsonElement enabled = entry.get("enabled");
            if (enabled != null && !enabled.isJsonNull()) {
                module.setEnabledSilently(enabled.getAsBoolean());
            }
        }
    }

    private static Path getPath(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Config name cannot be empty.");
        }
        if (auto.equalsIgnoreCase(name) || "default".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("Config name 'default' is reserved.");
        }
        return dir.resolve(name + ".json");
    }

    private static Path getPath() {
        return dir.resolve(auto);
    }

    private static String strip(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}
