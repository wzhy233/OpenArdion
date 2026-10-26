package io.github.wzhy233.ardion.gui.ui;

public final class UiTransition {
    private float value;
    private float start;
    private float target;
    private long started;
    private long duration;

    public UiTransition(float initial) { snap(initial); }

    public void snap(float value) {
        this.value = start = target = value;
        duration = 0;
    }

    public float to(float requested, long now, long durationNanos) {
        sample(now);
        if (requested != target) {
            start = value;
            target = requested;
            started = now;
            duration = Math.max(1, durationNanos);
        }
        return sample(now);
    }

    public float sample(long now) {
        if (value == target) return value;
        double t = Math.max(0, Math.min(1, (now - started) / (double) duration));
        float eased = ease((float) t);
        value = t >= 1 ? target : start + (target - start) * eased;
        return value;
    }

    public static float ease(float t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
