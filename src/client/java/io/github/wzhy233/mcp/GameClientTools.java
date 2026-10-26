package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.mixin.client.ChatScreenAccessor;
import io.github.wzhy233.ardion.mixin.render.BossBarHudAccessor;
import io.github.wzhy233.ardion.mixin.render.InGameHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class GameClientTools {
    private GameClientTools() {
    }

    static byte[] screenshot(int maxEdge) throws Exception {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                GameToolSupport.requirePlayer(client);
                ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> {
                    try {
                        future.complete(encode(image, maxEdge));
                    } catch (Throwable throwable) {
                        future.completeExceptionally(throwable);
                    }
                });
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return GameToolSupport.await(future, 5);
    }

    private static byte[] encode(NativeImage image, int maxEdge) throws IOException {
        NativeImage scaled = image;
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            int edge = Math.max(160, Math.min(maxEdge <= 0 ? 960 : maxEdge, 1280));
            int longest = Math.max(width, height);
            if (longest > edge) {
                int nextWidth = Math.max(1, width * edge / longest);
                int nextHeight = Math.max(1, height * edge / longest);
                scaled = new NativeImage(nextWidth, nextHeight, false);
                image.resizeSubRectTo(0, 0, width, height, scaled);
            }
            Path path = Files.createTempFile("ardion-frame-", ".png");
            try {
                scaled.writeTo(path);
                byte[] bytes = Files.readAllBytes(path);
                JsonObject meta = new JsonObject();
                meta.addProperty("width", scaled.getWidth());
                meta.addProperty("height", scaled.getHeight());
                return pack(meta.toString(), bytes);
            } finally {
                Files.deleteIfExists(path);
            }
        } finally {
            if (scaled != image) scaled.close();
            image.close();
        }
    }

    private static byte[] pack(String meta, byte[] png) {
        byte[] prefix = meta.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (prefix.length > 65535) throw new IllegalStateException("Screenshot metadata is too large");
        byte[] packed = new byte[2 + prefix.length + png.length];
        packed[0] = (byte) (prefix.length >>> 8);
        packed[1] = (byte) prefix.length;
        System.arraycopy(prefix, 0, packed, 2, prefix.length);
        System.arraycopy(png, 0, packed, 2 + prefix.length, png.length);
        return packed;
    }

    static JsonObject screen(MinecraftClient client) {
        JsonObject screen = new JsonObject();
        if (client.currentScreen == null) {
            screen.addProperty("open", false);
            return screen;
        }
        screen.addProperty("open", true);
        screen.addProperty("title", client.currentScreen.getTitle().getString());
        screen.addProperty("type", client.currentScreen.getClass().getSimpleName());
        if (client.currentScreen instanceof ChatScreen chat) {
            var field = ((ChatScreenAccessor) chat).ardion$getChatField();
            if (field != null && !GlobalSetting.getChatChannel().is("ai")
                    && !field.getText().stripLeading().startsWith("/ai")) screen.addProperty("draft", field.getText());
        }
        if (client.currentScreen instanceof HandledScreen<?> handled) {
            JsonArray slots = new JsonArray();
            int shown = 0;
            for (Slot slot : handled.getScreenHandler().slots) {
                if (slot.getStack().isEmpty()) continue;
                if (shown >= 60) break;
                slots.add(GameToolSupport.stack(slot.getStack(), slot.id));
                shown++;
            }
            screen.add("slots", slots);
            ItemStack cursor = handled.getScreenHandler().getCursorStack();
            if (!cursor.isEmpty()) screen.add("cursor", GameToolSupport.stack(cursor, -1));
        }
        return screen;
    }

    static JsonObject hud(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject view = new JsonObject();
        InGameHud inGameHud = client.inGameHud;
        if (inGameHud != null) {
            InGameHudAccessor accessor = (InGameHudAccessor) inGameHud;
            Text title = accessor.ardion$getTitle();
            if (accessor.ardion$getTitleRemainTicks() > 0 && title != null && !title.getString().isBlank()) {
                view.addProperty("title", title.getString());
                Text subtitle = accessor.ardion$getSubtitle();
                if (subtitle != null && !subtitle.getString().isBlank()) view.addProperty("subtitle", subtitle.getString());
            }
            Text action = accessor.ardion$getOverlayMessage();
            if (accessor.ardion$getOverlayRemaining() > 0 && action != null && !action.getString().isBlank()) {
                view.addProperty("actionBar", action.getString());
            }
            if (inGameHud.getBossBarHud() != null) {
                JsonArray bosses = new JsonArray();
                for (ClientBossBar bar : ((BossBarHudAccessor) inGameHud.getBossBarHud()).ardion$getBossBars().values()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("name", bar.getName().getString());
                    row.addProperty("percent", GameToolSupport.round(bar.getPercent()));
                    row.addProperty("color", bar.getColor().name().toLowerCase(Locale.ROOT));
                    row.addProperty("style", bar.getStyle().name().toLowerCase(Locale.ROOT));
                    bosses.add(row);
                    if (bosses.size() >= 8) break;
                }
                view.add("bossBars", bosses);
            }
        }
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective != null) {
            JsonObject sidebar = new JsonObject();
            sidebar.addProperty("name", objective.getDisplayName().getString());
            JsonArray lines = new JsonArray();
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                if (entry.hidden()) continue;
                JsonObject line = new JsonObject();
                Text label = entry.name();
                line.addProperty("text", label == null ? entry.owner() : label.getString());
                line.addProperty("score", entry.value());
                lines.add(line);
                if (lines.size() >= 20) break;
            }
            sidebar.add("lines", lines);
            view.add("sidebar", sidebar);
        }
        JsonArray hotbar = new JsonArray();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inventory.getStack(slot);
            if (!item.isEmpty()) hotbar.add(GameToolSupport.stack(item, slot));
        }
        view.add("hotbar", hotbar);
        view.addProperty("selectedSlot", inventory.getSelectedSlot());
        return view;
    }

    static JsonObject clientView(MinecraftClient client) {
        JsonObject view = new JsonObject();
        view.addProperty("fps", client.getCurrentFps());
        view.addProperty("paused", client.isPaused());
        if (client.getWindow() != null) {
            view.addProperty("width", client.getWindow().getScaledWidth());
            view.addProperty("height", client.getWindow().getScaledHeight());
            view.addProperty("framebufferWidth", client.getWindow().getFramebufferWidth());
            view.addProperty("framebufferHeight", client.getWindow().getFramebufferHeight());
            view.addProperty("scale", client.getWindow().getScaleFactor());
        }
        GameOptions options = client.options;
        if (options != null) {
            view.addProperty("hudHidden", options.hudHidden);
            view.addProperty("viewDistance", options.getViewDistance().getValue());
            view.addProperty("simulationDistance", options.getSimulationDistance().getValue());
            view.addProperty("fov", options.getFov().getValue());
            view.addProperty("guiScale", options.getGuiScale().getValue());
            view.addProperty("gamma", options.getGamma().getValue());
        }
        if (client.getLanguageManager() != null) view.addProperty("language", client.getLanguageManager().getLanguage());
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler != null) {
            String brand = handler.getBrand();
            if (brand != null && !brand.isBlank()) view.addProperty("serverBrand", brand);
            ServerInfo info = handler.getServerInfo();
            if (info != null) {
                if (info.name != null) view.addProperty("server", info.name);
                if (info.address != null) view.addProperty("address", info.address);
                view.addProperty("ping", info.ping);
            }
        }
        if (client.world != null) view.addProperty("worldSimulationDistance", client.world.getSimulationDistance());
        return view;
    }

    static String runtime() {
        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject root = new JsonObject();
        root.addProperty("java", System.getProperty("java.version", ""));
        root.addProperty("javaVendor", System.getProperty("java.vendor", ""));
        root.addProperty("os", System.getProperty("os.name", ""));
        root.addProperty("arch", System.getProperty("os.arch", ""));
        root.addProperty("maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        FabricLoader loader = FabricLoader.getInstance();
        Path game = loader.getGameDir();
        Path config = loader.getConfigDir();
        root.addProperty("gameDir", game.toString());
        root.addProperty("configDir", config.toString());
        root.addProperty("logs", game.resolve("logs").toString());
        root.addProperty("mods", game.resolve("mods").toString());
        root.addProperty("saves", game.resolve("saves").toString());
        root.addProperty("crashReports", game.resolve("crash-reports").toString());
        String minecraft = "";
        JsonArray mods = new JsonArray();
        int count = 0;
        for (ModContainer container : loader.getAllMods()) {
            ModMetadata meta = container.getMetadata();
            if ("minecraft".equals(meta.getId())) minecraft = meta.getVersion().getFriendlyString();
            if (count >= 400) continue;
            JsonObject item = new JsonObject();
            item.addProperty("id", meta.getId());
            item.addProperty("name", meta.getName());
            item.addProperty("version", meta.getVersion().getFriendlyString());
            mods.add(item);
            count++;
        }
        root.addProperty("minecraft", minecraft);
        root.add("mods", mods);
        if (client.options != null) root.addProperty("viewDistance", client.options.getViewDistance().getValue());
        boolean inWorld = client.world != null && client.player != null;
        root.addProperty("inWorld", inWorld);
        if (inWorld) root.addProperty("dimension", client.world.getRegistryKey().getValue().toString());
        return root.toString();
    }
}
