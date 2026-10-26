package io.github.wzhy233.ardion.module.impl.combat;

import io.github.wzhy233.ardion.event.impl.player.AttackEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

public final class MoreKB extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final BooleanValue onlyGround = this.registerValue(new BooleanValue("only-ground", this, true));

    private LivingEntity target;

    public MoreKB() {
        super("more-kb", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        this.target = null;
    }

    @Override
    protected void onDisable() {
        this.target = null;
    }

    @Listener
    private void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity livingTarget) {
            this.target = livingTarget;
        }
    }

    @Listener
    private void onUpdate(UpdateEvent event) {
        if (event.getStage() != UpdateEvent.Stage.PRE || mc.player == null || mc.world == null) {
            return;
        }

        LivingEntity entity = this.getCrosshairLivingEntity();
        if (entity == null) {
            entity = this.target;
        }

        if (entity == null) {
            return;
        }

        double x = mc.player.getX() - entity.getX();
        double z = mc.player.getZ() - entity.getZ();
        float calcYaw = (float) (Math.atan2(z, x) * 180.0D / Math.PI - 90.0D);
        float diffYaw = Math.abs(MathHelper.wrapDegrees(calcYaw - entity.headYaw));
        if (diffYaw > 120.0F) {
            return;
        }

        if (entity.hurtTime == 10 && (!this.onlyGround.getValue() || mc.player.isOnGround())) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
                mc.player.setSprinting(true);
            }
            this.target = null;
        }
    }

    private LivingEntity getCrosshairLivingEntity() {
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        Entity entity = entityHitResult.getEntity();
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }
}
