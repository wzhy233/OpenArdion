package io.github.wzhy233.ardion.setting;

import io.github.wzhy233.ardion.config.HudConfig;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;

import java.util.List;

public final class HudSetting {
    private static final SettingOwner OWNER = new SettingOwner();
    private static final BooleanValue dynamicIsland = new BooleanValue("dynamic-island", OWNER, true);
    private static final BooleanValue moduleList = new BooleanValue("module-list", OWNER, true);
    private static final BooleanValue renderInGui = new BooleanValue("render-in-gui", OWNER, true);
    private static final NumberValue backgroundAlpha = new NumberValue("background-alpha", OWNER,
            20.0D, 0.0D, 100.0D, true);
    private static final BooleanValue sidebar = new BooleanValue("list-sidebar", OWNER, false);
    private static final BooleanValue notifications = new BooleanValue("notifications", OWNER, true);
    private static final BooleanValue moduleToggleSound = new BooleanValue("module-toggle-sound", OWNER, true);
    private static final BooleanValue islandTab = new BooleanValue("island-tab", OWNER, true, () -> dynamicIsland.getValue());
    private static final BooleanValue targetHud = new BooleanValue("target-hud", OWNER, true);
    private static final BooleanValue shadow = new BooleanValue("shadow", OWNER, true);
    private static final BooleanValue shaderGradient = new BooleanValue("shader-gradient", OWNER, true);
    private static final ModeValue gradientType = new ModeValue("gradient-type", OWNER, "linear-degree", () -> shaderGradient.getValue(),
            "horizontal", "vertical", "diagonal", "linear-degree", "radial");
    private static final NumberValue gradientAngle = new NumberValue("gradient-angle", OWNER, 30.0D, 0.0D, 360.0D, true,
            () -> shaderGradient.getValue() && gradientType.is("linear-degree"));
    private static final BooleanValue glow = new BooleanValue("glow", OWNER, true);
    private static final NumberValue glowSize = new NumberValue("glow-size", OWNER, 2.0D, 1.0D, 5.0D, () -> glow.getValue());
    private static final NumberValue glowIntensity = new NumberValue("glow-intensity", OWNER, 0.8D, 0.1D, 1.0D, () -> glow.getValue());
    private static final ModeValue blurBackend = new ModeValue("blur-backend", OWNER, "native", "native", "sksl", "off");
    private static final ModeValue skslShader = new ModeValue("sksl-shader", OWNER, "gaussian-blur",
            () -> blurBackend.is("sksl"), "gaussian-blur", "bloom", "threshold");
    private static final NumberValue blurSigma = new NumberValue("blur-sigma", OWNER, 14.0D, 1.0D, 30.0D, () -> !blurBackend.is("off"));
    private static final List<Value<?>> values = List.of(dynamicIsland, moduleList, renderInGui, backgroundAlpha,
            sidebar,
            notifications, moduleToggleSound, islandTab, targetHud, shadow, shaderGradient, gradientType, gradientAngle, glow, glowSize, glowIntensity,
            blurBackend, skslShader, blurSigma);

    private HudSetting() {
    }

    public static List<Value<?>> getValues() {
        return values;
    }

    public static boolean dynamicIsland() {
        return dynamicIsland.getValue();
    }

    public static boolean watermark() {
        return dynamicIsland();
    }

    public static boolean moduleList() {
        return moduleList.getValue();
    }

    public static boolean renderInGui() {
        return renderInGui.getValue();
    }

    public static boolean background() {
        return backgroundAlpha() > 0;
    }

    public static int backgroundAlpha() {
        return alpha(backgroundAlpha);
    }

    public static boolean sidebar() {
        return sidebar.getValue();
    }

    public static boolean moduleListLeft() {
        return HudConfig.arrayListLeftAligned();
    }

    public static boolean notifications() {
        return notifications.getValue();
    }

    public static boolean moduleToggleSound() {
        return moduleToggleSound.getValue();
    }

    public static boolean islandTab() {
        return islandTab.getValue();
    }

    public static boolean targetHud() {
        return targetHud.getValue();
    }

    public static void setTargetHud(boolean visible) {
        targetHud.setValue(visible);
    }

    public static String healthSource() {
        return GlobalSetting.targetHudHealthSource();
    }

    public static void setHealthSource(String source) {
        GlobalSetting.setHealthSource(source);
    }

    public static boolean shadow() {
        return shadow.getValue();
    }

    public static boolean shaderGradient() {
        return shaderGradient.getValue();
    }

    public static String gradientType() {
        return gradientType.getValue();
    }

    public static float gradientAngle() {
        return gradientAngle.getValue().floatValue();
    }

    public static boolean glow() {
        return glow.getValue();
    }

    public static float glowSize() {
        return glowSize.getValue().floatValue();
    }

    public static float glowIntensity() {
        return glowIntensity.getValue().floatValue();
    }

    public static boolean blur() {
        return !blurBackend.is("off");
    }

    public static boolean blurSksl() {
        return blurBackend.is("sksl");
    }

    public static String skslShader() {
        return skslShader.getValue();
    }

    public static float blurSigma() {
        return blurSigma.getValue().floatValue();
    }

    public static boolean isVisible(Value<?> value) {
        if (value instanceof BooleanValue booleanValue) return booleanValue.isVisible();
        if (value instanceof ModeValue modeValue) return modeValue.isVisible();
        if (value instanceof NumberValue numberValue) return numberValue.isVisible();
        return true;
    }

    private static int alpha(NumberValue value) {
        return Math.max(0, Math.min(255, Math.round(value.getValue().floatValue() / 100.0F * 255.0F)));
    }

    private static final class SettingOwner extends Module {
        private SettingOwner() {
            super("hud-settings", ModuleCategory.CLIENT);
        }
    }
}
