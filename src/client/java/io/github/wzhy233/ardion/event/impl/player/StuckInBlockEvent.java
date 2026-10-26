package io.github.wzhy233.ardion.event.impl.player;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class StuckInBlockEvent extends Event {
    private final Entity entity;
    private final BlockState state;
    private Vec3d multiplier;

    public StuckInBlockEvent(Entity entity, BlockState state, Vec3d multiplier) {
        this.entity = entity;
        this.state = state;
        this.multiplier = multiplier;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public BlockState getState() {
        return this.state;
    }

    public Vec3d getMultiplier() {
        return this.multiplier;
    }

    public void setMultiplier(Vec3d multiplier) {
        this.multiplier = multiplier;
    }
}
