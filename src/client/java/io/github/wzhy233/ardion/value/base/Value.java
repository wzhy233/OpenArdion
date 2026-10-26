package io.github.wzhy233.ardion.value.base;

import io.github.wzhy233.ardion.module.base.Module;

public abstract class Value<T> {
    private final String name;
    private final Module module;
    private final T defaultValue;
    private T value;

    protected Value(String name, Module module, T defaultValue) {
        this.name = name;
        this.module = module;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public final String getName() {
        return this.name;
    }

    public final Module getModule() {
        return this.module;
    }

    public final T getDefaultValue() {
        return this.defaultValue;
    }

    public final T getValue() {
        return this.value;
    }

    public final void setValue(T value) {
        this.value = this.normalize(value);
    }

    public final void reset() {
        this.value = this.defaultValue;
    }

    public final void setValueFromString(String value) {
        this.setValue(this.parse(value));
    }

    public String getValueAsString() {
        return String.valueOf(this.value);
    }

    protected T normalize(T value) {
        return value;
    }

    protected abstract T parse(String value);
}
