package io.github.wzhy233.ardion.gui.ui;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;

public final class UiLayer implements AutoCloseable {
    private final Canvas canvas;
    private final int saved;

    public UiLayer(Canvas canvas, Rect bounds, float opacity) {
        this.canvas = canvas;
        saved = canvas.save();
        if (opacity < 1) canvas.saveLayerAlpha(bounds, Math.round(Math.clamp(opacity, 0, 1) * 255));
    }

    @Override
    public void close() { canvas.restoreToCount(saved); }
}
