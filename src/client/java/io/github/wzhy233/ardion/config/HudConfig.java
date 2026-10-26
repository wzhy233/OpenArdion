package io.github.wzhy233.ardion.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.setting.HudSetting;
import net.minecraft.client.MinecraftClient;

public final class HudConfig {
    private static final float DEFAULT_XYZ_X = 4.0F;
    private static final float DEFAULT_XYZ_Y = 4.0F;
    private static final float DEFAULT_ARRAY_X = 2.0F;
    private static final float DEFAULT_ARRAY_Y = 17.0F;
    private static final float DEFAULT_INFO_X = 4.0F;
    private static final float DEFAULT_INFO_Y = 4.0F;
    private static final float DEFAULT_TARGET_X = 10.0F;
    private static final float DEFAULT_TARGET_Y = 40.0F;

    private static boolean xyzVisible = true;
    private static float xyzX = DEFAULT_XYZ_X;
    private static float xyzY = DEFAULT_XYZ_Y;
    private static float xyzScale = 1.0F;

    private static boolean arrayListVisible = true;
    private static float arrayListX = DEFAULT_ARRAY_X;
    private static float arrayListY = DEFAULT_ARRAY_Y;
    private static float arrayListScale = 1.0F;
    private static boolean arrayListDefaultPosition = true;
    private static boolean arrayListLeftAligned = false;
    private static int arrayListLastScreenWidth = -1;

    private static boolean infoVisible = true;
    private static float infoX = DEFAULT_INFO_X;
    private static float infoY = DEFAULT_INFO_Y;
    private static float infoScale = 1.0F;

    private static float targetHudX = DEFAULT_TARGET_X;
    private static float targetHudY = DEFAULT_TARGET_Y;
    private static float targetHudScale = 1.0F;
    private static float targetHudBackground = 25.0F;
    private static boolean targetHudHead = true;
    private static boolean targetHudIndicator = true;
    private static boolean targetHudOutline = false;
    private static boolean targetHudShadow = true;

    private HudConfig() {
    }

    public static JsonObject json() {
        JsonObject root = new JsonObject();
        root.add("xyz", element(xyzVisible, xyzX, xyzY, xyzScale));
        JsonObject arrayList = element(arrayListVisible, arrayListX, arrayListY, arrayListScale);
        arrayList.addProperty("default-position", arrayListDefaultPosition);
        arrayList.addProperty("left-aligned", arrayListLeftAligned);
        root.add("arraylist", arrayList);
        root.add("info", element(infoVisible, infoX, infoY, infoScale));

        JsonObject target = element(HudSetting.targetHud(), targetHudX, targetHudY, targetHudScale);
        target.addProperty("background", targetHudBackground);
        target.addProperty("head", targetHudHead);
        target.addProperty("indicator", targetHudIndicator);
        target.addProperty("outline", targetHudOutline);
        target.addProperty("shadow", targetHudShadow);
        root.add("target-hud", target);
        return root;
    }

    public static void apply(JsonObject root) {
        if (root == null) return;
        applyXyz(root.getAsJsonObject("xyz"));
        applyArrayList(root.getAsJsonObject("arraylist"));
        applyInfo(root.getAsJsonObject("info"));
        applyTargetHud(root.getAsJsonObject("target-hud"));
    }

    public static void applyLegacy(JsonObject modules) {
        if (modules == null) return;
        JsonObject hudValues = values(modules, "hud");
        if (hudValues != null) {
            xyzVisible = bool(hudValues, "show-xyz", xyzVisible);
            setXyz(number(hudValues, "xyz-x", xyzX), number(hudValues, "xyz-y", xyzY), number(hudValues, "xyz-scale", xyzScale));
            arrayListVisible = bool(hudValues, "show-arraylist", arrayListVisible);
            float legacyArrayX = number(hudValues, "arraylist-x", arrayListX);
            float legacyArrayY = number(hudValues, "arraylist-y", arrayListY);
            float legacyArrayScale = number(hudValues, "arraylist-scale", arrayListScale);
            applyArrayListPosition(legacyArrayX, legacyArrayY, legacyArrayScale,
                    inferArrayListLeftAligned(legacyArrayX),
                    isDefaultArrayPosition(legacyArrayX, legacyArrayY, legacyArrayScale));
            infoVisible = bool(hudValues, "show-info", infoVisible);
            setInfo(number(hudValues, "info-x", infoX), number(hudValues, "info-y", infoY), number(hudValues, "info-scale", infoScale));
        }

        JsonObject triggerValues = values(modules, "trigger-bot");
        if (triggerValues != null) {
            HudSetting.setTargetHud(bool(triggerValues, "target-hud", HudSetting.targetHud()));
            string(triggerValues, "health-source").ifPresent(GlobalSetting::setHealthSource);
            setTargetHud(number(triggerValues, "target-hud-x", targetHudX), number(triggerValues, "target-hud-y", targetHudY), number(triggerValues, "target-hud-scale", targetHudScale));
            targetHudBackground = clamp(number(triggerValues, "target-hud-background", targetHudBackground), 0.0F, 100.0F);
            targetHudHead = bool(triggerValues, "target-hud-head", targetHudHead);
            targetHudIndicator = bool(triggerValues, "target-hud-indicator", targetHudIndicator);
            targetHudOutline = bool(triggerValues, "target-hud-outline", targetHudOutline);
            targetHudShadow = bool(triggerValues, "target-hud-shadow", targetHudShadow);
        }
    }

