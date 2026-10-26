package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.util.CommandSuggestions;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ToggleCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("toggle")
                .then(ClientCommandManager.argument("module", StringArgumentType.string())
                        .suggests(CommandSuggestions::modules)
                        .executes(context -> {
                            String moduleName = StringArgumentType.getString(context, "module");
                            Module module = ModuleManager.getModule(moduleName);
                            if (module == null) {
                                OutputUtil.error("Unknown module: " + moduleName);
                                return 0;
                            }
                            module.toggle();
                            return 1;
                        }));
    }

    @Override
    public String getUsage() {
        return "ardion toggle <module>";
    }

    @Override
    public String getDescription() {
        return "Toggle a module on or off.";
    }
}
