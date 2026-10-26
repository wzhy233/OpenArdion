package io.github.wzhy233.ardion.module.impl.combat;

import io.github.wzhy233.ardion.event.impl.player.AttackEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.network.PacketUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class Criticals extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public Criticals() {
        super("criticals", ModuleCategory.COMBAT);
    }

    @Listener(Priority.HIGHEST)
    private void onAttack(AttackEvent event) {
        if (!(event.getTarget() instanceof LivingEntity) || !this.canDoCriticalHit(event.getTarget())) {
            return;
        }

        this.sendOffset(0.2D);
        this.sendOffset(0.01D);
        mc.player.addCritParticles(event.getTarget());
    }

    private void sendOffset(double offset) {
        PacketUtil.sendQueued(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY() + offset,
                mc.player.getZ(),
                mc.player.getYaw(),
                mc.player.getPitch(),
                false,
                mc.player.horizontalCollision
        ));
    }

    private boolean canDoCriticalHit(Entity target) {
        return mc.player != null
                && mc.world != null
                && target instanceof LivingEntity
                && mc.player.isOnGround()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.hasBlindnessEffect()
                && !mc.player.hasVehicle()
                && !mc.player.isSprinting()
                && !mc.player.isInLava()
                && !mc.player.wasInPowderSnow
                && !mc.player.isSubmergedInWater()
                && !mc.player.isGliding();
    }
}
