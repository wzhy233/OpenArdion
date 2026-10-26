package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.util.CommandSuggestions;
import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ConfigCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("config")
                .then(ClientCommandManager.literal("save")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    try {
                                        ConfigManager.save(name);
                                    } catch (IOException | IllegalArgumentException exception) {
                                        OutputUtil.error(exception.getMessage());
                                        return 0;
                                    }
                                    OutputUtil.info("Saved config: " + name);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("load")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .suggests(CommandSuggestions::configNames)
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    try {
                                        ConfigManager.load(name);
                                    } catch (IOException | IllegalArgumentException exception) {
                                        OutputUtil.error(exception.getMessage());
                                        return 0;
                                    }
                                    OutputUtil.info("Loaded config: " + name);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("list")
                        .executes(context -> {
                            List<String> names;
                            try {
                                names = ConfigManager.list();
                            } catch (IOException exception) {
                                OutputUtil.error(exception.getMessage());
                                return 0;
                            }

                            if (names.isEmpty()) {
                                OutputUtil.warn("No saved configs.");
                                return 0;
                            }

                            List<Text> lines = new ArrayList<>();
                            lines.add(Text.literal("Configs:").formatted(Formatting.GRAY));
                            for (String name : names) {
                                lines.add(Text.empty()
                                        .append(OutputUtil.bullet())
                                        .append(Text.literal(name).formatted(Formatting.WHITE)));
                            }
                            OutputUtil.infoLines(lines);
                            return 1;
                        }));
    }

    @Override
    public String getUsage() {
        return "ardion config save <name> | ardion config load <name> | ardion config list";
    }

    @Override
    public String getDescription() {
        return "Save, load, or list local configs.";
    }
}