    public static boolean isXyzVisible() {
        return xyzVisible;
    }

    public static void setXyzVisible(boolean visible) {
        xyzVisible = visible;
    }

    public static void setXyz(float x, float y, float scale) {
        xyzX = clamp(x, 0.0F, 10000.0F);
        xyzY = clamp(y, 0.0F, 10000.0F);
        xyzScale = clamp(scale, 0.5F, 3.0F);
    }

    public static void resetXyz() {
        setXyz(DEFAULT_XYZ_X, DEFAULT_XYZ_Y, 1.0F);
        xyzVisible = true;
    }

    public static float xyzX() {
        return xyzX;
    }

    public static float xyzY() {
        return xyzY;
    }

    public static float xyzScale() {
        return xyzScale;
    }

    public static boolean isArrayListVisible() {
        return arrayListVisible;
    }

    public static void setArrayListVisible(boolean visible) {
        arrayListVisible = visible;
    }

    public static void setArrayList(float x, float y, float scale) {
        applyArrayListPosition(x, y, scale, arrayListLeftAligned, false);
    }

    public static void setArrayList(float x, float y, float scale, boolean leftAligned) {
        applyArrayListPosition(x, y, scale, leftAligned, false);
    }

    public static void resetArrayList() {
        applyArrayListPosition(DEFAULT_ARRAY_X, DEFAULT_ARRAY_Y, 1.0F, false, true);
        arrayListVisible = true;
    }

    public static void constrainArrayListToScreen(int screenWidth) {
        if (screenWidth <= 0) return;
        float maxX = Math.max(DEFAULT_ARRAY_X, screenWidth - DEFAULT_ARRAY_X);
        if (arrayListDefaultPosition) {
            arrayListX = maxX;
            arrayListLeftAligned = false;
            arrayListLastScreenWidth = screenWidth;
            return;
        }
        if (!arrayListLeftAligned && arrayListLastScreenWidth > 0 && arrayListLastScreenWidth != screenWidth) {
            arrayListX += screenWidth - arrayListLastScreenWidth;
        }
        arrayListX = clamp(arrayListX, DEFAULT_ARRAY_X, maxX);
        arrayListLastScreenWidth = screenWidth;
    }

    public static float arrayListX() {
        return arrayListX;
    }

    public static float arrayListY() {
        return arrayListY;
    }

    public static float arrayListScale() {
        return arrayListScale;
    }

    public static boolean arrayListLeftAligned() {
        return arrayListLeftAligned;
    }

    public static boolean isInfoVisible() {
        return infoVisible;
    }

    public static void setInfoVisible(boolean visible) {
        infoVisible = visible;
    }

    public static void setInfo(float xFromRight, float yFromBottom, float scale) {
        infoX = clamp(xFromRight, 0.0F, 10000.0F);
        infoY = clamp(yFromBottom, 0.0F, 10000.0F);
        infoScale = clamp(scale, 0.5F, 3.0F);
    }

    public static void resetInfo() {
        setInfo(DEFAULT_INFO_X, DEFAULT_INFO_Y, 1.0F);
        infoVisible = true;
    }

    public static float infoX() {
        return infoX;
    }

    public static float infoY() {
        return infoY;
    }

    public static float infoScale() {
        return infoScale;
    }

    public static boolean isTargetHudVisible() {
        return HudSetting.targetHud();
    }

    public static void setTargetHudVisible(boolean visible) {
        HudSetting.setTargetHud(visible);
    }

