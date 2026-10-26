package io.github.wzhy233.ardion.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.wzhy233.ardion.event.impl.player.MotionEvent;
import io.github.wzhy233.ardion.event.impl.player.MovementInputEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.mixin.input.InputAccessor;
import io.github.wzhy233.ardion.module.impl.movement.NoSlow;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Unique
    private MotionEvent ardion$motionEvent;

    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo ci) {
        EventManager.post(new UpdateEvent(UpdateEvent.Stage.PRE));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void afterTick(CallbackInfo ci) {
        EventManager.post(new UpdateEvent(UpdateEvent.Stage.POST));
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void beforeSendMovementPackets(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        MotionEvent event = EventManager.post(new MotionEvent(MotionEvent.Stage.PRE, player.getYaw(), player.getPitch(), player.isOnGround()));
        if (event.isCancelled()) {
            this.ardion$motionEvent = null;
            ci.cancel();
            return;
        }

        this.ardion$motionEvent = event;
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void afterSendMovementPackets(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        EventManager.post(new MotionEvent(MotionEvent.Stage.POST, player.getYaw(), player.getPitch(), player.isOnGround()));
        this.ardion$motionEvent = null;
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float redirectPacketYaw(ClientPlayerEntity player) {
        return this.ardion$motionEvent == null ? player.getYaw() : this.ardion$motionEvent.getYaw();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float redirectPacketPitch(ClientPlayerEntity player) {
        return this.ardion$motionEvent == null ? player.getPitch() : this.ardion$motionEvent.getPitch();
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
    private boolean redirectPacketOnGround(ClientPlayerEntity player) {
        return this.ardion$motionEvent == null ? player.isOnGround() : this.ardion$motionEvent.isOnGround();
    }


    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER), cancellable = true)
    private void onTickMovement(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        io.github.wzhy233.mcp.GameMovement.apply(player);
        PlayerInput input = player.input == null ? PlayerInput.DEFAULT : player.input.playerInput;
        Vec2f movementInput = player.input == null ? Vec2f.ZERO : player.input.getMovementInput();
        MovementInputEvent event = EventManager.post(new MovementInputEvent(
                movementInput.y,
                movementInput.x,
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                input.sneak(),
                input.sprint()
        ));
        if (player.input != null && (event.getForward() != movementInput.y
                || event.getStrafe() != movementInput.x
                || event.isJump() != input.jump()
                || event.isSneak() != input.sneak()
                || event.isSprint() != input.sprint())) {
            player.input.playerInput = new PlayerInput(
                    event.getForward() > 0.0F,
                    event.getForward() < 0.0F,
                    event.getStrafe() > 0.0F,
                    event.getStrafe() < 0.0F,
                    event.isJump(),
                    event.isSneak(),
                    event.isSprint()
            );
        }
        if (player.input != null) {
            player.forwardSpeed = event.getForward();
            player.sidewaysSpeed = event.getStrafe();
            if (event.getForward() != movementInput.y || event.getStrafe() != movementInput.x) {
                ((InputAccessor) player.input).ardion$setMovementVector(new Vec2f(event.getStrafe(), event.getForward()).normalize());
            }
        }
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
            method = "applyMovementSpeedFactors",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getActiveItemSpeedMultiplier()F")
    )
    private float ardion$noSlowActiveItemMultiplier(float original) {
        NoSlow noSlow = ModuleManager.getModule(NoSlow.class);
        return noSlow != null && noSlow.isEnabled() && noSlow.shouldCancelSlow() ? 1.0F : original;
    }
}
