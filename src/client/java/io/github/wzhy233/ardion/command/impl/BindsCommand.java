package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.util.input.KeyUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BindsCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("binds")
                .executes(context -> {
                    List<Module> boundModules = ModuleManager.getModules().stream()
                            .filter(Module::hasBind)
                            .sorted(Comparator.comparing(Module::getName))
                            .toList();

                    if (boundModules.isEmpty()) {
                        OutputUtil.warn("No module binds set.");
                        return 0;
                    }

                    OutputUtil.infoLines(formatBindLines(boundModules));
                    return 1;
                });
    }

    @Override
    public String getUsage() {
        return "ardion binds";
    }

    @Override
    public String getDescription() {
        return "List all module keybinds.";
    }

    private List<Text> formatBindLines(List<Module> modules) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Binds:"));
        for (Module module : modules) {
            MutableText line = Text.empty()
                    .append(OutputUtil.bullet())
                    .append(Text.literal(module.getName()).formatted(Formatting.YELLOW))
                    .append(Text.literal(" -> ").formatted(Formatting.GRAY))
                    .append(Text.literal(KeyUtil.getKeyName(module.getBindKey())).formatted(Formatting.GREEN));
            lines.add(line);
        }
        return lines;
    }
}
