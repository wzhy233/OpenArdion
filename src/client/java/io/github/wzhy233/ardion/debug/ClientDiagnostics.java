package io.github.wzhy233.ardion.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.event.base.CancellableEvent;
import io.github.wzhy233.ardion.event.base.Event;
import java.util.ArrayDeque;

public final class ClientDiagnostics {
    private static volatile long until;
    private static volatile String filter = "";
    private static final ArrayDeque<JsonObject> EVENTS = new ArrayDeque<>();
    private static long dropped;
    public static long begin(Event event) {
        if (System.nanoTime() >= until || !event.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains(filter)) return 0;
        return System.nanoTime();
    }
    public static synchronized void end(long start, Event event, Object owner, String method, Throwable failure) {
        if (start == 0) return;
        JsonObject row = new JsonObject();
        row.addProperty("event", event.getClass().getName()); row.addProperty("listener", owner.getClass().getName() + "." + method);
        row.addProperty("thread", Thread.currentThread().getName()); row.addProperty("micros", (System.nanoTime() - start) / 1000);
        row.addProperty("cancelled", event instanceof CancellableEvent cancellable && cancellable.isCancelled());
        if (failure != null) row.addProperty("error", failure.getClass().getName() + ": " + failure.getMessage());
        if (EVENTS.size() == 512) { EVENTS.removeFirst(); dropped++; }
        EVENTS.addLast(row);
    }
    public static synchronized String control(String action, String query, int seconds) {
        switch (action) {
            case "start" -> { EVENTS.clear(); dropped = 0; filter = query.toLowerCase(java.util.Locale.ROOT); until = System.nanoTime() + seconds * 1_000_000_000L; }
            case "stop" -> until = 0;
            case "read" -> { }
            default -> throw new IllegalArgumentException("Trace action must be start, read, stop");
        }
        JsonObject result = new JsonObject(); result.addProperty("active", System.nanoTime() < until); result.addProperty("dropped", dropped);
        JsonArray rows = new JsonArray(); EVENTS.forEach(rows::add); result.add("events", rows);
        return result.toString();
    }
}
