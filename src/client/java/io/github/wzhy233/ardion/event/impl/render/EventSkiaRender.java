package io.github.wzhy233.ardion.event.impl.render;

import io.github.humbleui.skija.Canvas;
import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.client.gui.DrawContext;

public final class EventSkiaRender extends Event {
    private final Canvas canvas;
    private final DrawContext drawContext;
    private final int scaledWidth;
    private final int scaledHeight;
    private final float tickDelta;

    public EventSkiaRender(Canvas canvas, DrawContext drawContext, int scaledWidth, int scaledHeight, float tickDelta) {
        this.canvas = canvas;
        this.drawContext = drawContext;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
        this.tickDelta = tickDelta;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public DrawContext getDrawContext() {
        return drawContext;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public int getScaledHeight() {
        return scaledHeight;
    }

    public float getTickDelta() {
        return tickDelta;
    }
}
