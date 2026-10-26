package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.Event;

public class UpdateEvent extends Event {
    private final Stage stage;

    public UpdateEvent(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return this.stage;
    }

    public enum Stage {
        PRE,
        POST
    }
}
