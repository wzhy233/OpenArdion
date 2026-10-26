package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public final class GameTools {
    private GameTools() {
    }

    public static List<Spec> specs() {
        var specs = new java.util.ArrayList<>(List.of(
                spec("debug", "Read the live game process: player, biome, light, weather, targeted block properties, nearby blocks, entities, open screen, container, HUD, boss bars, scoreboard, titles, chat, and client stats. Read only.", schema(
                        Map.of(
                                "radius", prop("number", "Nearby block radius. Default 4, max 8. Entities are the nearest ones within 32 blocks."),
                                "blockLimit", prop("number", "Maximum nearby blocks. Default 80, max 120."),
                                "entityLimit", prop("number", "Maximum entities. Default 24, max 48.")
                        ), List.of())),
                spec("state", "Read the local player, world, crosshair, and open screen. Nothing here can be changed. Use debug for the full scene.", schema()),
                spec("inventory", "Read the player inventory and any open container. Read only.", schema()),
                spec("entities", "Read nearby entities. Read only.", schema(
                        Map.of(
                                "radius", prop("number", "Blocks around the player. Default 32, max 96."),
                                "limit", prop("number", "Maximum rows. Default 32, max 80.")
                        ), List.of())),
                spec("block", "Read one block. Read only.", schema(
                        Map.of(
                                "x", prop("number", "Block x"),
                                "y", prop("number", "Block y"),
                                "z", prop("number", "Block z")
                        ), List.of("x", "y", "z"))),
                spec("players", "Read the tab list: name, latency, and gamemode. No account or session data.", schema()),
                spec("chat_log", "Read recent chat lines, oldest first. Read only.", schema(
                        Map.of("limit", prop("number", "Default 40, max 80.")), List.of())),
                spec("screenshot", "Capture the current Minecraft frame as a PNG.", schema(
                        Map.of("maxEdge", prop("number", "Longest side in pixels. Default 960, max 1280.")), List.of())),
                spec("send_chat", "Send a chat message as the player. Not a slash command.", schema(
                        Map.of("text", prop("string", "Message to send.")), List.of("text"))),
                spec("send_command", "Run a slash command as the player. Do not include the leading slash.", schema(
                        Map.of("command", prop("string", "Command without the leading slash.")), List.of("command"))),
                spec("command_help", "Read the connected server's command syntax and validate a command without running it. Use when command syntax or availability needs checking. An empty command lists available roots; syntax reflects this server and Minecraft version.", schema(
                        Map.of("command", prop("string", "Command or root to inspect, without a leading slash.")), List.of())),
                spec("runtime", "Read mods, paths, JVM, and client settings. No account or session data.", schema()),
                spec("prepare_bot", "Prepare a separate local Alex after the user explicitly requested a bot in their current singleplayer world. Opens LAN if necessary, preserves game mode/cheats/account verification, reuses an existing LAN port, and returns actual loopback connection options. Authorizes one local Alex login for 30 seconds. Not needed for current-player control.", schema())
        ));
        specs.addAll(GameActionSpecs.specs());
        specs.addAll(EcosystemSpecs.specs());
        return List.copyOf(specs);
    }

    public static Result call(String name, JsonObject args) throws Exception {
        if (name != null && (name.equals("jvm_write") || name.equals("memory_write") || name.equals("raw_packet") || name.equals("send_packet")
                || name.startsWith("jvm_") || name.contains("packet_send"))) {
            throw new IllegalArgumentException("Forbidden tool: agents cannot modify JVM memory or send raw packets");
        }
        JsonObject input = args == null ? new JsonObject() : args;
        return switch (name) {
            case "prepare_bot" -> text(GameToolSupport.onClient(GameBotTools::prepare));
            case "debug" -> text(GameToolSupport.onClient(() -> GameSceneTools.debug(GameToolSupport.number(input, "radius", 4), (int) GameToolSupport.number(input, "blockLimit", 80), (int) GameToolSupport.number(input, "entityLimit", 24))));
            case "state" -> text(GameToolSupport.onClient(GameSceneTools::state));
            case "inventory" -> text(GameToolSupport.onClient(GameSceneTools::inventory));
            case "entities" -> text(GameToolSupport.onClient(() -> GameSceneTools.entities(GameToolSupport.number(input, "radius", 32), (int) GameToolSupport.number(input, "limit", 32))));
            case "block" -> text(GameToolSupport.onClient(() -> GameWorldTools.block(GameToolSupport.number(input, "x", Double.NaN), GameToolSupport.number(input, "y", Double.NaN), GameToolSupport.number(input, "z", Double.NaN))));
            case "players" -> text(GameToolSupport.onClient(GameSceneTools::players));
            case "chat_log" -> text(GameToolSupport.onClient(() -> GameChatTools.chat((int) GameToolSupport.number(input, "limit", 40))));
            case "screenshot" -> image(GameClientTools.screenshot((int) GameToolSupport.number(input, "maxEdge", 960)));
            case "send_chat" -> text(GameToolSupport.onClient(() -> GameChatTools.chatSend(GameToolSupport.text(input, "text"))));
            case "send_command" -> text(GameToolSupport.onClient(() -> GameChatTools.commandSend(GameToolSupport.text(input, "command"))));
            case "command_help" -> text(GameToolSupport.onClient(() -> GameChatTools.commandHelp(input.has("command") ? GameToolSupport.text(input, "command") : "")));
            case "runtime" -> text(GameToolSupport.onClient(GameClientTools::runtime));
            case "find_blocks" -> text(GameToolSupport.onClient(() -> GameWorldTools.find(input)));
            case "navigate" -> text(GameToolSupport.onClient(() -> GameActionTools.start("navigate", input)));
            case "mine_block" -> text(GameToolSupport.onClient(() -> GameActionTools.start("mine", input)));
            case "use_item" -> text(GameToolSupport.onClient(() -> GameActionTools.start("use", input)));
            case "wait_ticks" -> text(GameToolSupport.onClient(() -> GameActionTools.start("wait", input)));
            case "craft_recipe" -> text(GameToolSupport.onClient(() -> GameActionTools.start("craft", input)));
            case "action_status" -> text(GameActionTools.status(input));
            case "cancel_action" -> text(GameToolSupport.onClient(() -> GameActionTools.cancel("Cancelled by agent")));
            case "interact_block" -> text(GameToolSupport.onClient(() -> GameInteractionTools.block(input)));
            case "interact_entity" -> text(GameToolSupport.onClient(() -> GameInteractionTools.entity(input)));
            case "aim_entity" -> text(GameToolSupport.onClient(() -> GameInteractionTools.aim(input)));
            case "inventory_action" -> text(GameToolSupport.onClient(() -> GameInventoryTools.action(input)));
            case "recipes" -> text(GameToolSupport.onClient(() -> GameInventoryTools.recipes(input)));
            case "ardion_modules" -> text(GameToolSupport.onClient(() -> checked(() -> ArdionTools.modules(input))));
            case "ardion_settings" -> text(GameToolSupport.onClient(() -> checked(() -> ArdionTools.settings(input))));
            case "ardion_config" -> text(GameToolSupport.onClient(() -> checked(() -> ArdionTools.configs(input))));
            case "ardion_command" -> text(GameToolSupport.onClient(() -> checked(() -> ArdionTools.command(input))));
            case "ardion_music" -> text(ArdionMusicTools.call(input));
            case "client_inspect" -> text(GameToolSupport.onClient(() -> checked(() -> ArdionDebugTools.inspect(input))));
            case "client_threads" -> text(ArdionDebugTools.threads(input));
            case "client_memory" -> text(ArdionDebugTools.memory());
            case "client_events" -> text(io.github.wzhy233.ardion.debug.ClientDiagnostics.control(ArdionTools.required(input, "action"), GameToolSupport.text(input, "query"), input.has("seconds") ? GameInventoryTools.integer(input, "seconds", 1, 300) : 30));
            case "lua" -> text(LuaRuntime.call(input));
            default -> throw new IllegalArgumentException("Unknown tool");
        };
    }

    static Result image(byte[] packed) {
        int length = ((packed[0] & 0xFF) << 8) | (packed[1] & 0xFF);
        String meta = new String(packed, 2, length, java.nio.charset.StandardCharsets.UTF_8);
        byte[] png = java.util.Arrays.copyOfRange(packed, 2 + length, packed.length);
        return new Result(meta, png, false);
    }

    private static String checked(java.util.concurrent.Callable<String> call) {
        try { return call.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException(exception.getMessage(), exception); }
    }

    private static Result text(String value) {
        return new Result(value, null, false);
    }

    static Spec spec(String name, String description, JsonObject schema) {
        return new Spec(name, description, schema);
    }

    static JsonObject schema() {
        return schema(Map.of(), List.of());
    }

    static JsonObject schema(Map<String, JsonObject> properties, List<String> required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject fields = new JsonObject();
        properties.forEach(fields::add);
        schema.add("properties", fields);
        if (!required.isEmpty()) {
            JsonArray names = new JsonArray();
            required.forEach(names::add);
            schema.add("required", names);
        }
        return schema;
    }

    static JsonObject prop(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }

    public static void ensureTick() {
        GameActionTools.ensureTick();
    }

    public static void releaseAll() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (!client.isOnThread()) { client.execute(GameTools::releaseAll); return; }
        GameActionTools.cancel("All agent inputs released");
        GameMovement.release();
    }

    public static boolean isUsingItem() { return GameActionTools.usingItem(); }

    public static boolean isMining() { return GameActionTools.mining(); }

    public record Spec(String name, String description, JsonObject schema) {
    }

    public record Result(String text, byte[] png, boolean error) {
        static Result fail(String text) {
            return new Result(text, null, true);
        }
    }
}
