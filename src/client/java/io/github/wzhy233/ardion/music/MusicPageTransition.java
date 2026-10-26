package io.github.wzhy233.ardion.music;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import java.util.function.Consumer;

public final class MusicPageTransition {
    private float value = 1, start = 1, target = 1, elapsed, duration;

    public void snap(float closed) { value = start = target = closed; elapsed = duration = 0; }
    public float value() { return value; }
    public boolean moving() { return value != target; }
    public static float offset(float closed) { return 24 * closed; }

    public float step(boolean open, float seconds) {
        float requested = open ? 0 : 1;
        if (target != requested) {
            start = value; target = requested; elapsed = 0;
            duration = (open ? io.github.wzhy233.ardion.gui.ui.UiManager.ENTER_MS
                    : io.github.wzhy233.ardion.gui.ui.UiManager.EXIT_MS) / 1000.0F;
        }
        if (value == target) return value;
        elapsed += Math.max(0, seconds);
        float t = Math.min(1, elapsed / duration);
        float eased = io.github.wzhy233.ardion.gui.ui.UiTransition.ease(t);
        value = t == 1 ? target : start + (target - start) * eased;
        return value;
    }

    public static void draw(Canvas canvas, float width, float height, float radius, float closed, Consumer<Canvas> page) {
        if (closed >= 1) return;
        int save = canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(0, 0, width, height, radius), true);
            canvas.translate(0, offset(closed));
            Rect bounds = Rect.makeXYWH(0, 0, width, height);
            canvas.clipRect(bounds);
            try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, bounds, 1 - closed)) {
                page.accept(canvas);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