    public static void setTargetHud(float xFromCenter, float yFromCenter, float scale) {
        targetHudX = clamp(xFromCenter, -1000.0F, 1000.0F);
        targetHudY = clamp(yFromCenter, -1000.0F, 1000.0F);
        targetHudScale = clamp(scale, 0.5F, 2.0F);
    }

    public static void resetTargetHud() {
        setTargetHud(DEFAULT_TARGET_X, DEFAULT_TARGET_Y, 1.0F);
        HudSetting.setTargetHud(true);
        targetHudBackground = 25.0F;
        targetHudHead = true;
        targetHudIndicator = true;
        targetHudOutline = false;
        targetHudShadow = true;
    }

    public static float targetHudX() {
        return targetHudX;
    }

    public static float targetHudY() {
        return targetHudY;
    }

    public static float targetHudScale() {
        return targetHudScale;
    }

    public static float targetHudBackground() {
        return targetHudBackground;
    }

    public static boolean targetHudHead() {
        return targetHudHead;
    }

    public static boolean targetHudIndicator() {
        return targetHudIndicator;
    }

    public static boolean targetHudOutline() {
        return targetHudOutline;
    }

    public static boolean targetHudShadow() {
        return targetHudShadow;
    }

    private static JsonObject element(boolean visible, float x, float y, float scale) {
        JsonObject element = new JsonObject();
        element.addProperty("visible", visible);
        element.addProperty("x", x);
        element.addProperty("y", y);
        element.addProperty("scale", scale);
        return element;
    }

    private static void applyXyz(JsonObject element) {
        if (element == null) return;
        xyzVisible = bool(element, "visible", xyzVisible);
        setXyz(number(element, "x", xyzX), number(element, "y", xyzY), number(element, "scale", xyzScale));
    }

    private static void applyArrayList(JsonObject element) {
        if (element == null) return;
        arrayListVisible = bool(element, "visible", arrayListVisible);
        float x = number(element, "x", arrayListX);
        float y = number(element, "y", arrayListY);
        float scale = number(element, "scale", arrayListScale);
        boolean defaultPosition = bool(element, "default-position", isDefaultArrayPosition(x, y, scale));
        boolean leftAligned = element.has("left-aligned")
                ? bool(element, "left-aligned", false)
                : inferArrayListLeftAligned(x);
        applyArrayListPosition(x, y, scale, leftAligned, defaultPosition);
    }

    private static void applyInfo(JsonObject element) {
        if (element == null) return;
        infoVisible = bool(element, "visible", infoVisible);
        setInfo(number(element, "x", infoX), number(element, "y", infoY), number(element, "scale", infoScale));
    }

    private static void applyTargetHud(JsonObject element) {
        if (element == null) return;
        HudSetting.setTargetHud(bool(element, "visible", HudSetting.targetHud()));
        setTargetHud(number(element, "x", targetHudX), number(element, "y", targetHudY), number(element, "scale", targetHudScale));
        targetHudBackground = clamp(number(element, "background", targetHudBackground), 0.0F, 100.0F);
        targetHudHead = bool(element, "head", targetHudHead);
        targetHudIndicator = bool(element, "indicator", targetHudIndicator);
        targetHudOutline = bool(element, "outline", targetHudOutline);
        targetHudShadow = bool(element, "shadow", targetHudShadow);
    }

    private static JsonObject values(JsonObject modules, String moduleName) {
        JsonObject module = modules.getAsJsonObject(moduleName);
        return module == null ? null : module.getAsJsonObject("values");
    }

    private static boolean bool(JsonObject object, String name, boolean fallback) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    private static float number(JsonObject object, String name, float fallback) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static java.util.Optional<String> string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? java.util.Optional.empty() : java.util.Optional.of(element.getAsString());
    }

    private static void applyArrayListPosition(float x, float y, float scale, boolean leftAligned, boolean defaultPosition) {
        arrayListX = clamp(x, 0.0F, 10000.0F);
        arrayListY = clamp(y, 0.0F, 10000.0F);
        arrayListScale = clamp(scale, 0.5F, 3.0F);
        arrayListLeftAligned = leftAligned;
        arrayListDefaultPosition = defaultPosition;
    }

    private static boolean inferArrayListLeftAligned(float x) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            return x <= client.getWindow().getScaledWidth() / 2.0F;
        }
        return arrayListLeftAligned;
    }

    private static boolean isDefaultArrayPosition(float x, float y, float scale) {
        return Math.abs(x - DEFAULT_ARRAY_X) < 0.001F
                && Math.abs(y - DEFAULT_ARRAY_Y) < 0.001F
                && Math.abs(scale - 1.0F) < 0.001F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
