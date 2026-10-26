package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.player.KeepSprintEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;

public final class KeepSprint extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final BooleanValue prediction = this.registerValue(new BooleanValue("prediction", this, false));
    private final NumberValue percentage = this.registerValue(new NumberValue("slowdown-percentage", this,
            0.0D, 0.0D, 100.0D, true));

    private boolean can;

    public KeepSprint() {
        super("keep-sprint", ModuleCategory.MOVEMENT);
    }

    @Listener
    private void onPreUpdate(UpdateEvent event) {
        if (event.getStage() == UpdateEvent.Stage.PRE) {
            this.can = false;
        }
    }

    @Listener
    private void onPostUpdate(UpdateEvent event) {
        if (event.getStage() == UpdateEvent.Stage.POST) {
            this.can = true;
        }
    }

    @Listener
    private void onKeepSprint(KeepSprintEvent event) {
        if (!this.shouldKeepSprint()) {
            return;
        }

        event.setMotion(event.getMotion() + (1.0D - event.getMotion())
                * (1.0D - this.percentage.getValue() / 100.0D));
        event.setSprint(true);
    }

    private boolean shouldKeepSprint() {
        if (mc.player == null) {
            return false;
        }
        return !this.prediction.getValue() || this.can;
    }
}
