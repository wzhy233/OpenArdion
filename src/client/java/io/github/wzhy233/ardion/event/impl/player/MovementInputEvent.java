package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.CancellableEvent;

public class MovementInputEvent extends CancellableEvent {
    private float forward;
    private float strafe;
    private final boolean pressingForward;
    private final boolean pressingBack;
    private final boolean pressingLeft;
    private final boolean pressingRight;
    private boolean jump;
    private boolean sprint;
    private boolean sneak;

    public MovementInputEvent(float forward, float strafe, boolean pressingForward, boolean pressingBack,
                              boolean pressingLeft, boolean pressingRight, boolean jump, boolean sneak,
                              boolean sprint) {
        this.forward = forward;
        this.strafe = strafe;
        this.pressingForward = pressingForward;
        this.pressingBack = pressingBack;
        this.pressingLeft = pressingLeft;
        this.pressingRight = pressingRight;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
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

    public boolean isPressingForward() {
        return this.pressingForward;
    }

    public boolean isPressingBack() {
        return this.pressingBack;
    }

    public boolean isPressingLeft() {
        return this.pressingLeft;
    }

    public boolean isPressingRight() {
        return this.pressingRight;
    }

    public boolean isJump() {
        return this.jump;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public boolean isSneak() {
        return this.sneak;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public boolean isSprint() {
        return this.sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

    public boolean isMoving() {
        return this.pressingForward != this.pressingBack || this.pressingLeft != this.pressingRight;
    }
}
