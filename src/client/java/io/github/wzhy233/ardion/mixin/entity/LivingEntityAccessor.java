package io.github.wzhy233.ardion.mixin.entity;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("hurtTime")
    int getHurtTime();

    @Accessor("maxHurtTime")
    int getMaxHurtTime();

    @Accessor("jumpingCooldown")
    void setJumpingCooldown(int jumpingCooldown);
}
