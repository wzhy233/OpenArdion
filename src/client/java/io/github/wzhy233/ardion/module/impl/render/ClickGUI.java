package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.gui.screen.ClickGuiScreen;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {
    public ClickGUI() {
        super("click-gui", ModuleCategory.RENDER);
        this.setBindKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    protected void onEnable() {
        setEnabled(false);
        MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
    }
}
