package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.util.CommandSuggestions;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.util.input.KeyUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class BindCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("bind")
                .then(ClientCommandManager.literal("clear")
                        .then(ClientCommandManager.argument("module", StringArgumentType.string())
                                .suggests(CommandSuggestions::modules)
                                .executes(context -> {
                                    Module module = getModule(StringArgumentType.getString(context, "module"));
                                    module.clearBindKey();
                                    OutputUtil.info("Cleared bind for " + module.getName());
                                    return 1;
                                })))
                .then(ClientCommandManager.argument("module", StringArgumentType.string())
                        .suggests(CommandSuggestions::modules)
                        .then(ClientCommandManager.argument("key", StringArgumentType.greedyString())
                                .executes(context -> {
                                    Module module = getModule(StringArgumentType.getString(context, "module"));
                                    int keyCode = KeyUtil.parseKey(StringArgumentType.getString(context, "key"));
                                    module.setBindKey(keyCode);
                                    OutputUtil.info("Bound " + module.getName() + " to " + KeyUtil.getKeyName(keyCode));
                                    return 1;
                                })));
    }

    @Override
    public String getUsage() {
        return "ardion bind <module> <key> | ardion bind clear <module>";
    }

    @Override
    public String getDescription() {
        return "Set or clear a module keybind.";
    }

    private Module getModule(String name) {
        Module module = ModuleManager.getModule(name);
        if (module == null) {
            throw new IllegalArgumentException("Unknown module: " + name);
        }
        return module;
    }
}
