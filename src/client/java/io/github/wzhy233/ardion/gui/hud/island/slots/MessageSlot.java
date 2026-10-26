package io.github.wzhy233.ardion.gui.hud.island.slots;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.hud.island.Slot;
import io.github.wzhy233.ardion.gui.notification.Notification;
import io.github.wzhy233.ardion.gui.notification.NotificationType;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Icon;
import io.github.wzhy233.ardion.util.render.Skia;

import java.awt.*;

public final class MessageSlot extends Slot {
    private static final float FONT_SIZE = 18.0F;
    private static final float ICON_SIZE = 32.0F;
    private static final float PADDING_X = 12.0F;
    private static final float HEIGHT = FONT_SIZE;
    private static final float ICON_GAP = 6.0F;

    private final String content;
    private final Color color;
    private final String icon;

    public MessageSlot(Notification notification) {
        super(notification.getDuration());
        this.content = notification.getContent();
        this.color = notification.getColor();
        this.icon = iconFor(notification.getType());
    }

    @Override
    public float getHeight() {
        Font iconFont = Fonts.getIconFill(ICON_SIZE);
        Rect iconBounds = Skia.getTextBounds(icon, iconFont);
        return Math.max(HEIGHT, iconBounds.getHeight());
    }

    @Override
    public float measureWidth() {
        Font font = Fonts.getMedium(FONT_SIZE);
        Font iconFont = Fonts.getIconFill(ICON_SIZE);
        Rect bounds = Skia.getTextBounds(content, font);
        Rect iconBounds = Skia.getTextBounds(icon, iconFont);
        return PADDING_X + iconBounds.getWidth() + ICON_GAP + bounds.getWidth() + PADDING_X;
    }

    @Override
    public void render(float x, float y, float width, float alpha) {
        Font font = Fonts.getMedium(FONT_SIZE);
        Font iconFont = Fonts.getIconFill(ICON_SIZE);
        Rect bounds = Skia.getTextBounds(content, font);
        Rect iconBounds = Skia.getTextBounds(icon, iconFont);
        float slotHeight = Math.max(HEIGHT, iconBounds.getHeight());

        float iconX = x + PADDING_X;
        float iconY = y + (slotHeight - iconBounds.getHeight()) / 2.0F;
        Paint paint = Skia.scratchPaint();
        paint.setColor(color.getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(icon, iconX, iconY, iconFont, paint);

        float textX = iconX + iconBounds.getWidth() + ICON_GAP;
        float textY = y + (slotHeight - bounds.getHeight()) / 2.0F;
        paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(content, textX, textY, font, paint);
    }

    private static String iconFor(NotificationType type) {
        return switch (type) {
            case SUCCESS -> Icon.CHECK_CIRCLE;
            case FAILED -> Icon.ERROR;
            case WARNING -> Icon.WARNING;
            case INFO -> Icon.INFO;
        };
    }
}
