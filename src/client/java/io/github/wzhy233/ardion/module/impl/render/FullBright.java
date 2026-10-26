package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.world.WorldTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class FullBright extends Module {

    public FullBright() {
        super("full-bright", ModuleCategory.RENDER);
    }

    @Listener
    public void onTick(WorldTickEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) return;
        client.player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                220,
                0,
                false,
                false
        ));
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player != null) {
            client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}