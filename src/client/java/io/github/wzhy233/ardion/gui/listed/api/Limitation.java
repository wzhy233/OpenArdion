package io.github.wzhy233.ardion.gui.listed.api;

import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import io.github.wzhy233.ardion.util.render.Skia;

public final class Limitation {
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float radius;

    public Limitation(float x, float y, float width, float height, float radius) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
    }

    public void start() {
        Skia.save();
        try (Path path = Path.makeRRect(RRect.makeXYWH(x, y, width, height, radius))) {
            Skia.clipPath(path);
        }
    }

    public void end() {
        Skia.restore();
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}
