package io.github.wzhy233.ardion.util.client;

import io.github.wzhy233.ardion.gui.notification.Notification;
import io.github.wzhy233.ardion.gui.notification.NotificationType;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.util.text.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class OutputUtil {
    private static int notificationSuppressionDepth;

    private OutputUtil() {
    }

    public static void send(String message) {
        sendMultiline(message, Formatting.WHITE);
    }

    public static void info(String message) {
        notify(message, NotificationType.INFO);
        sendMultiline(message, Formatting.GRAY);
    }

    public static void success(String message) {
        notify(message, NotificationType.SUCCESS);
        sendMultiline(message, Formatting.GREEN);
    }

    public static void infoChatOnly(String message) {
        sendMultiline(message, Formatting.GRAY);
    }

    public static void warn(String message) {
        notify(message, NotificationType.WARNING);
        sendMultiline(message, Formatting.YELLOW);
    }

    public static void error(String message) {
        notify(message, NotificationType.FAILED);
        sendMultiline(message, Formatting.RED);
    }

    public static void infoLines(List<Text> lines) {
        sendLines(lines, Formatting.GRAY);
    }

    public static Text bullet() {
        return Text.literal("▸ ").formatted(Formatting.WHITE);
    }

    public static void withNotificationsSuppressed(Runnable action) {
        notificationSuppressionDepth++;
        try {
            action.run();
        } finally {
            notificationSuppressionDepth--;
        }
    }

    public static boolean areNotificationsSuppressed() {
        return notificationSuppressionDepth > 0;
    }

    public static void send(Text message) {
        send(message, true);
    }

    public static void send(Text message, boolean prefixed) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null || !GlobalSetting.getClientOutput().getValue()) {
            return;
        }

        ChatHud chatHud = client.inGameHud.getChatHud();
        MutableText line = Text.empty();
        if (prefixed) {
            line.append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal("A").formatted(Formatting.AQUA))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
        }
        chatHud.addMessage(line.append(message));
    }

    private static void sendMultiline(String message, Formatting color) {
        String[] lines = splitLines(message);
        for (int i = 0; i < lines.length; i++) {
            send(coloredMessage(color, lines[i]), i == 0);
        }
    }

    private static void notify(String message, NotificationType type) {
        if (areNotificationsSuppressed()) {
            return;
        }
        HUD.postNotification(new Notification(TextUtil.safe(message), 2000L, type));
    }

    private static void sendLines(List<Text> lines, Formatting fallbackColor) {
        for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            send(line.copy().formatted(fallbackColor), i == 0);
        }
    }

    private static String[] splitLines(String message) {
        return TextUtil.safe(message).split("\\R", -1);
    }

    private static Text coloredMessage(Formatting color, String message) {
        MutableText content = Text.literal(message).formatted(color);
        return Text.empty().append(content);
    }
}
