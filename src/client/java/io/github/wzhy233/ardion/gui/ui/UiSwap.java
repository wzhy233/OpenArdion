package io.github.wzhy233.ardion.gui.ui;

public final class UiSwap<T> {
    private final UiTransition opacity = new UiTransition(1);
    private T current;
    private T requested;
    private float value = 1;

    public T update(T next, long now) {
        if (current == null) current = next;
        requested = next;
        if (!java.util.Objects.equals(current, requested)) {
            value = opacity.to(0, now, UiManager.EXIT_MS * 1_000_000L);
            if (value == 0) {
                current = requested;
                value = opacity.to(1, now, UiManager.ENTER_MS * 1_000_000L);
            }
        } else value = opacity.to(1, now, UiManager.ENTER_MS * 1_000_000L);
        return current;
    }

    public float opacity() { return value; }
    public boolean moving() { return value < 1 || !java.util.Objects.equals(current, requested); }
}
