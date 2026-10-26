package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.gui.screen.MusicPlayerScreen;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import net.minecraft.client.MinecraftClient;

public final class MusicPlayer extends Module {
    private static MusicPlayer instance;
    private final ModeValue lyricsStyle = this.registerValue(new ModeValue("lyrics-style", this, "sweep", "classic", "sweep"));

    public MusicPlayer() {
        super("music-player", ModuleCategory.CLIENT);
        instance = this;
    }

    public static boolean lyricSweep() {
        return instance == null || instance.lyricsStyle.is("sweep");
    }

    @Override
    protected void onEnable() {
        setEnabled(false);
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new MusicPlayerScreen()));
    }
}
