package io.github.wzhy233.ardion.event.impl.client;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.client.MinecraftClient;

public class MinecraftTickEvent extends Event {
    private final MinecraftClient client;
    private final Stage stage;

    public MinecraftTickEvent(MinecraftClient client, Stage stage) {
        this.client = client;
        this.stage = stage;
    }

    public MinecraftClient getClient() {
        return this.client;
    }

    public Stage getStage() {
        return this.stage;
    }

    public enum Stage {
        PRE,
        POST
    }
}
