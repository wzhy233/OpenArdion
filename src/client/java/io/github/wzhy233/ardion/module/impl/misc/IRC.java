package io.github.wzhy233.ardion.module.impl.misc;

import io.github.wzhy233.ardion.irc.IRCClient;
import io.github.wzhy233.ardion.irc.IRCMessage;
import io.github.wzhy233.ardion.irc.IRCPresence;
import io.github.wzhy233.ardion.irc.IrcNameOverlay;
import io.github.wzhy233.ardion.irc.IrcProxy;
import io.github.wzhy233.ardion.setting.ProxySetting;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.module.impl.render.CustomSkin;
import io.github.wzhy233.ardion.skin.CustomSkinService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.entity.player.PlayerSkinType;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

public final class IRC extends Module {
    private final BooleanValue toyNameOverride = this.registerValue(new BooleanValue("toy-name-override", this, false));
    private final BooleanValue useProxy = this.registerValue(new BooleanValue("use-proxy", this, false));
    private volatile IRCClient client;
    private ThreadPoolExecutor sender;
    private String proxyKey = "";
    private Map<UUID, IRCPresence> sharedSkins = Map.of();

    public IRC() {
        super("irc", ModuleCategory.MISC);
        IrcProxy.watch(useProxy::getValue);
        if (isActive()) ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> stop());
    }

    @Override
    protected void onEnable() {
        proxyKey = proxyKey();
        IRCClient connection = new IRCClient();
        client = connection;
        sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), task -> {
                    Thread thread = new Thread(task, "ardion-irc-send");
                    thread.setDaemon(true);
                    return thread;
                });
        connection.addListener(new IRCClient.Listener() {
            private void display(String message, Formatting color) {
                MinecraftClient.getInstance().execute(() -> {
                    if (client == connection) chat(message, color);
                });
            }

            @Override
            public void onConnected(String username) {
                display("Connected: " + username, Formatting.GREEN);
            }

            @Override
            public void onMessage(IRCMessage message) {
                String permission = message.getUser().getPermission();
                String badge = permission.equalsIgnoreCase("User") ? "" : "[" + permission + "] ";
                display(badge + message.getUser().getUsername() + ": " + message.getContent(), Formatting.WHITE);
            }

            @Override
            public void onSystemMessage(String message) {
                display("* " + message, Formatting.GRAY);
            }

            @Override
            public void onError(String message) {
                display(message, Formatting.RED);
            }

            @Override
            public void onDisconnected() {
                display("Disconnected. Reconnecting...", Formatting.YELLOW);
            }
        });
        connection.start();
    }

    @Override
    protected void onDisable() {
        stop();
    }

    private void stop() {
        IrcNameOverlay.clear();
        clearSharedSkins();
        IRCClient previous = client;
        client = null;
        if (previous != null) previous.shutdown();
        if (sender != null) sender.shutdownNow();
        sender = null;
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) return;
        String nextProxy = proxyKey();
        if (!nextProxy.equals(proxyKey)) {
            proxyKey = nextProxy;
            if (client != null) client.reconnect();
        }
        IRCClient connection = client;
        MinecraftClient minecraft = event.getClient();
        if (connection == null || !connection.isConnected()) {
            IrcNameOverlay.clear();
            clearSharedSkins();
            return;
        }

        String mcName = minecraft.player == null || minecraft.world == null
                ? "" : minecraft.player.getGameProfile().name();
        UUID uuid = mcName.isEmpty() ? null : minecraft.player.getUuid();
        CustomSkin customSkin = CustomSkin.get();
        String skinName = customSkin == null ? "" : customSkin.sharedSkinName();
        try {
            connection.syncPresence(new IRCPresence(mcName, uuid, skinName,
                    customSkin != null && customSkin.isEnabled(), customSkin != null && customSkin.capeEnabled(),
                    customSkin == null ? "auto" : customSkin.modelName()));
        } catch (IllegalStateException ignored) {
            IrcNameOverlay.clear();
            clearSharedSkins();
            return;
        }
        updateSharedSkins(minecraft, connection, !skinName.isEmpty());

        if (!toyNameOverride.getValue() || minecraft.world == null || minecraft.getNetworkHandler() == null) {
            IrcNameOverlay.clear();
            return;
        }
        Set<String> present = new HashSet<>();
        minecraft.getNetworkHandler().getListedPlayerListEntries().forEach(entry ->
                present.add(entry.getProfile().name().toLowerCase(Locale.ROOT)));
        minecraft.world.getPlayers().forEach(player ->
                present.add(player.getGameProfile().name().toLowerCase(Locale.ROOT)));

        Map<String, String> names = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        connection.getOnlineUsers().forEach(user -> {
            String worldName = user.getMcName();
            if (worldName == null || !worldName.matches("[A-Za-z0-9_]{1,16}")) return;
            String key = worldName.toLowerCase(Locale.ROOT);
            if (present.contains(key) && names.putIfAbsent(key, user.getUsername()) != null) {
                duplicates.add(key);
            }
        });
        names.keySet().removeAll(duplicates);
        IrcNameOverlay.setNames(names);
    }

    private void updateSharedSkins(MinecraftClient minecraft, IRCClient connection, boolean localReady) {
        if (!localReady || minecraft.world == null || minecraft.player == null || minecraft.getNetworkHandler() == null) {
            clearSharedSkins();
            return;
        }
        Map<UUID, String> players = new HashMap<>();
        minecraft.getNetworkHandler().getPlayerList().forEach(entry -> players.put(entry.getProfile().id(), entry.getProfile().name()));
        minecraft.world.getPlayers().forEach(player -> players.put(player.getUuid(), player.getGameProfile().name()));
        Map<UUID, IRCPresence> peers = new HashMap<>();
        Set<UUID> claimed = new HashSet<>();
        Set<UUID> duplicates = new HashSet<>();
        connection.getOnlineUsers().forEach(user -> {
            IRCPresence presence = user.getPresence();
            if (presence.uuid() == null) return;
            if (!claimed.add(presence.uuid())) duplicates.add(presence.uuid());
            String name = players.get(presence.uuid());
            if (presence.sharesSkin() && name != null && presence.matches(presence.uuid(), name)
                    && !presence.uuid().equals(minecraft.player.getUuid())) {
                peers.put(presence.uuid(), presence);
            }
        });
        peers.keySet().removeAll(duplicates);
        sharedSkins = Map.copyOf(peers);
        Set<String> skins = new HashSet<>();
        peers.values().forEach(peer -> skins.add(peer.skinName()));
        CustomSkinService.syncShared(skins);
    }

    private void clearSharedSkins() {
        sharedSkins = Map.of();
        CustomSkinService.clearShared();
    }

    public SkinTextures sharedTextures(UUID uuid, String playerName) {
        IRCClient connection = client;
        CustomSkin local = CustomSkin.get();
        if (!isEnabled() || connection == null || !connection.isConnected()
                || local == null || local.sharedSkinName().isEmpty()) return null;
        IRCPresence peer = sharedSkins.get(uuid);
        if (peer == null || !peer.sharesSkin() || !peer.matches(uuid, playerName)) return null;
        PlayerSkinType model = switch (peer.model()) {
            case "slim" -> PlayerSkinType.SLIM;
            case "wide" -> PlayerSkinType.WIDE;
            default -> null;
        };
        return CustomSkinService.sharedTextures(peer.skinName(), local.capeEnabled() && peer.cape(), model);
    }

    public void reconnect() {
        if (client != null) client.reconnect();
    }

    private String proxyKey() {
        return useProxy.getValue() ? "on\n" + ProxySetting.key() : "off";
    }

    public void showStatus() {
        IRCClient connection = client;
        chat(connection == null ? "Off" : connection.isConnected() ? "Connected" : "Not connected",
                Formatting.GRAY);
    }

    public void showUsers() {
        IRCClient connection = client;
        if (connection == null || !connection.isConnected()) {
            chat("Not connected", Formatting.RED);
            return;
        }
        String users = connection.getOnlineUsers().stream()
                .map(user -> user.getPermission().equalsIgnoreCase("User")
                        ? user.getUsername() : user.getUsername() + " [" + user.getPermission() + "]")
                .collect(java.util.stream.Collectors.joining(", "));
        chat("Online (" + connection.getOnlineUsers().size() + "): "
                + (users.isEmpty() ? "none" : users), Formatting.GRAY);
    }

    public void send(String input) {
        IRCClient connection = client;
        if (connection == null || !connection.isConnected()) {
            chat("Not connected. Use /ardion irc connect.", Formatting.RED);
            return;
        }
        String message = input.trim();
        if (message.isEmpty()) return;
        if (message.getBytes(StandardCharsets.UTF_8).length > 512) {
            chat("Message too long (max 512 bytes)", Formatting.RED);
            return;
        }
        try {
            sender.execute(() -> {
                try {
                    if (message.startsWith("/")) connection.sendCommand(message.substring(1));
                    else connection.sendMessage(message);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (client == connection) chat(exception.getMessage(), Formatting.RED);
                    });
                }
            });
        } catch (RejectedExecutionException exception) {
            chat("Too many messages. Try again in a moment.", Formatting.RED);
        }
    }

    public static void chat(String message, Formatting color) {
        OutputUtil.send(Text.literal("[IRC] ").formatted(Formatting.AQUA)
                .append(Text.literal(message).formatted(color)), false);
    }

    public static boolean interceptChat(String message) {
        if (message == null || message.isEmpty()) return false;
        boolean prefixed = message.startsWith("$");
        boolean ircMode = GlobalSetting.getChatChannel().is("irc") && !message.startsWith("/");
        if (!prefixed && !ircMode) return false;
        IRC module = ModuleManager.getModule(IRC.class);
        if (module == null) {
            chat("IRC unavailable", Formatting.RED);
            return true;
        }
        module.send(prefixed ? message.substring(1) : message);
        return true;
    }
}
