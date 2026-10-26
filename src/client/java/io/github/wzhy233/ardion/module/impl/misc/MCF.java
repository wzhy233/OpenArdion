package io.github.wzhy233.ardion.module.impl.misc;

import io.github.wzhy233.ardion.event.impl.client.MouseInputEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.manager.FriendManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class MCF extends Module {
    public MCF() {
        super("mcf", ModuleCategory.MISC);
    }

    @Listener
    private void onMouseInput(MouseInputEvent event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.getAction() != GLFW.GLFW_PRESS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || client.world == null || client.crosshairTarget == null) return;
        if (!(client.targetedEntity instanceof PlayerEntity target) || target == client.player) return;
        try {
            boolean added = FriendManager.toggle(target.getUuid(), target.getName().getString());
            OutputUtil.infoChatOnly((added ? "Added " : "Removed ") + target.getName().getString()
                    + (added ? " to friends list" : " from friends list"));
        } catch (IOException | UncheckedIOException exception) {
            OutputUtil.error("Could not save friends: " + exception.getMessage());
        }
    }
}
