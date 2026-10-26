package io.github.wzhy233.ardion.command.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class CommandSuggestions {
    private CommandSuggestions() {
    }

    public static CompletableFuture<Suggestions> modules(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        for (Module module : ModuleManager.getModules()) {
            suggest(builder, module.getName());
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> values(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        Module module = module(context);
        if (module == null) {
            return builder.buildFuture();
        }

        for (Value<?> value : module.getValues()) {
            suggest(builder, value.getName());
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> valueOptions(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        Module module = module(context);
        if (module == null) {
            return builder.buildFuture();
        }

        Value<?> value = module.getValue(StringArgumentType.getString(context, "value"));
        if (value instanceof BooleanValue) {
            suggest(builder, "true");
            suggest(builder, "false");
        } else if (value instanceof ModeValue modeValue) {
            for (String mode : modeValue.getModes()) {
                suggest(builder, mode);
            }
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> configNames(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        try {
            for (String name : ConfigManager.list()) {
                suggest(builder, name);
            }
        } catch (IOException ignored) {
        }
        return builder.buildFuture();
    }

    private static Module module(CommandContext<FabricClientCommandSource> context) {
        try {
            return ModuleManager.getModule(StringArgumentType.getString(context, "module"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void suggest(SuggestionsBuilder builder, String value) {
        if (value.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(value);
        }
    }
}
