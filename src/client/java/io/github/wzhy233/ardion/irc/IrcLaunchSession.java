package io.github.wzhy233.ardion.irc;

import net.minecraft.client.session.Session;

public final class IrcLaunchSession {
    private static volatile MinecraftSession session;

    private IrcLaunchSession() {
    }

    public static void capture(Session launchSession) {
        if (session != null || launchSession == null) return;
        synchronized (IrcLaunchSession.class) {
            if (session == null) {
                session = new MinecraftSession(launchSession.getUsername(),
                        launchSession.getUuidOrNull() == null ? null : launchSession.getUuidOrNull().toString());
            }
        }
    }

    public static MinecraftSession get() {
        return session;
    }
}
