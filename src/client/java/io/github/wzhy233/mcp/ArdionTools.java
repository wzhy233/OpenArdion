package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.config.HudConfig;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import java.util.List;

final class ArdionTools {
    static String action(JsonObject args) { return args.has("action") ? GameToolSupport.text(args, "action") : "list"; }
    static String required(JsonObject args, String key) {
        String text = GameToolSupport.text(args, key);
        if (text.isBlank()) throw new IllegalArgumentException(key + " is required");
        return text;
    }
    static boolean bool(JsonObject args, String key) {
        if (!args.has(key) || !args.get(key).isJsonPrimitive() || !args.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key + " must be a boolean");
        return args.get(key).getAsBoolean();
    }
    static Module module(String name) {
        Module module = ModuleManager.getModule(name);
        if (module == null) throw new IllegalArgumentException("Unknown registered module: " + name);
        return module;
    }
    static void set(Value<?> value, JsonElement input) {
        if (input == null || !input.isJsonPrimitive()) throw new IllegalArgumentException("value must be a string, number or boolean");
        if (value instanceof NumberValue && !Double.isFinite(input.getAsDouble())) throw new IllegalArgumentException("Number must be finite");
        value.setValueFromString(input.getAsString());
    }
    static JsonObject value(Value<?> value) {
        JsonObject row = new JsonObject();
        row.addProperty("name", value.getName()); row.addProperty("type", value.getClass().getSimpleName());
        row.addProperty("value", value.getValueAsString()); row.addProperty("default", String.valueOf(value.getDefaultValue()));
        row.addProperty("visible", GlobalSetting.isVisible(value));
        if (value instanceof NumberValue number) {
            row.addProperty("min", number.getMin()); row.addProperty("max", number.getMax()); row.addProperty("integer", number.isInteger());
        }
        if (value instanceof ModeValue mode) { JsonArray choices = new JsonArray(); mode.getModes().forEach(choices::add); row.add("choices", choices); }
        return row;
    }
    static JsonObject module(Module module) {
        JsonObject row = new JsonObject();
        row.addProperty("name", module.getName()); row.addProperty("category", module.getCategory().name());
        row.addProperty("enabled", module.isEnabled()); row.addProperty("bind", module.getBindKey());
        row.addProperty("class", module.getClass().getName());
        JsonArray values = new JsonArray(); module.getValues().forEach(value -> values.add(value(value))); row.add("values", values);
        return row;
    }
    static String modules(JsonObject args) throws Exception {
        String action = action(args);
        if (action.equals("list")) {
            String query = GameToolSupport.text(args, "query").toLowerCase(java.util.Locale.ROOT);
            JsonArray rows = new JsonArray();
            ModuleManager.getModules().stream().filter(module -> module.getName().contains(query)).forEach(module -> rows.add(module(module)));
            JsonObject result = new JsonObject(); result.add("modules", rows); return result.toString();
        }
        Module module = module(required(args, "module"));
        JsonObject before = module(module);
        switch (action) {
            case "get" -> { }
            case "enable" -> module.setEnabled(bool(args, "enabled"));
            case "toggle" -> module.toggle();
            case "set", "reset" -> {
                Value<?> value = module.getValue(required(args, "setting"));
                if (value == null) throw new IllegalArgumentException("Unknown module setting");
                if (action.equals("reset")) value.reset(); else set(value, args.get("value"));
            }
            default -> throw new IllegalArgumentException("Unknown module action");
        }
        if (args.has("persist") && bool(args, "persist")) ConfigManager.save("default");
        JsonObject result = new JsonObject(); result.add("before", before); result.add("after", module(module)); return result.toString();
    }
    static String settings(JsonObject args) throws Exception {
        String action = action(args);
        if (action.equals("list")) {
            JsonArray values = new JsonArray(); GlobalSetting.getConfigValues().forEach(value -> values.add(value(value)));
            JsonObject result = new JsonObject(); result.add("settings", values); return result.toString();
        }
        Value<?> value = GlobalSetting.getValue(required(args, "setting"));
        if (value == null) throw new IllegalArgumentException("Unknown global/theme/HUD/target setting");
        JsonObject before = value(value);
        switch (action) {
            case "get" -> { }
            case "set" -> set(value, args.get("value"));
            case "reset" -> value.reset();
            default -> throw new IllegalArgumentException("Unknown settings action");
        }
        if (args.has("persist") && bool(args, "persist")) ConfigManager.save("default");
        JsonObject result = new JsonObject(); result.add("before", before); result.add("after", value(value)); return result.toString();
    }
    static String configs(JsonObject args) throws Exception {
        JsonObject result = new JsonObject();
        switch (action(args)) {
            case "list" -> { JsonArray names = new JsonArray(); ConfigManager.list().forEach(names::add); result.add("configs", names); }
            case "save" -> { ConfigManager.save(required(args, "name")); result.addProperty("saved", true); }
            case "load" -> { ConfigManager.load(required(args, "name")); result.addProperty("loaded", true); }
            case "hud_get" -> result.add("hud", HudConfig.json());
            case "hud_apply" -> {
                if (!args.has("data") || !args.get("data").isJsonObject()) throw new IllegalArgumentException("data must be a HUD config object");
                HudConfig.apply(args.getAsJsonObject("data")); result.add("hud", HudConfig.json());
            }
            default -> throw new IllegalArgumentException("Unknown config action");
        }
        return result.toString();
    }
    static String command(JsonObject args) throws Exception {
        var client = MinecraftClient.getInstance();
        GameToolSupport.requirePlayer(client);
        var dispatcher = ClientCommandManager.getActiveDispatcher();
        if (dispatcher == null) throw new IllegalStateException("Client command tree is unavailable");
        var source = (FabricClientCommandSource) (Object) client.player.networkHandler.getCommandSource();
        String command = GameToolSupport.text(args, "command").strip().replaceFirst("^/", "");
        if (command.contains("\n") || command.contains("\r")) throw new IllegalArgumentException("One client command per call");
        JsonObject result = new JsonObject();
        String action = args.has("action") ? action(args) : "help";
        if (action.equals("execute")) {
            if (command.isBlank()) throw new IllegalArgumentException("command is required");
            result.addProperty("result", dispatcher.execute(dispatcher.parse(command, source)));
            result.add("feedback", GameChatTools.chatLines(client, 12));
        } else if (action.equals("help")) {
            JsonArray usage = new JsonArray();
            var node = dispatcher.getRoot().getChild(command.split(" ", 2)[0]);
            if (node == null) dispatcher.getSmartUsage(dispatcher.getRoot(), source).values().forEach(usage::add);
            else for (String row : dispatcher.getAllUsage(node, source, true)) usage.add(node.getName() + (row.isBlank() ? "" : " " + row));
            var parsed = dispatcher.parse(command, source);
            result.addProperty("valid", !parsed.getReader().canRead() && parsed.getContext().getCommand() != null);
            result.add("usage", usage);
        } else throw new IllegalArgumentException("action must be help or execute");
        result.addProperty("scope", "Local Fabric client command dispatcher, not a chat/server command packet");
        return result.toString();
    }
}
