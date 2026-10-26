package io.github.wzhy233.ardion.util.render;

import net.minecraft.client.gui.DrawContext;

public record DrawFrame(DrawContext drawContext, int scaledWidth, int scaledHeight, float tickDelta, float scaleX, float scaleY,
                        boolean postEvent) {
}
