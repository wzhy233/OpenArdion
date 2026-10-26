package io.github.wzhy233.ardion.command.base;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public interface Command {
    LiteralArgumentBuilder<FabricClientCommandSource> build();

    String getUsage();

    String getDescription();
}
