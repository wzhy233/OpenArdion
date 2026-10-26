package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.Event;

public class KeepSprintEvent extends Event {
    private double motion;
    private boolean sprint;

    public KeepSprintEvent(double motion, boolean sprint) {
        this.motion = motion;
        this.sprint = sprint;
    }

    public double getMotion() {
        return this.motion;
    }

    public void setMotion(double motion) {
        this.motion = motion;
    }

    public boolean isSprint() {
        return this.sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
