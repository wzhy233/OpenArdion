package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.command.util.CommandSuggestions;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.util.input.KeyUtil;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import io.github.wzhy233.ardion.value.impl.StringValue;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class ListCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("list")
                .executes(context -> {
                    List<Module> modules = ModuleManager.getModules();
                    if (modules.isEmpty()) {
                        OutputUtil.warn("No modules registered.");
                        return 0;
                    }

                    OutputUtil.infoLines(formatModuleSummaryLines(modules));
                    return 1;
                })
                .then(ClientCommandManager.argument("module", StringArgumentType.string())
                        .suggests(CommandSuggestions::modules)
                        .executes(context -> {
                            String moduleName = StringArgumentType.getString(context, "module");
                            Module module = ModuleManager.getModule(moduleName);
                            if (module == null) {
                                OutputUtil.error("Unknown module: " + moduleName);
                                return 0;
                            }

                            OutputUtil.infoLines(formatModuleDetails(module));
                            return 1;
                        }));
    }

    @Override
    public String getUsage() {
        return "ardion list [module]";
    }

    @Override
    public String getDescription() {
        return "List all modules or inspect one module in detail.";
    }

    private List<Text> formatModuleSummaryLines(List<Module> modules) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Modules:"));
        for (Module module : modules) {
            MutableText line = Text.empty()
                    .append(OutputUtil.bullet())
                    .append(highlightModule(module.getName()));
            if (module.hasBind()) {
                line.append(Text.literal(" (" + KeyUtil.getKeyName(module.getBindKey()) + ")").formatted(Formatting.GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    private List<Text> formatModuleDetails(Module module) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Module: ").formatted(Formatting.GRAY).append(highlightModule(module.getName())));
        lines.add(Text.literal("Category: " + module.getCategory()).formatted(Formatting.GRAY));
        lines.add(Text.literal("Enabled: " + module.isEnabled()).formatted(Formatting.GRAY));
        lines.add(Text.literal("Bind: " + KeyUtil.getKeyName(module.getBindKey())).formatted(Formatting.GRAY));
        List<Value<?>> visibleValues = module.getValues().stream()
                .filter(ListCommand::isVisible)
                .toList();

        if (visibleValues.isEmpty()) {
            lines.add(Text.literal("Values: none").formatted(Formatting.GRAY));
            return lines;
        }

        lines.add(Text.literal("Values:").formatted(Formatting.GRAY));
        for (Value<?> value : visibleValues) {
            MutableText line = Text.empty()
                    .append(OutputUtil.bullet())
                    .append(highlightValue(value.getName()))
                    .append(Text.literal(" = " + value.getValueAsString()).formatted(Formatting.GRAY));
            lines.add(line);
        }
        return lines;
    }

    private Text highlightModule(String moduleName) {
        return Text.literal(moduleName).formatted(Formatting.YELLOW);
    }

    private Text highlightValue(String valueName) {
        return Text.literal(valueName).formatted(Formatting.GREEN);
    }

    private static boolean isVisible(Value<?> value) {
        if (value instanceof BooleanValue booleanValue) return booleanValue.isVisible();
        if (value instanceof NumberValue numberValue) return numberValue.isVisible();
        if (value instanceof ModeValue modeValue) return modeValue.isVisible();
        if (value instanceof StringValue stringValue) return stringValue.isVisible();
        return true;
    }
}
