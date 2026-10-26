package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.render.AntiBlind;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.render.GameRenderer.class)
public abstract class MixinGameRenderer {
    @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEffectFadeFactor(Lnet/minecraft/registry/entry/RegistryEntry;F)F"))
    private float removeNausea(ClientPlayerEntity player, RegistryEntry<StatusEffect> effect, float tickDelta) {
        AntiBlind antiBlind = AntiBlind.get();
        if (antiBlind != null && antiBlind.isEnabled() && effect.matches(StatusEffects.NAUSEA)) {
            return 0.0F;
        }

        return player.getEffectFadeFactor(effect, tickDelta);
    }
}
