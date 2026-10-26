package io.github.wzhy233.ardion.event.impl.render;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.client.gui.DrawContext;

public class Render2DAfterSkiaEvent extends Event {
    private final DrawContext drawContext;
    private final float tickDelta;
    private final int scaledWidth;
    private final int scaledHeight;

    public Render2DAfterSkiaEvent(DrawContext drawContext, float tickDelta, int scaledWidth, int scaledHeight) {
        this.drawContext = drawContext;
        this.tickDelta = tickDelta;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
    }

    public DrawContext getDrawContext() {
        return this.drawContext;
    }

    public float getTickDelta() {
        return this.tickDelta;
    }

    public int getScaledWidth() {
        return this.scaledWidth;
    }

    public int getScaledHeight() {
        return this.scaledHeight;
    }
}
