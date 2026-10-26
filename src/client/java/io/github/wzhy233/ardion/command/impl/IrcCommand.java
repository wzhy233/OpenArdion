package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.module.impl.misc.IRC;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class IrcCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("irc")
                .executes(context -> { module().showStatus(); return 1; })
                .then(ClientCommandManager.literal("connect").executes(context -> {
                    if (module().isEnabled()) module().reconnect();
                    else module().setEnabled(true);
                    return 1;
                }))
                .then(ClientCommandManager.literal("disconnect").executes(context -> {
                    module().setEnabled(false);
                    return 1;
                }))
                .then(ClientCommandManager.literal("reconnect").executes(context -> {
                    if (module().isEnabled()) module().reconnect();
                    else module().setEnabled(true);
                    return 1;
                }))
                .then(ClientCommandManager.literal("users").executes(context -> {
                    module().showUsers();
                    return 1;
                }))
                .then(ClientCommandManager.literal("send")
                        .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    module().send(StringArgumentType.getString(context, "message"));
                                    return 1;
                                })));
    }

    private static IRC module() {
        return ModuleManager.getModule(IRC.class);
    }

    @Override
    public String getUsage() {
        return "ardion irc [connect|disconnect|reconnect|users|send <message>]";
    }

    @Override
    public String getDescription() {
        return "IRC chat and connection ($message, $/command).";
    }
}
