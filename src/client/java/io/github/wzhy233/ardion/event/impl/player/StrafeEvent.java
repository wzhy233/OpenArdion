package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.Event;

public class StrafeEvent extends Event {
    private float forward;
    private float strafe;
    private float yaw;
    private float friction;

    public StrafeEvent(float forward, float strafe, float yaw, float friction) {
        this.forward = forward;
        this.strafe = strafe;
        this.yaw = yaw;
        this.friction = friction;
    }

    public float getForward() {
        return this.forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getFriction() {
        return this.friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public void multiply(double multiplier) {
        this.forward *= (float) multiplier;
        this.strafe *= (float) multiplier;
    }
}
