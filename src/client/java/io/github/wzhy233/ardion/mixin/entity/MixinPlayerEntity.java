package io.github.wzhy233.ardion.mixin.entity;

import io.github.wzhy233.ardion.event.impl.player.KeepSprintEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.impl.world.Scaffold;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class MixinPlayerEntity {
    private boolean ardion$keepSprint;

    @Redirect(
            method = "knockbackTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void onAttackSlowdown(PlayerEntity player, Vec3d velocity) {
        Vec3d currentVelocity = player.getVelocity();
        double motion = currentVelocity.horizontalLengthSquared() <= 1.0E-12D
                ? 0.6D
                : velocity.horizontalLength() / currentVelocity.horizontalLength();
        KeepSprintEvent event = EventManager.post(new KeepSprintEvent(motion, false));
        if (currentVelocity.horizontalLengthSquared() <= 1.0E-12D) {
            player.setVelocity(velocity);
        } else {
            player.setVelocity(currentVelocity.x * event.getMotion(), velocity.y, currentVelocity.z * event.getMotion());
        }
        this.ardion$keepSprint = event.isSprint();
    }

    @Redirect(
            method = "knockbackTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V"
            )
    )
    private void onAttackStopSprinting(PlayerEntity player, boolean sprinting) {
        player.setSprinting(this.ardion$keepSprint || sprinting);
        this.ardion$keepSprint = false;
    }
}
