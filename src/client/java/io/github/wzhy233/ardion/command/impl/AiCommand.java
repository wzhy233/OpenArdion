package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.ai.AiService;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.mcp.AgentConsole;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;


public final class AiCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("ai")
                .executes(context -> {
                    OutputUtil.infoChatOnly("Use /ai <message>, /ai stop, or /ai open-cli. Configure the native agent with /connect and /models in its CLI.");
                    return 1;
                })
                .then(ClientCommandManager.literal("cancel").executes(context -> {
                    AiService.cancel();
                    return 1;
                }))
                .then(ClientCommandManager.literal("stop").executes(context -> {
                    AiService.cancel();
                    return 1;
                }))
                .then(ClientCommandManager.literal("open-cli").executes(context -> {
                    Thread.startVirtualThread(() -> {
                        String message;
                        boolean opened;
                        try {
                            AgentConsole.open();
                            message = "Agent console opened";
                            opened = true;
                        } catch (Exception exception) {
                            String detail = exception.getMessage();
                            message = detail == null || detail.isBlank() ? "Could not open the agent console" : detail;
                            opened = false;
                        }
                        String text = message;
                        boolean success = opened;
                        MinecraftClient.getInstance().execute(() -> {
                            if (success) OutputUtil.infoChatOnly(text);
                            else OutputUtil.error(text);
                        });
                    });
                    return 1;
                }))
                .then(ClientCommandManager.argument("content", StringArgumentType.greedyString())
                        .executes(context -> {
                            AiService.ask(StringArgumentType.getString(context, "content"));
                            return 1;
                        }));
    }

    @Override
    public String getUsage() {
        return "ai <content> | ai stop | ai open-cli";
    }

    @Override
    public String getDescription() {
        return "Use the complete agent in chat or its independent CLI conversation.";
    }
}
