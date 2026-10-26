package io.github.wzhy233.mcp;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NetworkUtils;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

public final class GameBotTools {
    private record LoginPermit(MinecraftServer server, long expires) { }
    private static final AtomicReference<LoginPermit> LOGIN = new AtomicReference<>();

    private GameBotTools() { }

    static String prepare() {
        var client = MinecraftClient.getInstance();
        var player = GameToolSupport.requirePlayer(client);
        var server = client.getServer();
        if (server == null) throw new IllegalStateException("No integrated singleplayer server; supply an explicit remote bot host/port and account instead");
        if (player.getName().getString().equalsIgnoreCase("Alex")) throw new IllegalStateException("Your current player is already named Alex; a separate Alex cannot join with the same name");
        if (server.getPlayerManager().getPlayerList().stream().anyMatch(p -> p.getName().getString().equalsIgnoreCase("Alex"))) throw new IllegalStateException("Alex is already in this world; disconnect that bot before summoning another");
        boolean opened = !server.isRemote();
        if (opened) {
            int port = NetworkUtils.findLocalPort();
            boolean cheats = server.getSaveProperties().areCommandsAllowed();
            if (port <= 0 || !server.openToLan(client.interactionManager.getCurrentGameMode(), cheats, port)) throw new IllegalStateException("Could not open this world to LAN; check the local listener/firewall and retry explicitly");
        }
        int port = server.getServerPort();
        if (port < 1 || port > 65535) throw new IllegalStateException("Published LAN server did not provide a usable port");
        LOGIN.set(new LoginPermit(server, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)));
        JsonObject connection = new JsonObject();
        connection.addProperty("host", "127.0.0.1"); connection.addProperty("port", port);
        connection.addProperty("username", "Alex"); connection.addProperty("auth", "offline");
        connection.addProperty("version", FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
        JsonObject result = new JsonObject(); result.add("connection", connection);
        result.addProperty("openedLan", opened);
        result.addProperty("gameMode", client.interactionManager.getCurrentGameMode().getId());
        result.addProperty("commandsAllowed", server.getSaveProperties().areCommandsAllowed());
        result.addProperty("onlineMode", server.isOnlineMode());
        return result.toString();
    }

    public static boolean consumeLocalLogin(MinecraftServer server, SocketAddress address, String name) {
        LoginPermit permit = LOGIN.get();
        if (permit == null || permit.server() != server || System.nanoTime() >= permit.expires()
                || !"Alex".equals(name) || !(address instanceof InetSocketAddress socket)
                || socket.getAddress() == null || !socket.getAddress().isLoopbackAddress()) return false;
        return LOGIN.compareAndSet(permit, null);
    }
}
