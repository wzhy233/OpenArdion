package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.mixin.render.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.ClientPlayerEntity;
import java.util.List;

final class GameChatTools {
    private GameChatTools() {
    }

    static String chat(int limit) {
        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject root = new JsonObject();
        root.add("lines", chatLines(client, limit));
        return root.toString();
    }

    static String chatSend(String text) {
        String message = checkChat(text, false);
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        if (player.networkHandler == null) throw new IllegalStateException("Not connected");
        if (client.inGameHud != null) client.inGameHud.getChatHud().addToMessageHistory(message);
        player.networkHandler.sendChatMessage(message);
        return "{\"sent\":true}";
    }

    static String commandHelp(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        var handler = player.networkHandler;
        if (handler == null) throw new IllegalStateException("Not connected");
        String command = text.strip().replaceFirst("^/", "");
        var dispatcher = handler.getCommandDispatcher();
        var source = handler.getCommandSource();
        var parsed = dispatcher.parse(command, source);
        var node = dispatcher.getRoot();
        String head = command.split(" ", 2)[0];
        var child = node.getChild(head);
        JsonObject result = new JsonObject();
        JsonArray usage = new JsonArray();
        if (child == null) {
            for (var entry : dispatcher.getSmartUsage(node, source).values()) usage.add(entry);
        } else {
            for (String entry : dispatcher.getAllUsage(child, source, true)) usage.add(head + (entry.isEmpty() ? "" : " " + entry));
        }
        result.add("usage", usage);
        result.addProperty("available", command.isEmpty() || child != null);
        result.addProperty("valid", !command.isEmpty() && !parsed.getReader().canRead() && parsed.getContext().getCommand() != null);
        result.addProperty("cursor", parsed.getReader().getCursor());
        result.addProperty("note", "Commands advertised at the current permission level; a missing command does not prove a world's cheats setting. Sending is not proof of execution. Read chat_log and state afterwards.");
        return result.toString();
    }

    static String commandSend(String text) {
        String command = checkChat(text, true);
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank()) throw new IllegalArgumentException("Command is empty");
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = GameToolSupport.requirePlayer(client);
        if (player.networkHandler == null) throw new IllegalStateException("Not connected");
        if (client.inGameHud != null) client.inGameHud.getChatHud().addToMessageHistory("/" + command);
        player.networkHandler.sendChatCommand(command);
        return "{\"sent\":true,\"executionConfirmed\":false,\"next\":\"Read chat_log and state to check the server result\"}";
    }

    private static String checkChat(String text, boolean command) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(command ? "Command is empty" : "Chat message is empty");
        String message = text.replace("\r", "").replace("\n", "").trim();
        if (!command && message.startsWith("/")) throw new IllegalArgumentException("Use send_command for slash commands");
        if (message.length() > 256) throw new IllegalArgumentException("Minecraft chat accepts at most 256 characters");
        return message;
    }

    static JsonArray chatLines(MinecraftClient client, int limit) {
        if (client.inGameHud == null) return new JsonArray();
        return chatLines(((ChatHudAccessor) client.inGameHud.getChatHud()).ardion$getMessages(), limit);
    }

    static JsonArray chatLines(List<ChatHudLine> messages, int limit) {
        JsonArray lines = new JsonArray();
        int cap = Math.max(1, Math.min(limit, 80));
        for (int index = 0; index < messages.size() && lines.size() < cap; index++) {
            String line = messages.get(index).content().getString();
            if (!line.isBlank() && !line.startsWith("[AI] ")) lines.add(line);
        }
        JsonArray ordered = new JsonArray();
        for (int index = lines.size() - 1; index >= 0; index--) ordered.add(lines.get(index));
        return ordered;
    }
}
