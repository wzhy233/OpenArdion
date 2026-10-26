package io.github.wzhy233.ardion.music;

final class PlaybackClock {
    private record Sample(long playedMs, double anchorMs, long anchorNanos,
                          double submittedMs, boolean stopped) {
        double position(long nowNanos) {
            double elapsed = stopped ? 0.0 : Math.max(0L, nowNanos - anchorNanos) / 1_000_000.0;
            return Math.min(submittedMs, anchorMs + elapsed);
        }
    }

    private volatile Sample sample = new Sample(0L, 0.0, 0L, 0.0, false);

    synchronized void update(long playedMs, double submittedMs, long nowNanos) {
        Sample previous = sample;
        if (previous.stopped) return;
        long played = Math.max(0L, playedMs);
        double submitted = Math.max(played, submittedMs);
        if (previous.submittedMs == 0.0 || played > previous.playedMs
                || previous.position(nowNanos) >= previous.submittedMs) {
            double anchor = Math.min(submitted, Math.max(played, previous.position(nowNanos)));
            sample = new Sample(played, anchor, nowNanos, submitted, false);
        } else {
            sample = new Sample(previous.playedMs, previous.anchorMs, previous.anchorNanos, submitted, false);
        }
    }

    long positionMs(long nowNanos) {
        return (long) sample.position(nowNanos);
    }

    synchronized void stop(long nowNanos) {
        Sample previous = sample;
        sample = new Sample(previous.playedMs, previous.position(nowNanos), nowNanos,
                previous.submittedMs, true);
    }
}
