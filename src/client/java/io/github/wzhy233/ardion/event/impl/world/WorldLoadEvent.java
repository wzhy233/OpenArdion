package io.github.wzhy233.ardion.event.impl.world;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.client.world.ClientWorld;

public class WorldLoadEvent extends Event {
    private final ClientWorld world;

    public WorldLoadEvent(ClientWorld world) {
        this.world = world;
    }

    public ClientWorld getWorld() {
        return this.world;
    }
}
