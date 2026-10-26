package io.github.wzhy233.ardion.util.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class JsonUtil {
    private JsonUtil() {
    }

    public static JsonObject object(JsonObject object, String key) {
        JsonElement element = property(object, key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static String string(JsonObject object, String key, String fallback) {
        JsonElement element = property(object, key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = property(object, key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt()
                : fallback;
    }

    public static long lng(JsonObject object, String key, long fallback) {
        JsonElement element = property(object, key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsLong()
                : fallback;
    }

    public static float flt(JsonObject object, String key, float fallback) {
        JsonElement element = property(object, key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsFloat()
                : fallback;
    }

    private static JsonElement property(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element;
    }
}
