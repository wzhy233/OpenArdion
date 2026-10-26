package io.github.wzhy233.ardion.event.listener;

public enum Priority {
    HIGHEST(-999),
    HIGHER(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
    LOWER(4),
    LOWEST(999);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return this.level;
    }
}
