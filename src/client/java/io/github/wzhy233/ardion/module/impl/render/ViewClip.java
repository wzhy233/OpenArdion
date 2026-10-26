package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.render.Render2DEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.time.SmoothAnimationTimer;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public final class ViewClip extends Module {
    private final NumberValue scale = this.registerValue(new NumberValue("scale", this, 1.0D, 0.5D, 2.0D));
    private final BooleanValue animation = this.registerValue(new BooleanValue("animation", this, true));
    private final NumberValue animationSpeed = this.registerValue(new NumberValue("animation-speed", this, 0.3D, 0.01D, 0.5D, () -> this.animation.getValue()));
    private final SmoothAnimationTimer personViewAnimation = new SmoothAnimationTimer(100.0F);
    private Perspective lastPerspective;

    public ViewClip() {
        super("view-clip", ModuleCategory.RENDER);
    }

    @Listener
    public void onRender(Render2DEvent event) {
        Perspective perspective = MinecraftClient.getInstance().options.getPerspective();
        if (this.lastPerspective != perspective) {
            this.lastPerspective = perspective;
            if (perspective == Perspective.FIRST_PERSON || perspective == Perspective.THIRD_PERSON_BACK) {
                this.personViewAnimation.value = 0.0F;
            }
        }

        this.personViewAnimation.speed = this.animation.getValue() ? this.animationSpeed.getValue().floatValue() : 1.0F;
        this.personViewAnimation.update(true);
    }

    public float getCameraScale() {
        return this.scale.getValue().floatValue() * this.personViewAnimation.value / 100.0F;
    }
}
