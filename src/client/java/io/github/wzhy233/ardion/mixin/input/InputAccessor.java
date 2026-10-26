package io.github.wzhy233.ardion.mixin.input;

import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    @Accessor("movementVector")
    void ardion$setMovementVector(Vec2f movementVector);
}
