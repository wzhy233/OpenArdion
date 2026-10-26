package io.github.wzhy233.ardion.irc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

public record IRCPresence(String mcName, UUID uuid, String skinName, boolean customSkin, boolean cape, String model) {
    public static final IRCPresence EMPTY = new IRCPresence("", null, "", false, true, "auto");

    public IRCPresence {
        if (mcName == null || !mcName.matches("[A-Za-z0-9_]{0,16}")
                || skinName == null || !skinName.isEmpty() && !skinName.matches("[A-Za-z0-9_]{3,16}")
                || !customSkin && !skinName.isEmpty()
                || !"auto".equals(model) && !"slim".equals(model) && !"wide".equals(model)) {
            throw new IllegalArgumentException("Invalid IRC presence");
        }
    }

    public boolean sharesSkin() {
        return customSkin && uuid != null && !mcName.isEmpty() && !skinName.isEmpty();
    }

    public boolean matches(UUID playerId, String playerName) {
        return uuid != null && uuid.equals(playerId) && mcName.equalsIgnoreCase(playerName);
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.addProperty("mcName", mcName);
        result.addProperty("uuid", uuid == null ? "" : uuid.toString());
        result.addProperty("skin-name", skinName);
        result.addProperty("customSkin", customSkin);
        result.addProperty("cape", cape);
        result.addProperty("model", model);
        return result;
    }

    public static IRCPresence fromJson(JsonObject json) {
        String id = string(json, "uuid", "");
        if (!id.isEmpty() && !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Invalid IRC player UUID");
        }
        return new IRCPresence(string(json, "mcName", ""), id.isEmpty() ? null : UUID.fromString(id),
                string(json, "skin-name", ""), bool(json, "customSkin", false),
                bool(json, "cape", true), string(json, "model", "auto"));
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement value = json.get(key);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid IRC presence field: " + key);
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = json.get(key);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Invalid IRC presence field: " + key);
        }
        return value.getAsBoolean();
    }
}
