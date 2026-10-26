package io.github.wzhy233.ardion.event.impl.client;

import io.github.wzhy233.ardion.event.base.Event;

public final class MouseInputEvent extends Event {
    private final int button;
    private final int action;

    public MouseInputEvent(int button, int action) {
        this.button = button;
        this.action = action;
    }

    public int getButton() {
        return button;
    }

    public int getAction() {
        return action;
    }
}
