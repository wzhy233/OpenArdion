package io.github.wzhy233.ardion.util.time;

public final class TickTimeHelper {
    private long lastReset = TimeUtil.now();

    public boolean delay(double ticks) {
        return TimeUtil.elapsed(this.lastReset, Math.round(ticks * 50.0D));
    }

    public void reset() {
        this.lastReset = TimeUtil.now();
    }
}
