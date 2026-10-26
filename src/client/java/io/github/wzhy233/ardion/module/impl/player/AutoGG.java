package io.github.wzhy233.ardion.module.impl.player;

import io.github.wzhy233.ardion.event.impl.network.ChatReceiveEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.StringValue;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class AutoGG extends Module {
    private static final List<String> TRIGGERS = List.of(
            "1st killer", "1st place", "winner", "damage dealt",
            "winning team", "1st", "winners", "winning team",
            " won the game", "top seeker:", "1st place",
            "last team standing", "winner #1 (", "top survivors",
            "winners",  "most wool placed",
            "your overall winstreak:", "play again"
    );

    private final StringValue message = this.registerValue(new StringValue("message", this, "gg"));
    private boolean end;

    public AutoGG() {
        super("auto-gg", ModuleCategory.PLAYER);
    }

    @Listener
    public void onChat(ChatReceiveEvent event) {
        if (this.end) return;

        String text = event.getPlainMessage();
        if (text.isBlank() || TRIGGERS.stream().noneMatch(text.toLowerCase()::contains)) return;

        this.end = true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatMessage(this.message.getValue());
        }
    }

    @Listener
    public void onWorldChange(WorldLoadEvent event) {
        this.end = false;
    }

    @Override
    protected void onEnable() {
        this.end = false;
    }

    @Override
    protected void onDisable() {
        this.end = false;
    }
}
