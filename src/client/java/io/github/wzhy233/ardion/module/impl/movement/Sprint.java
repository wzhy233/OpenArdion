package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import net.minecraft.client.MinecraftClient;

public final class Sprint extends Module {
    public Sprint() {
        super("sprint", ModuleCategory.MOVEMENT);
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        if (MinecraftClient.getInstance().player == null) return;
        MinecraftClient.getInstance().options.sprintKey.setPressed(true);
    }
}