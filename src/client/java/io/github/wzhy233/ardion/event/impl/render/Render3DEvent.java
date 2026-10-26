package io.github.wzhy233.ardion.event.impl.render;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class Render3DEvent extends Event {
    private final MatrixStack matrices;
    private final VertexConsumerProvider consumers;
    private final float tickDelta;

    public Render3DEvent(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.tickDelta = tickDelta;
    }

    public MatrixStack getMatrices() {
        return this.matrices;
    }

    public VertexConsumerProvider getConsumers() {
        return this.consumers;
    }

    public float getTickDelta() {
        return this.tickDelta;
    }
}
