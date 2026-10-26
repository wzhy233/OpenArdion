package io.github.wzhy233.ardion.value.impl;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.value.base.Value;

import java.math.BigDecimal;
import java.util.function.BooleanSupplier;

public final class NumberValue extends Value<Double> {
    private final double min;
    private final double max;
    private final boolean integer;
    private final BooleanSupplier visibilityCondition;

    public NumberValue(String name, Module module, double defaultValue, double min, double max) {
        this(name, module, defaultValue, min, max, false, () -> true);
    }

    public NumberValue(String name, Module module, double defaultValue, double min, double max, boolean integer) {
        this(name, module, defaultValue, min, max, integer, () -> true);
    }

    public NumberValue(String name, Module module, double defaultValue, double min, double max,
                       BooleanSupplier visibilityCondition) {
        this(name, module, defaultValue, min, max, false, visibilityCondition);
    }

    public NumberValue(String name, Module module, double defaultValue, double min, double max,
                       boolean integer, BooleanSupplier visibilityCondition) {
        super(name, module, defaultValue);
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.visibilityCondition = visibilityCondition;
        this.setValue(defaultValue);
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public boolean isInteger() {
        return this.integer;
    }

    public boolean isVisible() {
        return this.visibilityCondition.getAsBoolean();
    }

    @Override
    public String getValueAsString() {
        if (this.integer) {
            return Integer.toString(this.getValue().intValue());
        }
        return BigDecimal.valueOf(this.getValue()).stripTrailingZeros().toPlainString();
    }

    @Override
    protected Double normalize(Double value) {
        if (value == null) {
            throw new IllegalArgumentException("Number value cannot be null");
        }
        double normalized = Math.max(this.min, Math.min(this.max, value));
        if (this.integer) {
            normalized = Math.rint(normalized);
        }
        return normalized;
    }

    @Override
    protected Double parse(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (this.integer && parsed != Math.rint(parsed)) {
                throw new IllegalArgumentException("Invalid integer: " + value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number: " + value, exception);
        }
    }
}