package io.github.wzhy233.ardion.gui.hud.island.slots;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.hud.island.Slot;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Icon;
import io.github.wzhy233.ardion.util.render.Skia;

import java.awt.*;

public final class ModuleToggleSlot extends Slot {
    private static final float TITLE_SIZE = 18.0F;
    private static final float SUBTITLE_SIZE = 12.0F;
    private static final float LINE_GAP = 3.0F;
    private static final float PADDING_X = 14.0F;
    private static final float ICON_SIZE = 32.0F;
    private static final float ICON_GAP = 6.0F;
    private static final Color ENABLED_COLOR = new Color(44, 253, 37);
    private static final Color DISABLED_COLOR = new Color(255, 90, 90);
    private static final String TITLE_TEXT = "Module Toggled";

    private String moduleName;
    private boolean enabled;

    public ModuleToggleSlot(String moduleName, boolean enabled, long duration) {
        super(duration);
        this.moduleName = moduleName;
        this.enabled = enabled;
    }

    public String getModuleName() {
        return moduleName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void replaceWith(ModuleToggleSlot replacement, long now) {
        this.moduleName = replacement.moduleName;
        this.enabled = replacement.enabled;
        restart(now);
    }

    public boolean matchesModule(String moduleName) {
        return this.moduleName != null && this.moduleName.equalsIgnoreCase(moduleName);
    }

    private String subtitle() {
        return moduleName + " " + (enabled ? "Enabled" : "Disabled");
    }

    @Override
    public float getHeight() {
        Font titleFont = Fonts.getMedium(TITLE_SIZE);
        Font subFont = Fonts.getMedium(SUBTITLE_SIZE);
        Font iconFont = Fonts.getIconFill(ICON_SIZE);
        Rect title = Skia.getTextBounds(TITLE_TEXT, titleFont);
        Rect sub = Skia.getTextBounds(subtitle(), subFont);
        Rect icon = Skia.getTextBounds(icon(), iconFont);
        return Math.max(title.getHeight() + LINE_GAP + sub.getHeight(), icon.getHeight());
    }

    @Override
    public float measureWidth() {
        Font titleFont = Fonts.getMedium(TITLE_SIZE);
        Font subFont = Fonts.getMedium(SUBTITLE_SIZE);
        Font iconFont = Fonts.getIconFill(ICON_SIZE);
        Rect title = Skia.getTextBounds(TITLE_TEXT, titleFont);
        Rect sub = Skia.getTextBounds(subtitle(), subFont);
        Rect icon = Skia.getTextBounds(icon(), iconFont);
        return PADDING_X + icon.getWidth() + ICON_GAP + Math.max(title.getWidth(), sub.getWidth()) + PADDING_X;
    }

    @Override
    public void render(float x, float y, float width, float alpha) {
        Font titleFont = Fonts.getMedium(TITLE_SIZE);
        Font subFont = Fonts.getMedium(SUBTITLE_SIZE);
        Font iconFont = Fonts.getIconFill(ICON_SIZE);
        Rect title = Skia.getTextBounds(TITLE_TEXT, titleFont);
        String subtitle = subtitle();
        Rect sub = Skia.getTextBounds(subtitle, subFont);
        String icon = icon();
        Rect iconBounds = Skia.getTextBounds(icon, iconFont);

        float textBlockHeight = title.getHeight() + LINE_GAP + sub.getHeight();
        float slotHeight = Math.max(textBlockHeight, iconBounds.getHeight());
        float iconX = x + PADDING_X;
        float iconY = y + (slotHeight - iconBounds.getHeight()) / 2.0F;
        float textX = iconX + iconBounds.getWidth() + ICON_GAP;
        float titleY = y + (slotHeight - textBlockHeight) / 2.0F;
        float subY = titleY + title.getHeight() + LINE_GAP;
        Color stateColor = enabled ? ENABLED_COLOR : DISABLED_COLOR;

        Paint paint = Skia.scratchPaint();
        paint.setColor(stateColor.getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(icon, iconX, iconY, iconFont, paint);

        paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(TITLE_TEXT, textX, titleY, titleFont, paint);

        paint = Skia.scratchPaint();
        paint.setColor(stateColor.getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(subtitle, textX, subY, subFont, paint);
    }

    private String icon() {
        return enabled ? Icon.EXTENSION : Icon.EXTENSION_OFF;
    }
}
