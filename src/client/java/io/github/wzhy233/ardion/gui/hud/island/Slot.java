package io.github.wzhy233.ardion.gui.hud.island;

import io.github.wzhy233.ardion.util.animation.Animation;
import io.github.wzhy233.ardion.util.animation.Easing;

public abstract class Slot {
    static final long SHAPE_MS = 500L;

    long enteredAt;
    final long duration;
    long fadeOutStart = -1L;
    final Animation yOffsetAnim = new Animation(Easing.EASE_OUT_EXPO, SHAPE_MS);

    protected Slot(long duration) {
        this.enteredAt = System.currentTimeMillis();
        this.duration = duration;
    }

    void initYOffset(float y) {
        yOffsetAnim.animate(y);
    }

    void markFadeOut(long now) {
        if (fadeOutStart == -1L) fadeOutStart = now;
    }

    protected final void restart(long now) {
        enteredAt = now;
        fadeOutStart = -1L;
    }

    public abstract float getHeight();

    public abstract float measureWidth();

    public abstract void render(float x, float y, float width, float alpha);
}
