package io.github.wzhy233.ardion.gui.notification;

import java.awt.Color;

public final class Notification {
    private final String content;
    private final NotificationType type;
    private final Color color;
    private final long beginTime;
    private final long duration;

    public Notification(String content) {
        this(content, 2000L, NotificationType.WARNING);
    }

    public Notification(String content, long duration, NotificationType type) {
        this.content = content;
        this.type = type;
        this.color = colorFor(type);
        this.duration = duration;
        this.beginTime = System.currentTimeMillis();
    }

    public boolean isDone() {
        return duration != -1L && System.currentTimeMillis() >= beginTime + duration;
    }

    public String getContent() {
        return content;
    }

    public NotificationType getType() {
        return type;
    }

    public Color getColor() {
        return color;
    }

    public long getDuration() {
        return duration;
    }

    private static Color colorFor(NotificationType type) {
        return switch (type) {
            case INFO -> new Color(0, 119, 255);
            case SUCCESS -> new Color(44, 253, 37);
            case FAILED -> new Color(255, 0, 0);
            case WARNING -> new Color(255, 196, 0);
        };
    }
}
