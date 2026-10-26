package io.github.wzhy233.ardion.util.client;

import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.manager.ModuleManager;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URL;

public final class SpooferProtectionUtil {
    private static final ThreadLocal<Boolean> DECODING_REMOTE_PACKET = ThreadLocal.withInitial(() -> false);

    private SpooferProtectionUtil() {
    }

    public static boolean isEnabled() {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        return spoofer != null && spoofer.isEnabled();
    }

    public static boolean protectsTranslationKeys() {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        return spoofer != null && spoofer.isEnabled() && spoofer.protectsTranslationKeys();
    }

    public static boolean protectsLocalHttp() {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        return spoofer != null && spoofer.isEnabled() && spoofer.protectsLocalHttp();
    }

    public static boolean protectsFingerprinting() {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);
        return spoofer != null && spoofer.isEnabled() && spoofer.protectsFingerprinting();
    }

    public static boolean isDecodingRemotePacket() {
        return DECODING_REMOTE_PACKET.get();
    }

    public static void setDecodingRemotePacket(boolean value) {
        DECODING_REMOTE_PACKET.set(value);
    }

    public static boolean isLocalUrl(URL url) {
        if (url == null) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()) {
                    return true;
                }
            }
        } catch (UnknownHostException ignored) {
        }
        return false;
    }
}
