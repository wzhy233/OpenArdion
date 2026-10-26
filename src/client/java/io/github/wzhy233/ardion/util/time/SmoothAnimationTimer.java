package io.github.wzhy233.ardion.util.time;

public final class SmoothAnimationTimer {
    public float target;
    public float speed = 0.4F;
    public float value;
    private long lastUpdate = System.currentTimeMillis();

    public SmoothAnimationTimer(float target) {
        this.target = target;
        this.value = target;
    }

    public void update(boolean increment) {
        long now = System.currentTimeMillis();
        int delta = (int) Math.max(0L, now - this.lastUpdate);
        this.lastUpdate = now;
        float finalState = increment ? this.target : 0.0F;
        float step = delta * (Math.max(10.0F, Math.abs(this.value - finalState) * 40.0F) * this.speed / 1000.0F);

        if (this.value < finalState) {
            this.value = Math.min(this.value + step, finalState);
        } else if (this.value > finalState) {
            this.value = Math.max(this.value - step, finalState);
        }
    }
}
