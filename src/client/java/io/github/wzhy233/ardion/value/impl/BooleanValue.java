package io.github.wzhy233.ardion.value.impl;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.value.base.Value;

import java.util.function.BooleanSupplier;

public final class BooleanValue extends Value<Boolean> {
    private final BooleanSupplier visibilityCondition;

    public BooleanValue(String name, Module module, boolean defaultValue) {
        this(name, module, defaultValue, () -> true);
    }

    public BooleanValue(String name, Module module, boolean defaultValue, BooleanSupplier visibilityCondition) {
        super(name, module, defaultValue);
        this.visibilityCondition = visibilityCondition;
    }

    public boolean isVisible() {
        return this.visibilityCondition.getAsBoolean();
    }

    @Override
    protected Boolean parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Boolean value cannot be null");
        }

        return switch (value.trim().toLowerCase()) {
            case "true", "on", "1", "yes", "y", "t" -> true;
            case "false", "off", "0", "no", "n", "f" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean: " + value);
        };
    }
}