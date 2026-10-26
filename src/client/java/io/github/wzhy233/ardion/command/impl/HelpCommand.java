package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.manager.CommandManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class HelpCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("help")
                .executes(context -> {
                    List<Text> lines = new ArrayList<>();
                    lines.add(Text.literal("Available commands:").formatted(Formatting.GRAY));
                    for (Command command : CommandManager.getCommands()) {
                        MutableText line = Text.empty()
                                .append(OutputUtil.bullet())
                                .append(Text.literal(command.getUsage()).formatted(Formatting.YELLOW))
                                .append(Text.literal(" : " + command.getDescription()).formatted(Formatting.GRAY));
                        lines.add(line);
                    }
                    OutputUtil.infoLines(lines);
                    return 1;
                });
    }

    @Override
    public String getUsage() {
        return "ardion help";
    }

    @Override
    public String getDescription() {
        return "Show all available commands.";
    }
}
