package io.github.wzhy233.ardion.event.impl.render;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.entity.Entity;

public class EventGlowing extends Event {
    private final Entity entity;
    private boolean glowing;

    public EventGlowing(Entity entity, boolean initial) {
        this.entity = entity;
        this.glowing = initial;
    }

    public Entity getEntity() { return entity; }
    public boolean isGlowing() { return glowing; }
    public void setGlowing(boolean glowing) { this.glowing = glowing; }
}