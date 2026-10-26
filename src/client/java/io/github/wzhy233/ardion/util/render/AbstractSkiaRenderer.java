package io.github.wzhy233.ardion.util.render;

import io.github.humbleui.skija.Surface;

public abstract class AbstractSkiaRenderer {
    public abstract void init(int width, int height);

    public abstract void preRender(Surface surface);

    public abstract void postRender(Surface surface);

    public abstract void destroy();

    public abstract Surface create(int width, int height);
}
