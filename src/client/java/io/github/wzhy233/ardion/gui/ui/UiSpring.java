package io.github.wzhy233.ardion.gui.ui;

public final class UiSpring {
    private float value;
    private float velocity;

    public UiSpring(float initial) { value = initial; }
    public float value() { return value; }
    public void snap(float target) { value = target; velocity = 0; }

    public float step(float target, float seconds, float frequency) {
        float dt = Math.max(0, Math.min(seconds, 0.1F));
        float offset = value - target;
        float impulse = velocity + frequency * offset;
        float decay = (float) Math.exp(-frequency * dt);
        value = target + (offset + impulse * dt) * decay;
        velocity = (velocity - frequency * impulse * dt) * decay;
        if (Math.abs(value - target) < 0.001F && Math.abs(velocity) < 0.001F) snap(target);
        return value;
    }
}
