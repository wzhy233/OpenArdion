package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.util.CommandSuggestions;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.value.base.Value;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class SetCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("set")
                .then(ClientCommandManager.argument("module", StringArgumentType.string())
                        .suggests(CommandSuggestions::modules)
                        .then(ClientCommandManager.argument("value", StringArgumentType.string())
                                .suggests(CommandSuggestions::values)
                                .then(ClientCommandManager.argument("newValue", StringArgumentType.greedyString())
                                        .suggests(CommandSuggestions::valueOptions)
                                        .executes(context -> {
                                            Module module = getModule(StringArgumentType.getString(context, "module"));
                                            String valueName = StringArgumentType.getString(context, "value");
                                            Value<?> value = module.getValue(valueName);
                                            if (value == null) {
                                                OutputUtil.error("Unknown value '" + valueName + "' for module " + module.getName());
                                                return 0;
                                            }

                                            String rawValue = StringArgumentType.getString(context, "newValue");
                                            try {
                                                value.setValueFromString(rawValue);
                                            } catch (IllegalArgumentException exception) {
                                                OutputUtil.error(exception.getMessage());
                                                return 0;
                                            }
                                            OutputUtil.info("Set " + module.getName() + "." + value.getName() + " to " + value.getValueAsString());
                                            return 1;
                                        }))));
    }

    @Override
    public String getUsage() {
        return "ardion set <module> <valuename> <value>";
    }

    @Override
    public String getDescription() {
        return "Update a module value.";
    }

    private Module getModule(String name) {
        Module module = ModuleManager.getModule(name);
        if (module == null) {
            throw new IllegalArgumentException("Unknown module: " + name);
        }
        return module;
    }
}
