package io.github.wzhy233.ardion.mixin.entity;

import io.github.wzhy233.ardion.event.impl.player.JumpEvent;
import io.github.wzhy233.ardion.event.impl.player.PostJumpEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @Unique
    private float ardion$oldJumpYaw;

    @Unique
    private boolean ardion$spoofingJumpYaw;

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void beforeJump(CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity)) {
            return;
        }

        Entity entity = (Entity) (Object) this;
        JumpEvent event = EventManager.post(new JumpEvent(entity.getYaw()));
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }
        this.ardion$oldJumpYaw = entity.getYaw();
        this.ardion$spoofingJumpYaw = true;
        entity.setYaw(event.getYaw());
    }

    @Inject(method = "jump", at = @At("TAIL"))
    private void afterJump(CallbackInfo ci) {
        if (!this.ardion$spoofingJumpYaw || !((Object) this instanceof ClientPlayerEntity)) {
            return;
        }

        ((Entity) (Object) this).setYaw(this.ardion$oldJumpYaw);
        this.ardion$spoofingJumpYaw = false;
        EventManager.post(new PostJumpEvent());
    }
}
