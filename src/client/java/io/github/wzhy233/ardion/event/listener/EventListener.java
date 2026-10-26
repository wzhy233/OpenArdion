package io.github.wzhy233.ardion.event.listener;

import io.github.wzhy233.ardion.event.base.Event;

@FunctionalInterface
public interface EventListener<T extends Event> {
    void invoke(T event);
}
