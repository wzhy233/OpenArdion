package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.gui.screen.HudEditorScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public final class HudEditorCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("hud-editor")
                .executes(context -> {
                    MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new HudEditorScreen()));
                    return 1;
                });
    }

    @Override
    public String getUsage() {
        return "ardion hud-editor";
    }

    @Override
    public String getDescription() {
        return "Open the HUD element editor.";
    }
}
