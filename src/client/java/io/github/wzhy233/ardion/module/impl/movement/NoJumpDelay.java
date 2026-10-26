package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.mixin.entity.LivingEntityAccessor;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import net.minecraft.client.MinecraftClient;

public final class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("no-jump-delay", ModuleCategory.MOVEMENT);
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ((LivingEntityAccessor) client.player).setJumpingCooldown(0);
    }
}
