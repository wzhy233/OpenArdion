package io.github.wzhy233.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.minecraft.client.MinecraftClient;

public final class AgentNotify {
    private static String last = "";
    private static long at;

    private AgentNotify() {
    }

    public static void post(String level, String message) {
        if (message == null || message.isBlank()) return;
        String text = message.replaceAll("\\s+", " ").strip();
        if (text.length() > 180) text = text.substring(0, 179) + "…";
        long now = System.currentTimeMillis();
        synchronized (AgentNotify.class) {
            if (text.equals(last) && now - at < 2000) return;
            last = text;
            at = now;
        }
        String line = text;
        Runnable run = () -> {
            switch (level == null ? "" : level) {
                case "success" -> OutputUtil.success(line);
                case "warn", "warning" -> OutputUtil.warn(line);
                case "error" -> OutputUtil.error(line);
                default -> OutputUtil.info(line);
            }
        };
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && !client.isOnThread()) client.execute(run);
            else run.run();
        } catch (Throwable ignored) {
        }
    }

    static void tool(String type, JsonObject part) {
        String name = part != null && part.has("tool") && part.get("tool").isJsonPrimitive() ? part.get("tool").getAsString() : "";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("voyager") && !lower.endsWith("lua") && !lower.contains("_lua")) return;
        JsonObject state = part != null && part.has("state") && part.get("state").isJsonObject() ? part.getAsJsonObject("state") : null;
        String error = error(state);
        if (type.equals("tool_start")) post("info", "Running " + name);
        else if (error != null) post("error", name + " failed: " + error);
        else post("success", "Finished " + name);
    }

    private static String error(JsonObject state) {
        if (state == null || !state.has("error") || state.get("error").isJsonNull()) return null;
        JsonElement error = state.get("error");
        if (error.isJsonPrimitive()) return error.getAsString();
        if (error.isJsonObject() && error.getAsJsonObject().has("message")) return error.getAsJsonObject().get("message").getAsString();
        return null;
    }
}
