package io.github.wzhy233.ardion.util.time;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");

    private TimeUtil() {
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static boolean elapsed(long startTime, long duration) {
        return duration >= 0L && now() - startTime >= duration;
    }

    public static String clock() {
        return LocalTime.now().format(clock);
    }
}
