package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.CancellableEvent;

public class JumpEvent extends CancellableEvent {
    private float yaw;

    public JumpEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
