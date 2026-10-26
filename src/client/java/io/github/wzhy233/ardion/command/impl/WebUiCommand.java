package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.web.WebServer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Util;

import java.net.URI;

public final class WebUiCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("webui")
                .executes(context -> {
                    String url = WebServer.getUrl();
                    try {
                        WebServer.start();
                    } catch (Exception exception) {
                        OutputUtil.error(exception.getMessage());
                        return 0;
                    }
                    try {
                        Util.getOperatingSystem().open(URI.create(url));
                    } catch (Exception exception) {
                        OutputUtil.error("Failed to open browser: " + exception.getMessage());
                        OutputUtil.info("Open manually: " + url);
                        return 0;
                    }

                    OutputUtil.info("Opening Web UI: " + url);
                    return 1;
                });
    }

    @Override
    public String getUsage() {
        return "ardion webui";
    }

    @Override
    public String getDescription() {
        return "Open the local Web UI in your browser.";
    }
}
