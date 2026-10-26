package io.github.wzhy233.ardion.util.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

public final class ServerUtil {
    private ServerUtil() {
    }

    public static boolean isJoined(Server server) {
        String address = getAddress();
        return !address.isBlank() && address.contains(server.getAddressFragment());
    }

    public static boolean isMultiplayer() {
        return MinecraftClient.getInstance().getCurrentServerEntry() != null;
    }

    public static boolean isSingleplayer() {
        return MinecraftClient.getInstance().isConnectedToLocalServer();
    }

    public static String getAddress() {
        ServerInfo entry = MinecraftClient.getInstance().getCurrentServerEntry();
        return entry == null || entry.address == null ? "" : entry.address.toLowerCase(Locale.ROOT);
    }
}
