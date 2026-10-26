package io.github.wzhy233.ardion.setting;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.ColorValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;

import java.awt.*;
import java.util.List;

public final class ThemeSetting {
    private static final SettingOwner OWNER = new SettingOwner();
    private static final ModeValue colorMode = new ModeValue("color-mode", OWNER, "custom-fade",
            "white", "red", "blue", "vape", "custom-static", "custom-fade", "custom-3-colors", "rainbow");
    private static final ColorValue color1 = new ColorValue("color-1", OWNER,
            new Color(210, 80, 105).getRGB(), () -> colorMode.getValue().startsWith("custom"));
    private static final ColorValue color2 = new ColorValue("color-2", OWNER,
            new Color(135, 190, 255).getRGB(), () -> colorMode.is("custom-fade") || colorMode.is("custom-3-colors"));
    private static final ColorValue color3 = new ColorValue("color-3", OWNER,
            new Color(0, 255, 255).getRGB(), () -> colorMode.is("custom-3-colors"));
    private static final NumberValue saturation = new NumberValue("rainbow-saturation", OWNER,
            0.9D, 0.05D, 1.0D, () -> colorMode.is("rainbow"));
    private static final NumberValue brightness = new NumberValue("rainbow-brightness", OWNER,
            0.9D, 0.05D, 1.0D, () -> colorMode.is("rainbow"));
    private static final List<Value<?>> values = List.of(colorMode, color1, color2, color3, saturation, brightness);

    private ThemeSetting() {
    }

    public static List<Value<?>> getValues() {
        return values;
    }

    public static int getColor(int offset) {
        return switch (colorMode.getValue()) {
            case "white" -> -1;
            case "red" -> fade(new Color(239, 76, 76), new Color(202, 0, 0), 2500L, offset);
            case "blue" -> fade(new Color(5, 138, 255), new Color(0, 35, 206), 2500L, offset);
            case "vape" -> new Color(5, 134, 105).getRGB();
            case "custom-static" -> color1.getColor();
            case "custom-fade" -> fade(color1.getValue(), color2.getValue(), 2500L, offset);
            case "custom-3-colors" -> fade(color1.getValue(), color2.getValue(), color3.getValue(), 3000L, offset);
            case "rainbow" -> rainbow(4500L, (int) (offset * 0.65F), saturation.getValue().floatValue(), brightness.getValue().floatValue());
            default -> -1;
        };
    }

    public static boolean isVisible(Value<?> value) {
        if (value instanceof ColorValue colorValue) return colorValue.isVisible();
        if (value instanceof ModeValue modeValue) return modeValue.isVisible();
        if (value instanceof NumberValue numberValue) return numberValue.isVisible();
        return true;
    }

    private static int fade(Color color1, Color color2, long ms, int offset) {
        double scale = (((System.currentTimeMillis() + offset) % ms) / (double) ms) * 2.0D;
        double finalScale = scale > 1.0D ? 2.0D - scale : scale;
        return gradient(color1, color2, finalScale).getRGB();
    }

    private static int fade(Color color1, Color color2, Color color3, long ms, int offset) {
        double scale = (((System.currentTimeMillis() + offset) % ms) / (double) ms) * 3.0D;
        if (scale > 2.0D) return gradient(color3, color1, scale - 2.0D).getRGB();
        if (scale > 1.0D) return gradient(color2, color3, scale - 1.0D).getRGB();
        return gradient(color1, color2, scale).getRGB();
    }

    private static Color gradient(Color color1, Color color2, double scale) {
        scale = Math.max(0.0D, Math.min(1.0D, scale));
        return new Color(
                (int) (color1.getRed() + (color2.getRed() - color1.getRed()) * scale),
                (int) (color1.getGreen() + (color2.getGreen() - color1.getGreen()) * scale),
                (int) (color1.getBlue() + (color2.getBlue() - color1.getBlue()) * scale),
                (int) (color1.getAlpha() + (color2.getAlpha() - color1.getAlpha()) * scale)
        );
    }

    private static int rainbow(long ms, int offset, float saturation, float brightness) {
        float scale = ((System.currentTimeMillis() + offset) % ms) / (float) ms;
        return Color.HSBtoRGB(scale, saturation, brightness);
    }

    private static final class SettingOwner extends Module {
        private SettingOwner() {
            super("theme", ModuleCategory.CLIENT);
        }
    }
}
