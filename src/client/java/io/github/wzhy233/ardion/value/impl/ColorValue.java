package io.github.wzhy233.ardion.value.impl;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.value.base.Value;

import java.awt.Color;
import java.util.function.BooleanSupplier;

public final class ColorValue extends Value<Color> {
    private final BooleanSupplier visibilityCondition;
    private float hue;
    private float saturation;
    private float brightness;
    private float alpha;

    public ColorValue(String name, Module module, int color) {
        this(name, module, color, () -> true);
    }

    public ColorValue(String name, Module module, int color, BooleanSupplier visibilityCondition) {
        super(name, module, intToColor(color));
        this.visibilityCondition = visibilityCondition;
        initializeColor(getValue());
    }

    public static Color intToColor(int color) {
        Color source = new Color(color);
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), color >> 24 & 255);
    }

    public int getColor() {
        return getValue().getRGB();
    }

    public boolean isVisible() {
        return visibilityCondition.getAsBoolean();
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setHue(float hue) {
        this.hue = clamp01(hue);
        calculate();
    }

    public void setSaturation(float saturation) {
        this.saturation = clamp01(saturation);
        calculate();
    }

    public void setBrightness(float brightness) {
        this.brightness = clamp01(brightness);
        calculate();
    }

    public void setAlpha(float alpha) {
        this.alpha = clamp01(alpha);
        calculate();
    }

    @Override
    public String getValueAsString() {
        return Integer.toString(getColor());
    }

    @Override
    protected Color normalize(Color value) {
        if (value == null) throw new IllegalArgumentException("Color value cannot be null");
        initializeColor(value);
        return value;
    }

    @Override
    protected Color parse(String value) {
        try {
            return intToColor(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid color: " + value, exception);
        }
    }

    private void initializeColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = color.getAlpha() / 255.0F;
    }

    private void calculate() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        super.setValue(intToColor((rgb & 0x00FFFFFF) | ((int) (alpha * 255.0F) << 24)));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
