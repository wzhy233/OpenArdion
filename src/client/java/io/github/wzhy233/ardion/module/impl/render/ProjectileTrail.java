package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;

public final class ProjectileTrail extends Module {
    private final ModeValue effect = this.registerValue(new ModeValue("effect", this, "hearts",
            "black-smoke", "fire", "green-star", "hearts", "magic", "music-note", "slime", "spark", "swirl", "white-smoke"));

    public ProjectileTrail() {
        super("projectile-trail", ModuleCategory.RENDER);
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null || client.world == null) {
            return;
        }

        ParticleEffect particle = particle();
        if (particle == null) {
            return;
        }

        Box box = Box.from(new net.minecraft.util.math.Vec3d(client.player.getX(), client.player.getY(), client.player.getZ())).expand(150.0D);
        for (ProjectileEntity projectile : client.world.getEntitiesByClass(ProjectileEntity.class, box, entity -> true)) {
            if (!shouldRenderTrail(client, projectile)) {
                continue;
            }

            client.world.addParticleClient(particle, projectile.getX(), projectile.getY(), projectile.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    private boolean shouldRenderTrail(MinecraftClient client, ProjectileEntity projectile) {
        if (projectile == null || projectile.isOnGround() || projectile.getVelocity().lengthSquared() <= 0.01D) {
            return false;
        }

        if (client.world == null || client.player == null) {
            return false;
        }

        boolean insideSolidBlock = client.world.getBlockState(projectile.getBlockPos()).isSolidBlock(client.world, projectile.getBlockPos());
        return !insideSolidBlock || projectile.getOwner() == null || projectile.getOwner().getId() == client.player.getId();
    }

    private ParticleEffect particle() {
        return switch (this.effect.getValue().toLowerCase()) {
            case "black-smoke" -> ParticleTypes.SMOKE;
            case "fire" -> ParticleTypes.FLAME;
            case "green-star" -> ParticleTypes.HAPPY_VILLAGER;
            case "hearts" -> ParticleTypes.HEART;
            case "magic" -> ParticleTypes.WITCH;
            case "music-note" -> ParticleTypes.NOTE;
            case "slime" -> ParticleTypes.ITEM_SLIME;
            case "spark" -> ParticleTypes.FIREWORK;
            case "swirl" -> ParticleTypes.ASH;
            case "white-smoke" -> ParticleTypes.WHITE_SMOKE;
            default -> null;
        };
    }
}
