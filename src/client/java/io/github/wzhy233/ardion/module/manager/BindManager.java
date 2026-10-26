package io.github.wzhy233.ardion.module.manager;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.impl.render.FreeLook;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class BindManager {
    private final Set<Integer> pressed = new HashSet<>();
    private final Set<Integer> downKeys = new HashSet<>();

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (client.player == null || client.currentScreen != null) {
            this.disableOnKeyModules();
            this.pressed.clear();
            this.downKeys.clear();
            return;
        }

        long handle = client.getWindow().getHandle();
        this.downKeys.clear();
        for (Module module : ModuleManager.getModules()) {
            if (!module.hasBind()) {
                continue;
            }

            int key = module.getBindKey();
            if (GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS) {
                if (isOnKey(module)) {
                    module.setEnabled(true);
                } else {
                    this.downKeys.add(key);
                    if (this.pressed.add(key)) {
                        for (Module bound : ModuleManager.getModulesByBind(key)) {
                            if (!isOnKey(bound)) {
                                bound.toggle();
                            }
                        }
                    }
                }
            } else if (isOnKey(module)) {
                module.setEnabled(false);
            }
        }

        this.pressed.retainAll(this.downKeys);
    }

    private void disableOnKeyModules() {
        for (Module module : ModuleManager.getModules()) {
            if (isOnKey(module) && module.isEnabled()) {
                module.setEnabled(false);
            }
        }
    }

    private static boolean isOnKey(Module module) {
        return module instanceof FreeLook;
    }
}
