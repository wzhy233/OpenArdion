package io.github.wzhy233.ardion.event.impl.world;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.client.world.ClientWorld;

public class WorldTickEvent extends Event {
    private final ClientWorld world;

    public WorldTickEvent(ClientWorld world) {
        this.world = world;
    }

    public ClientWorld getWorld() {
        return this.world;
    }
}
