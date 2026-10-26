package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

public final class Rotations extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final BooleanValue head = this.registerValue(new BooleanValue("head", this, true));
    private final BooleanValue body = this.registerValue(new BooleanValue("body", this, true));
    private final BooleanValue illness = this.registerValue(new BooleanValue("illness", this, false,
            () -> this.body.getValue() || this.head.getValue()));

    public Rotations() {
        super("rotations", ModuleCategory.RENDER);
    }

    public boolean shouldRenderHead() {
        return this.head.getValue();
    }

    public boolean shouldRenderBody() {
        return this.body.getValue();
    }

    public Rotation getRenderRotation(float tickDelta) {
        if (mc.player == null) {
            return null;
        }
        if (this.illness.getValue()) {
            return new Rotation(mc.player.getLerpedYaw(tickDelta) + 180.0F, mc.player.getLerpedPitch(tickDelta));
        }

        RotationManager manager = RotationManager.get();
        if (manager.getRotations() == null || mc.currentScreen instanceof InventoryScreen) {
            return null;
        }
        return manager.getRenderRotation(tickDelta);
    }
}
