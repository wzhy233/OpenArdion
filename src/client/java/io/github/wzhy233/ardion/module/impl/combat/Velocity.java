package io.github.wzhy233.ardion.module.impl.combat;

import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public final class Velocity extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final NumberValue horizontal = this.registerValue(new NumberValue("horizontal", this, 0.0D, -10.0D, 10.0D));
    private final NumberValue vertical = this.registerValue(new NumberValue("vertical", this, 0.0D, -10.0D, 10.0D));
    private final ModeValue filter = this.registerValue(new ModeValue("filter", this, "always", "always", "on-ground", "in-air"));

    public Velocity() {
        super("velocity", ModuleCategory.COMBAT);
    }

    @Listener(Priority.HIGHEST)
    private void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == mc.player.getId()) {
            if (!this.shouldModify()) {
                return;
            }

            double horizontal = this.horizontal.getValue();
            double vertical = this.vertical.getValue();
            if (horizontal == 0.0D && vertical == 0.0D) {
                event.setCancelled(true);
                return;
            }

            Vec3d velocity = velocityPacket.getVelocity();
            event.setPacket(new EntityVelocityUpdateS2CPacket(
                    velocityPacket.getEntityId(),
                    new Vec3d(velocity.x * horizontal, velocity.y * vertical, velocity.z * horizontal)
            ));
            return;
        }

        if (packet instanceof ExplosionS2CPacket explosionPacket && explosionPacket.playerKnockback().isPresent()) {
            if (!this.shouldModify()) {
                return;
            }

            Vec3d knockback = explosionPacket.playerKnockback().get();
            Vec3d modifiedKnockback = new Vec3d(
                    knockback.x * this.horizontal.getValue(),
                    knockback.y * this.vertical.getValue(),
                    knockback.z * this.horizontal.getValue()
            );
            event.setPacket(new ExplosionS2CPacket(
                    explosionPacket.center(),
                    explosionPacket.radius(),
                    explosionPacket.blockCount(),
                    Optional.of(modifiedKnockback),
                    explosionPacket.explosionParticle(),
                    explosionPacket.explosionSound(),
                    explosionPacket.blockParticles()
            ));
        }
    }

    private boolean shouldModify() {
        return switch (this.filter.getValue().toLowerCase()) {
            case "on-ground" -> mc.player.isOnGround();
            case "in-air" -> !mc.player.isOnGround();
            default -> true;
        };
    }
}
