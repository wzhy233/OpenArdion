package io.github.wzhy233.mcp;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class GameToolSupport {
    private GameToolSupport() {
    }

    static JsonObject stack(ItemStack stack, int slot) {
        JsonObject row = new JsonObject();
        row.addProperty("slot", slot);
        if (stack.isEmpty()) {
            row.addProperty("empty", true);
            return row;
        }
        row.addProperty("id", String.valueOf(Registries.ITEM.getId(stack.getItem())));
        row.addProperty("name", stack.getName().getString());
        row.addProperty("count", stack.getCount());
        if (stack.isDamageable()) {
            row.addProperty("damage", stack.getDamage());
            row.addProperty("maxDamage", stack.getMaxDamage());
        }
        return row;
    }

    static JsonObject vec(Vec3d vec) {
        JsonObject row = new JsonObject();
        row.addProperty("x", round(vec.x));
        row.addProperty("y", round(vec.y));
        row.addProperty("z", round(vec.z));
        return row;
    }

    static ClientPlayerEntity requirePlayer(MinecraftClient client) {
        if (client.player == null || client.world == null) throw new IllegalStateException("Not in a world");
        return client.player;
    }

    static String onClient(java.util.function.Supplier<String> supplier) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) return supplier.get();
        CompletableFuture<String> future = new CompletableFuture<>();
        client.execute(() -> {
            if (future.isDone()) return;
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return await(future, 4);
    }

    static <T> T await(CompletableFuture<T> future, int seconds) throws Exception {
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(false);
            throw new IOException("The game thread did not answer in time");
        } catch (InterruptedException exception) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            throw new IOException(cause == null ? "Game call failed" : cause.getMessage());
        }
    }

    static double number(JsonObject args, String key, double fallback) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull() || !args.get(key).isJsonPrimitive()) return fallback;
        try {
            double value = args.get(key).getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    static String text(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull() || !args.get(key).isJsonPrimitive()) return "";
        return args.get(key).getAsString().trim();
    }

    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
