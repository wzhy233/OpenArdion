package io.github.wzhy233.ardion.util.animation;

public final class Animation {
    private final Easing easing;
    private final long duration;
    private long startTime;
    private double startValue;
    private double destinationValue = Double.NaN;
    private double value;
    private boolean finished;

    public Animation(Easing easing, long duration) {
        this.easing = easing;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public double animate(double destinationValue) {
        if (!Double.isFinite(destinationValue)) {
            return Double.isFinite(value) ? value : 0.0D;
        }

        long millis = System.currentTimeMillis();
        if (this.destinationValue != destinationValue) {
            if (!Double.isNaN(this.destinationValue)) reset();
            this.destinationValue = destinationValue;
        } else {
            finished = millis - duration > startTime;
            if (finished) {
                value = destinationValue;
                return value;
            }
        }

        double result = easing.getFunction().apply(getProgress());
        double next = value > destinationValue
                ? startValue - (startValue - destinationValue) * result
                : startValue + (destinationValue - startValue) * result;
        if (!Double.isFinite(next)) {
            next = destinationValue;
            startValue = destinationValue;
        }
        value = next;
        return value;
    }

    private double getProgress() {
        if (duration <= 0L) return 1.0D;
        double progress = (double) (System.currentTimeMillis() - startTime) / (double) duration;
        return Math.max(0.0D, Math.min(1.0D, progress));
    }

    private void reset() {
        startTime = System.currentTimeMillis();
        startValue = value;
        finished = false;
    }
}
