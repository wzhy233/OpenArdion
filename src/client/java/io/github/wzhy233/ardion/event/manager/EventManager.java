package io.github.wzhy233.ardion.event.manager;

import io.github.wzhy233.ardion.event.base.Event;
import io.github.wzhy233.ardion.event.bus.EventBus;

public final class EventManager {
    private static EventBus eventBus;

    private EventManager() {
    }

    public static void init() {
        eventBus = new EventBus();
    }

    public static void register(Object subscriber) {
        if (eventBus == null) {
            return;
        }
        getEventBus().register(subscriber);
    }

    public static void unregister(Object subscriber) {
        if (eventBus == null) {
            return;
        }
        getEventBus().unregister(subscriber);
    }

    public static <T extends Event> T post(T event) {
        if (eventBus == null) {
            return event;
        }
        return getEventBus().post(event);
    }

    public static boolean isInitialized() {
        return eventBus != null;
    }

    private static EventBus getEventBus() {
        if (eventBus == null) {
            throw new IllegalStateException("EventManager has not been initialized yet.");
        }

        return eventBus;
    }
}
