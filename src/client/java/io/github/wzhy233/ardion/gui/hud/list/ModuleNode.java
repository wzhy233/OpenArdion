package io.github.wzhy233.ardion.gui.hud.list;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.hud.HudPaint;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.setting.ThemeSetting;
import io.github.wzhy233.ardion.util.animation.Animation;
import io.github.wzhy233.ardion.util.animation.Easing;
import io.github.wzhy233.ardion.util.input.KeyUtil;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;

import java.awt.*;

public final class ModuleNode {
    private final io.github.wzhy233.ardion.gui.ui.UiState ui = new io.github.wzhy233.ardion.gui.ui.UiState();
    private float opacity;

    public float opacity() { return opacity; }
    private static final int LIST_FONT_SIZE = 12;
    private static final int KEY_FONT_SIZE = 8;
    private static final float ROW_HEIGHT = 14.0F;
    public static final float ROW_RADIUS = 7.0F;
    private static final float PAD_LEFT = 6.0F;
    private static final float PAD_RIGHT_NO_KEY = 6.0F;
    private static final float NAME_SUFFIX_GAP = 4.0F;
    private static final float KEY_GAP = 5.0F;
    private static final float KEY_CIRCLE_RADIUS = 5.0F;
    private static final float KEY_EDGE_MARGIN = (ROW_HEIGHT - KEY_CIRCLE_RADIUS * 2.0F) / 2.0F;

    private final Module module;
    private final Animation animationY = new Animation(Easing.EASE_OUT_EXPO, 500L);
    private final Animation animationX = new Animation(Easing.EASE_OUT_EXPO, 500L);
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean leftAligned;
    private String cachedName = "";
    private boolean cachedHasKey;
    private float cachedWidth = -1.0F;

    public ModuleNode(Module module) {
        this.module = module;
    }

    public Module getModule() {
        return module;
    }

    public float measureWidth() {
        String name = module.getDisplayName();
        boolean hasKey = hasKeyBadge(module);
        if (!name.equals(cachedName) || hasKey != cachedHasKey || cachedWidth < 0.0F) {
            cachedName = name;
            cachedHasKey = hasKey;
            cachedWidth = computeWidth(name, hasKey);
        }
        return cachedWidth;
    }

    public boolean isAnimating() {
        return module.isEnabled() || x > -width && x < 10000.0F;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void update(float targetY, float minimumY) {
        opacity = ui.visible("visibility", module.isEnabled());
        float w = measureWidth();
        width = w;
        height = ROW_HEIGHT;
        leftAligned = HudSetting.moduleListLeft();
        x = (float) animationX.animate(destinationX(w, leftAligned));
        y = Math.max(minimumY, (float) animationY.animate(targetY));
    }

    public void render() {
        if (width <= 0.0F || height <= 0.0F || !Float.isFinite(x + y + width + height)) return;
        try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(Skia.getCanvas(),
                Rect.makeXYWH(x - 16, y - 16, width + 32, height + 32), opacity)) {
        Font font = Fonts.getMedium(LIST_FONT_SIZE);
        String name = module.getDisplayName();
        boolean hasKey = hasKeyBadge(module);

        int bgAlpha = HudSetting.backgroundAlpha();
        if (bgAlpha > 0 && HudSetting.shadow()) Skia.drawShadow(x, y, width, height, ROW_RADIUS);
        HudPaint.renderHudCard(x, y, width, height, ROW_RADIUS, Color.WHITE, bgAlpha);
        Skia.save();
        Skia.clip(x, y, width, height, ROW_RADIUS);

        Rect nameBounds = Skia.getTextBounds(name, font);
        float nameX = x + PAD_LEFT;
        float nameY = y + (height - nameBounds.getHeight()) / 2.0F;
        Color themeColor = new Color(ThemeSetting.getColor((int) (y * 10.0F)), true);
        if (HudSetting.sidebar()) {
            float sidebarX = leftAligned ? x : x + width - 2.0F;
            Skia.drawRect(sidebarX, y, 2.0F, height, new Color(255, 255, 255, 190));
        }

        Paint textPaint = Skia.scratchPaint();
        textPaint.setColor(themeColor.getRGB());
        Skia.drawText(name, nameX, nameY, font, textPaint);

        if (hasKey) {
            float circleCenterX = x + width - KEY_EDGE_MARGIN - KEY_CIRCLE_RADIUS;
            float circleCenterY = y + height / 2.0F;
            Skia.drawCircle(circleCenterX, circleCenterY, KEY_CIRCLE_RADIUS, new Color(255, 255, 255, 45));
            String keyChar = keyBadgeChar(module);
            Font keyFont = Fonts.getMedium(KEY_FONT_SIZE);
            Rect keyBounds = Skia.getTextBounds(keyChar, keyFont);
            Paint keyPaint = Skia.scratchPaint();
            keyPaint.setColor(themeColor.getRGB());
            Skia.drawText(keyChar,
                    circleCenterX - keyBounds.getWidth() / 2.0F,
                    circleCenterY - keyBounds.getHeight() / 2.0F,
                    keyFont, keyPaint);
        }

        Skia.restore();
        }
    }

    private float destinationX(float w, boolean left) {
        if (module.isEnabled()) return left ? 0.0F : -w;
        return left ? -(w + 10.0F) : 10.0F;
    }

    public static float nodeWidth(Module module) {
        return computeWidth(module.getDisplayName(), hasKeyBadge(module));
    }

    private static float computeWidth(String name, boolean hasKey) {
        Font font = Fonts.getMedium(LIST_FONT_SIZE);
        float w = PAD_LEFT + Skia.getTextBounds(name, font).getWidth();
        if (hasKey) {
            w += KEY_GAP + KEY_CIRCLE_RADIUS * 2.0F + KEY_EDGE_MARGIN;
        } else {
            w += PAD_RIGHT_NO_KEY;
        }
        return w;
    }

    private static boolean hasKeyBadge(Module module) {
        return module.hasBind();
    }

    private static String keyBadgeChar(Module module) {
        String name = KeyUtil.getKeyName(module.getBindKey());
        if (name == null || name.isEmpty() || "NONE".equals(name) || "UNKNOWN".equals(name)) return "?";
        return name;
    }

}
