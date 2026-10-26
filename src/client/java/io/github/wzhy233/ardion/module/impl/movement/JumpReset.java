package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.impl.player.MovementInputEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import java.util.concurrent.ThreadLocalRandom;

public final class JumpReset extends Module {
    private final BooleanValue liquidCheck = this.registerValue(new BooleanValue("liquid-check", this, true));
    private final NumberValue chance = this.registerValue(new NumberValue("chance", this, 100.0D, 0.0D, 100.0D, true));
    private boolean pending;

    public JumpReset() {
        super("jump-reset", ModuleCategory.MOVEMENT);
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            this.clearPending();
            return;
        }

        if (client.currentScreen != null) {
            this.clear();
            return;
        }
    }

    @Listener
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof GameStateChangeS2CPacket packet
                && packet.getReason() == GameStateChangeS2CPacket.PROJECTILE_HIT_PLAYER) {
            this.pending = true;
            return;
        }

        if (!(event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || packet.getEntityId() != client.player.getId()) return;
        if (!(client.player.getAttacker() instanceof PlayerEntity attacker) || attacker == client.player) return;

        this.pending = true;
    }

    @Listener
    public void onMovementInput(MovementInputEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!this.pending) return;

        ClientPlayerEntity player = client.player;
        this.pending = false;
        if (player == null || client.currentScreen != null || client.player.isSpectator()) {
            this.clear();
            return;
        }

        boolean shouldJump = this.canJumpReset(player)
                && ThreadLocalRandom.current().nextDouble(100.0D) < this.chance.getValue();

        event.setJump(shouldJump);
        if (client.options != null) {
            client.options.jumpKey.setPressed(false);
        }
    }

    @Override
    public void onDisable() {
        this.clearPending();
    }

    private void clearPending() {
        this.clear();
    }

    private void clear() {
        this.pending = false;
    }

    private boolean canJumpReset(ClientPlayerEntity player) {
        if (!player.isOnGround()) return false;
        return !this.liquidCheck.getValue()
                || (!player.isTouchingWater() && !player.isSubmergedInWater() && !player.isInLava());
    }
}
