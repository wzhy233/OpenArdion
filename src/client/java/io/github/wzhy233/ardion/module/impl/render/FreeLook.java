package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.access.CameraEntityAccess;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public final class FreeLook extends Module {
    private final ModeValue perspective = this.registerValue(new ModeValue("perspective", this, "behind", "front", "behind"));
    private Perspective previousPerspective;

    public FreeLook() {
        super("free-look", ModuleCategory.RENDER);
    }

    @Override
    protected void onEnable() {
        start();
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null && this.previousPerspective != null) {
            client.options.setPerspective(this.previousPerspective);
        }
        this.previousPerspective = null;
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() == MinecraftTickEvent.Stage.PRE && this.previousPerspective == null) {
            start();
        }
    }

    private void start() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options == null) {
            return;
        }

        if (this.previousPerspective == null) {
            this.previousPerspective = client.options.getPerspective();
        }
        if (client.player instanceof CameraEntityAccess access) {
            access.ardion$setCameraPitch(client.player.getPitch());
            access.ardion$setCameraYaw(client.player.getYaw());
        }
        client.options.setPerspective(this.perspective.is("front") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
    }
}
