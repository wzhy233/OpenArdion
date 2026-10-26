package io.github.wzhy233.ardion.event.impl.render;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.text.OrderedText;

public class RenderTextEvent extends Event {
    private OrderedText text;

    public RenderTextEvent(OrderedText text) {
        this.text = text;
    }

    public OrderedText getText() {
        return this.text;
    }

    public void setText(OrderedText text) {
        this.text = text;
    }
}
