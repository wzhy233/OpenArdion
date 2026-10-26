package io.github.wzhy233.ardion.event.bus;

import io.github.wzhy233.ardion.event.base.Event;
import io.github.wzhy233.ardion.event.listener.Listener;

import java.lang.reflect.Method;
import java.util.*;

public final class EventBus {
    private final Object lock = new Object();
    private final Map<Class<?>, List<RegisteredListener>> listeners = new HashMap<>();
    private final Map<Class<? extends Event>, List<RegisteredListener>> dispatchCache = new HashMap<>();
    private long nextOrder;

    public void register(Object subscriber) {
        synchronized (this.lock) {
            this.unregisterLocked(subscriber);
            for (Class<?> type = subscriber.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    Listener annotation = method.getAnnotation(Listener.class);
                    if (annotation == null || method.getParameterCount() != 1) {
                        continue;
                    }

                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (!Event.class.isAssignableFrom(parameterType)) {
                        continue;
                    }

                    method.setAccessible(true);
                    this.listeners
                            .computeIfAbsent(parameterType, key -> new ArrayList<>())
                            .add(new RegisteredListener(subscriber, method, annotation.value().getLevel(), this.nextOrder++));
                }
            }
            this.dispatchCache.clear();
        }
    }

    public void unregister(Object subscriber) {
        synchronized (this.lock) {
            this.unregisterLocked(subscriber);
        }
    }

    private void unregisterLocked(Object subscriber) {
        boolean changed = this.listeners.entrySet().removeIf(entry -> {
            boolean removed = entry.getValue().removeIf(listener -> listener.owner == subscriber);
            if (removed) {
                this.dispatchCache.clear();
            }
            return entry.getValue().isEmpty();
        });
        if (changed) {
            this.dispatchCache.clear();
        }
    }

    public <T extends Event> T post(T event) {
        List<RegisteredListener> callList;
        synchronized (this.lock) {
            callList = this.dispatchCache.computeIfAbsent(event.getClass(), this::buildDispatchList);
        }
        Set<Class<?>> lua = io.github.wzhy233.mcp.LuaRuntime.watched;
        if (lua != null && lua.contains(event.getClass())) io.github.wzhy233.mcp.LuaRuntime.onWatched(event);
        for (RegisteredListener listener : callList) {
            long start = io.github.wzhy233.ardion.debug.ClientDiagnostics.begin(event);
            Throwable failure = null;
            try { listener.invoke(event); }
            catch (RuntimeException | Error error) { failure = error; throw error; }
            finally { if (start != 0) io.github.wzhy233.ardion.debug.ClientDiagnostics.end(start, event, listener.owner, listener.method.getName(), failure); }
        }
        return event;
    }

    private List<RegisteredListener> buildDispatchList(Class<? extends Event> eventClass) {
        List<RegisteredListener> callList = new ArrayList<>();
        for (Map.Entry<Class<?>, List<RegisteredListener>> entry : this.listeners.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                callList.addAll(entry.getValue());
            }
        }

        callList.sort(Comparator
                .comparingInt((RegisteredListener listener) -> listener.priority)
                .thenComparingLong(listener -> listener.order));
        return List.copyOf(callList);
    }

    private static final class RegisteredListener {
        private final Object owner;
        private final Method method;
        private final int priority;
        private final long order;

        private RegisteredListener(Object owner, Method method, int priority, long order) {
            this.owner = owner;
            this.method = method;
            this.priority = priority;
            this.order = order;
        }

        private void invoke(Event event) {
            try {
                this.method.invoke(this.owner, event);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to dispatch event: " + event.getClass().getSimpleName(), exception);
            }
        }
    }
}
