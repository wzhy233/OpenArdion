package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.Font;

public final class Fonts {
    private static final String JB_MEDIUM = "JetBrainsMono-Medium.ttf";
    private static final String JB_SEMIBOLD = "JetBrainsMono-SemiBold.ttf";
    private static final String REGULAR = "MiSans-Regular.ttf";
    private static final String MEDIUM = "MiSans-Medium.ttf";
    private static final String ICON_FILL = "MaterialSymbolsRounded_Fill.ttf";
    private static final String ICON = "MaterialSymbolsRounded.ttf";
    private static final String ICON_FLUX = "fluxicon.ttf";
    private static final String ICON_MOON = "ico-moon.ttf";
    private static final String SEGOE_UI = "seguiemj.ttf";

    private Fonts() {
    }

    public static void loadAll() {
        FontHelper.preloadFonts(JB_MEDIUM, JB_SEMIBOLD, REGULAR, MEDIUM, ICON_FILL, ICON);
    }

    public static Font getJbMedium(float size) {
        return FontHelper.load(JB_MEDIUM, size);
    }

    public static Font getJbSemibold(float size) {
        return FontHelper.load(JB_SEMIBOLD, size);
    }

    public static Font getRegular(float size) {
        return FontHelper.load(REGULAR, size);
    }

    public static Font getMedium(float size) {
        return FontHelper.load(MEDIUM, size);
    }

    public static Font getIcon(float size) {
        return FontHelper.load(ICON, size);
    }

    public static Font getIconFill(float size) {
        return FontHelper.load(ICON_FILL, size);
    }

    public static Font getIconFlux(float size) {
        return FontHelper.load(ICON_FLUX, size);
    }

    public static Font getIconMoon(float size) {
        return FontHelper.load(ICON_MOON, size);
    }

    public static Font getSegoeUI(float size) {
        return FontHelper.load(SEGOE_UI, size);
    }

    public static Font getWeighted(float size, float weight) {
        return weight >= 0.5F ? getMedium(size) : getRegular(size);
    }
}
