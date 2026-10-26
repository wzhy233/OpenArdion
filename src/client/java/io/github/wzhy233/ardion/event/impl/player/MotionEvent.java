package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.CancellableEvent;

public class MotionEvent extends CancellableEvent {
    private final Stage stage;
    private float yaw;
    private float pitch;
    private boolean onGround;

    public MotionEvent(Stage stage, float yaw, float pitch, boolean onGround) {
        this.stage = stage;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    public Stage getStage() {
        return this.stage;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public enum Stage {
        PRE,
        POST
    }
}
