package io.github.wzhy233.ardion.value.impl;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.value.base.Value;

import java.util.function.BooleanSupplier;

public final class StringValue extends Value<String> {
    private final BooleanSupplier visibilityCondition;

    public StringValue(String name, Module module, String defaultValue) {
        this(name, module, defaultValue, () -> true);
    }

    public StringValue(String name, Module module, String defaultValue, BooleanSupplier visibilityCondition) {
        super(name, module, defaultValue);
        this.visibilityCondition = visibilityCondition;
    }

    public boolean isVisible() {
        return this.visibilityCondition.getAsBoolean();
    }

    @Override
    protected String normalize(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected String parse(String value) {
        return value;
    }
}