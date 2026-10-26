package io.github.wzhy233.ardion.music;

import io.github.wzhy233.ardion.gui.ui.UiSpring;

public final class MusicMotion {
    private final UiSpring spring;
    public MusicMotion(float initial) { spring = new UiSpring(initial); }
    public float value() { return spring.value(); }
    public void snap(float target) { spring.snap(target); }
    public float step(float target, float seconds, float frequency) { return spring.step(target, seconds, frequency); }
}