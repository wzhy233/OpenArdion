package io.github.wzhy233.ardion.module.impl.movement;

import io.github.wzhy233.ardion.event.impl.player.StuckInBlockEvent;
import io.github.wzhy233.ardion.event.impl.player.UpdateEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class FastWeb extends Module {
    private final ModeValue mode = this.registerValue(new ModeValue("mode", this, "GrimAC", "GrimAC"));
    private int playerInWebTick;
    private int ticksInWeb;

    public FastWeb() {
        super("fast-web", ModuleCategory.MOVEMENT);
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        if (!this.mode.is("GrimAC")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            this.reset();
            return;
        }

        if (event.getStage() == UpdateEvent.Stage.PRE && this.ticksInWeb > 1) {
            client.options.jumpKey.setPressed(false);
            return;
        }

        if (event.getStage() == UpdateEvent.Stage.POST && this.playerInWebTick < player.age) {
            this.ticksInWeb = 0;
        }
    }

    @Listener
    public void onStuck(StuckInBlockEvent event) {
        if (!this.mode.is("GrimAC")) return;
        if (event.getState().getBlock() != Blocks.COBWEB) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || event.getEntity() != client.player) return;

        this.playerInWebTick = client.player.age;
        this.ticksInWeb++;

        if (this.ticksInWeb > 5) {
            event.setMultiplier(new Vec3d(0.88D, 1.88D, 0.88D));
        }
    }

    @Override
    public void onDisable() {
        this.reset();
    }

    private void reset() {
        this.playerInWebTick = 0;
        this.ticksInWeb = 0;
    }
}
