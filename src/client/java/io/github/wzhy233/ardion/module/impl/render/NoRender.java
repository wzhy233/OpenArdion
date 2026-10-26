package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.List;

public final class NoRender extends Module {
    private final BooleanValue disableEffects = this.registerValue(new BooleanValue("disable-effects", this, true));

    public NoRender() {
        super("no-render", ModuleCategory.RENDER);
    }

    @Listener
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!this.disableEffects.getValue()) {
            return;
        }

        if (event.getPacket() instanceof EntityStatusEffectS2CPacket packet && isBadEffect(packet.getEffectId())) {
            event.setCancelled(true);
        }
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST || !this.disableEffects.getValue()) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null) {
            return;
        }

        for (StatusEffectInstance effect : List.copyOf(client.player.getStatusEffects())) {
            if (isBadEffect(effect.getEffectType())) {
                client.player.removeStatusEffect(effect.getEffectType());
            }
        }
    }

    @Override
    protected void onEnable() {
        if (!this.disableEffects.getValue()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        for (StatusEffectInstance effect : List.copyOf(client.player.getStatusEffects())) {
            if (isBadEffect(effect.getEffectType())) {
                client.player.removeStatusEffect(effect.getEffectType());
            }
        }
    }

    public boolean disableEffects() {
        return this.disableEffects.getValue();
    }

    private boolean isBadEffect(RegistryEntry<StatusEffect> effect) {
        return effect.matches(StatusEffects.BLINDNESS)
                || effect.matches(StatusEffects.NAUSEA)
                || effect.matches(StatusEffects.DARKNESS);
    }
}
