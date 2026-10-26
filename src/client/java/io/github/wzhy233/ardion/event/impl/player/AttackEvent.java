package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.entity.Entity;

public class AttackEvent extends Event {
    private final Entity target;

    public AttackEvent(Entity target) {
        this.target = target;
    }

    public Entity getTarget() {
        return this.target;
    }
}
