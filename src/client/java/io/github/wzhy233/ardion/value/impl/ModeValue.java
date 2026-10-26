package io.github.wzhy233.ardion.value.impl;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.value.base.Value;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class ModeValue extends Value<String> {
    private final Set<String> modes;
    private final BooleanSupplier visibilityCondition;

    public ModeValue(String name, Module module, String defaultValue, String... modes) {
        this(name, module, defaultValue, () -> true, modes);
    }

    public ModeValue(String name, Module module, String defaultValue,
                     BooleanSupplier visibilityCondition, String... modes) {
        super(name, module, defaultValue);
        this.visibilityCondition = visibilityCondition;
        this.modes = new LinkedHashSet<>();

        if (modes.length == 0) {
            throw new IllegalArgumentException("ModeValue must have at least one mode");
        }

        this.modes.addAll(Arrays.asList(modes));
        boolean found = false;
        for (String mode : this.modes) {
            if (mode.equalsIgnoreCase(defaultValue)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Default value '" + defaultValue + "' not found in modes");
        }
    }

    public Set<String> getModes() {
        return this.modes;
    }

    public int getModeCount() {
        return this.modes.size();
    }

    public boolean isVisible() {
        return this.visibilityCondition.getAsBoolean();
    }

    public boolean is(String mode) {
        return this.getValue().equalsIgnoreCase(mode);
    }

    public boolean isAny(String... modes) {
        String current = this.getValue();
        for (String mode : modes) {
            if (current.equalsIgnoreCase(mode)) {
                return true;
            }
        }
        return false;
    }

    public void next() {
        String[] modesArray = this.modes.toArray(new String[0]);
        String current = this.getValue();
        for (int i = 0; i < modesArray.length; i++) {
            if (modesArray[i].equalsIgnoreCase(current)) {
                int nextIndex = (i + 1) % modesArray.length;
                this.setValue(modesArray[nextIndex]);
                return;
            }
        }
    }

    public void previous() {
        String[] modesArray = this.modes.toArray(new String[0]);
        String current = this.getValue();
        for (int i = 0; i < modesArray.length; i++) {
            if (modesArray[i].equalsIgnoreCase(current)) {
                int prevIndex = (i - 1 + modesArray.length) % modesArray.length;
                this.setValue(modesArray[prevIndex]);
                return;
            }
        }
    }

    @Override
    protected String normalize(String value) {
        if (value == null) {
            return this.getDefaultValue();
        }

        for (String mode : this.modes) {
            if (mode.equalsIgnoreCase(value)) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Invalid mode: '" + value + "'. Available: " + this.modes);
    }

    @Override
    protected String parse(String value) {
        return value;
    }

    @Override
    public String getValueAsString() {
        return this.getValue();
    }
}