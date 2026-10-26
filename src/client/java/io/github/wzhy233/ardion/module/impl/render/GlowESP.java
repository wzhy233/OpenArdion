package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.render.EventGlowing;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.player.PlayerUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

public final class GlowESP extends Module {

    private final BooleanValue livingEntities = this.registerValue(
            new BooleanValue("living-entities", this, false)
    );
    private final BooleanValue items = this.registerValue(
            new BooleanValue("items", this, false)
    );
    private final BooleanValue projectiles = this.registerValue(
            new BooleanValue("projectiles", this, false)
    );

    private final BooleanValue players = this.registerValue(
            new BooleanValue("players", this, true)
    );

    public GlowESP() {
        super("glow-esp", ModuleCategory.RENDER);
    }

    @Listener
    public void onGlowing(EventGlowing event) {
        Entity entity = event.getEntity();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null) return;

        if (entity instanceof PlayerEntity && players.getValue() && !PlayerUtil.isTeammate(mc.player, (PlayerEntity) entity)) {
            event.setGlowing(true);
        } else if (entity instanceof LivingEntity && !(entity instanceof PlayerEntity) && livingEntities.getValue()) {
            event.setGlowing(true);
        } else if (entity instanceof ItemEntity && items.getValue()) {
            event.setGlowing(true);
        } else if (entity instanceof ProjectileEntity && projectiles.getValue()) {
            event.setGlowing(true);
        }
    }
}