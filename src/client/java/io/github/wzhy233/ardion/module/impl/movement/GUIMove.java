package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.player.MovementInputEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.listener.Priority;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class GUIMove extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public GUIMove() {
        super("gui-move", ModuleCategory.MOVEMENT);
    }

    @Listener(Priority.HIGHEST)
    private void onMovementInput(MovementInputEvent event) {
        if (mc.player == null || mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) {
            return;
        }

        boolean forwardKey = isKeyDown(mc.options.forwardKey);
        boolean backKey = isKeyDown(mc.options.backKey);
        boolean leftKey = isKeyDown(mc.options.leftKey);
        boolean rightKey = isKeyDown(mc.options.rightKey);
        boolean jumpKey = isKeyDown(mc.options.jumpKey);
        boolean sprintKey = isKeyDown(mc.options.sprintKey);

        float forward = forwardKey == backKey ? 0.0F : forwardKey ? 1.0F : -1.0F;
        float strafe = leftKey == rightKey ? 0.0F : leftKey ? 1.0F : -1.0F;

        event.setForward(forward);
        event.setStrafe(strafe);
        event.setJump(jumpKey);
        event.setSprint(sprintKey);
    }

    private static boolean isKeyDown(KeyBinding keyBinding) {
        return InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
    }
}
