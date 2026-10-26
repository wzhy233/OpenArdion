package io.github.wzhy233.ardion.gui.ui;

import java.util.HashMap;
import java.util.Map;

public final class UiState {
    private final Map<String, Channel> channels = new HashMap<>();
    private long lastPrune;

    public float value(String key, float target, long durationMillis) {
        long now = System.nanoTime();
        if (now - lastPrune > 10_000_000_000L) {
            channels.values().removeIf(channel -> now - channel.used > 10_000_000_000L);
            lastPrune = now;
        }
        Channel channel = channels.computeIfAbsent(key, ignored -> new Channel());
        channel.used = now;
        return channel.transition.to(target, now, durationMillis * 1_000_000L);
    }

    public float visible(String key, boolean visible) {
        return value(key, visible ? 1 : 0, visible ? UiManager.ENTER_MS : UiManager.EXIT_MS);
    }

    public float hover(String key, boolean hovered) {
        return value("hover:" + key, hovered ? 1 : 0, UiManager.INTERACTION_MS);
    }

    public void reset() { channels.clear(); }

    private static final class Channel {
        private final UiTransition transition = new UiTransition(0);
        private long used;
    }
}
