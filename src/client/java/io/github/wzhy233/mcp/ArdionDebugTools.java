package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;

final class ArdionDebugTools {
    static String inspect(JsonObject args) throws Exception {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        String target = ArdionTools.required(args, "target");
        Object object = switch (target) {
            case "client" -> client;
            case "player" -> client.player;
            case "world" -> client.world;
            default -> target.startsWith("module:") ? ArdionTools.module(target.substring(7)) : null;
        };
        Class<?> type;
        if (target.startsWith("class:")) type = Class.forName(target.substring(6), false, client.getClass().getClassLoader());
        else if (object != null) type = object.getClass();
        else throw new IllegalArgumentException("Target must be client, player, world, module:<name> or class:<name>; world/player require a connection");
        String query = GameToolSupport.text(args, "query").toLowerCase(java.util.Locale.ROOT);
        JsonObject result = new JsonObject(); result.addProperty("class", type.getName());
        result.addProperty("mappingNamespace", net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace());
        JsonArray fields = new JsonArray(), methods = new JsonArray();
        for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            for (var field : parent.getDeclaredFields()) {
                if (!field.getName().toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                JsonObject row = new JsonObject(); row.addProperty("name", field.getName()); row.addProperty("owner", parent.getName());
                row.addProperty("type", field.getType().getName()); row.addProperty("modifiers", Modifier.toString(field.getModifiers()));

                if (object != null && !Modifier.isStatic(field.getModifiers()) && !field.getName().toLowerCase(java.util.Locale.ROOT).matches(".*(token|key|secret|password|cookie|session|message|history).*")) {
                    try {
                        if (field.trySetAccessible() && (field.getType().isPrimitive() || field.getType().isEnum())) {
                            Object value = field.get(object);
                            row.addProperty("value", value instanceof Enum<?> entry ? entry.name() : String.valueOf(value));
                        }
                    } catch (RuntimeException | IllegalAccessException ignored) { }
                }
                fields.add(row);
            }
            for (var method : parent.getDeclaredMethods()) if (method.getName().toLowerCase(java.util.Locale.ROOT).contains(query)) methods.add(method.toGenericString());
        }
        result.add("fields", fields); result.add("methods", methods); result.addProperty("readOnly", true); return result.toString();
    }
    static String threads(JsonObject args) {
        String query = GameToolSupport.text(args, "query").toLowerCase(java.util.Locale.ROOT);
        var bean = ManagementFactory.getThreadMXBean();
        JsonArray rows = new JsonArray();
        for (var thread : bean.dumpAllThreads(true, true)) {
            if (!thread.getThreadName().toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            JsonObject row = new JsonObject(); row.addProperty("id", thread.getThreadId()); row.addProperty("name", thread.getThreadName());
            row.addProperty("state", thread.getThreadState().name()); row.addProperty("blockedCount", thread.getBlockedCount());
            row.addProperty("lockOwner", thread.getLockOwnerName());
            JsonArray stack = new JsonArray(); for (var frame : thread.getStackTrace()) stack.add(frame.toString()); row.add("stack", stack); rows.add(row);
        }
        JsonObject result = new JsonObject(); result.add("threads", rows); result.addProperty("readOnly", true); return result.toString();
    }
    static String memory() {
        var memory = ManagementFactory.getMemoryMXBean();
        JsonObject result = new JsonObject(); result.addProperty("heapUsed", memory.getHeapMemoryUsage().getUsed()); result.addProperty("heapMax", memory.getHeapMemoryUsage().getMax());
        result.addProperty("nonHeapUsed", memory.getNonHeapMemoryUsage().getUsed()); result.addProperty("loadedClasses", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        result.addProperty("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime()); result.addProperty("readOnly", true); return result.toString();
    }
}
