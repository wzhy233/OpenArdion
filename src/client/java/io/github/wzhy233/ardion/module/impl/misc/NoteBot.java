package io.github.wzhy233.ardion.module.impl.misc;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.notebot.File;
import io.github.wzhy233.ardion.notebot.Service;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;

import java.io.IOException;

public final class NoteBot extends Module {
    private final BooleanValue loop = this.registerValue(new BooleanValue("loop", this, false));
    private final BooleanValue harmony = this.registerValue(new BooleanValue("harmony", this, true));
    private final BooleanValue verboseOutput = this.registerValue(new BooleanValue("verbose-output", this, false));
    private final NumberValue tuneDelay = this.registerValue(new NumberValue("tune-delay", this, 5, 0, 10, true));

    public NoteBot() {
        super("note-bot", ModuleCategory.MISC);
    }

    @Override
    protected void onEnable() {
        try {
            File.ensureDirectories();
        } catch (IOException exception) {
            OutputUtil.error("Failed to initialize notebot directories: " + exception.getMessage());
            this.setEnabled(false);
            return;
        }

        Service.setLoop(this.loop.getValue());
        Service.setHarmony(this.harmony.getValue());
        Service.setVerboseOutput(this.verboseOutput.getValue());
        if (this.verboseOutput.getValue()) {
            OutputUtil.info("NoteBot songs directory: " + Service.getSongsDirectory());
        }
    }

    @Override
    protected void onDisable() {
        Service.stop();
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.POST) {
            return;
        }

        Service.setLoop(this.loop.getValue());
        Service.setHarmony(this.harmony.getValue());
        Service.setVerboseOutput(this.verboseOutput.getValue());
        Service.setTuneDelayThreshold(this.tuneDelay.getValue().intValue());
        Service.onTick(event.getClient());
    }
}
