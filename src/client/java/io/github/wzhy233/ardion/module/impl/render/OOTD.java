package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.web.WebServer;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.net.URI;

public final class OOTD extends Module {
    public OOTD() {
        super("ootd", ModuleCategory.RENDER);
    }

    @Override
    protected void onEnable() {
        WebServer.start();
        try {
            Util.getOperatingSystem().open(URI.create(WebServer.getOotdUrl()));
        } catch (Exception exception) {
            OutputUtil.error("Failed to open OOTD: " + exception.getMessage());
        }

        MinecraftClient.getInstance().execute(() -> {
            if (this.isEnabled()) {
                this.setEnabled(false);
            }
        });
    }
}
