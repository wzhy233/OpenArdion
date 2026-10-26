package io.github.wzhy233.mcp;

import io.github.wzhy233.ardion.mixin.input.InputAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

public final class GameMovement {
    private static boolean walking, jumping;
    static void steer(boolean jump) { walking = true; jumping = jump; }
    public static void release() { walking = false; jumping = false; }
    public static void apply(ClientPlayerEntity player) {
        if (!walking || player.input == null) return;
        player.input.playerInput = new PlayerInput(true, false, false, false, jumping, false, false);
        ((InputAccessor) player.input).ardion$setMovementVector(new Vec2f(0, 1));
    }
}
