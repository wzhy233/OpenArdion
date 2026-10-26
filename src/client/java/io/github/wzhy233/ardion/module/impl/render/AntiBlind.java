package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.List;

public final class AntiBlind extends Module {
    private final BooleanValue blinding = this.registerValue(new BooleanValue("blinding", this, true));
    private final BooleanValue darkness = this.registerValue(new BooleanValue("darkness", this, true));
    private final BooleanValue nausea = this.registerValue(new BooleanValue("nausea", this, true));
    private final BooleanValue wallOverlay = this.registerValue(new BooleanValue("wall-overlay", this, false));
    private final BooleanValue liquidsFog = this.registerValue(new BooleanValue("liquids-fog", this, false));
    private final NumberValue fireOpacity = this.registerValue(new NumberValue("fire-opacity", this, 100.0D, 0.0D, 100.0D, true));

    public AntiBlind() {
        super("anti-blind", ModuleCategory.RENDER);
    }

    @Listener
    private void onPacket(PacketReceiveEvent event) {
        if (event.getPacket() instanceof EntityStatusEffectS2CPacket packet && this.isBlocked(packet.getEffectId())) {
            event.setCancelled(true);
        }
    }

    @Listener
    private void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST || event.getClient().player == null) {
            return;
        }

        for (StatusEffectInstance effect : List.copyOf(event.getClient().player.getStatusEffects())) {
            if (this.isBlocked(effect.getEffectType())) {
                event.getClient().player.removeStatusEffect(effect.getEffectType());
            }
        }
    }

    public boolean blocksWallOverlay() {
        return this.isEnabled() && this.wallOverlay.getValue();
    }

    public boolean blocksLiquidsFog() {
        return this.isEnabled() && this.liquidsFog.getValue();
    }

    public float getFireOpacity() {
        return this.isEnabled() ? this.fireOpacity.getValue().floatValue() / 100.0F : 1.0F;
    }

    private boolean isBlocked(RegistryEntry<StatusEffect> effect) {
        return this.isEnabled() && ((this.blinding.getValue() && effect.matches(StatusEffects.BLINDNESS))
                || (this.darkness.getValue() && effect.matches(StatusEffects.DARKNESS))
                || (this.nausea.getValue() && effect.matches(StatusEffects.NAUSEA)));
    }

    public static AntiBlind get() {
        return io.github.wzhy233.ardion.module.manager.ModuleManager.getModule(AntiBlind.class);
    }
}
