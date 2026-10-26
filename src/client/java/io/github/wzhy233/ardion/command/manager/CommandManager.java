package io.github.wzhy233.ardion.command.manager;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.impl.*;
import io.github.wzhy233.ardion.module.impl.misc.IRC;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;

public final class CommandManager {
    private static final List<Command> commands = new ArrayList<>();

    private CommandManager() {
    }

    public static void init() {
        commands.clear();
        commands.add(new HelpCommand());
        commands.add(new AiCommand());
        if (ModuleManager.getModule(IRC.class) != null) commands.add(new IrcCommand());
        commands.add(new ListCommand());
        commands.add(new ToggleCommand());
        commands.add(new BindCommand());
        commands.add(new BindsCommand());
        commands.add(new FriendCommand());
        commands.add(new SetCommand());
        commands.add(new ConfigCommand());
        commands.add(new NotebotCommand());
        commands.add(new WebUiCommand());
        commands.add(new HudEditorCommand());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("ardion");
            for (Command command : commands) {
                if (command instanceof AiCommand) dispatcher.register(command.build());
                else root.then(command.build());
            }
            dispatcher.register(root);
        });
    }

    public static List<Command> getCommands() {
        return List.copyOf(commands);
    }
}
