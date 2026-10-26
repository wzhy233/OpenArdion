package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.module.impl.misc.NoteBot;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.notebot.Service;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class NotebotCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommandManager.literal("notebot")
                .executes(context -> {
                    this.sendInfo();
                    return 1;
                })
                .then(ClientCommandManager.literal("start")
                        .executes(context -> {
                            NoteBot module = ModuleManager.getModule(NoteBot.class);
                            if (module != null && !module.isEnabled()) {
                                module.setEnabled(true);
                            }
                            Service.start();
                            return 1;
                        }))
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            Service.stop();
                            return 1;
                        }))
                .then(ClientCommandManager.literal("queue")
                        .executes(context -> {
                            this.sendQueue();
                            return 1;
                        })
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("song", StringArgumentType.greedyString())
                                        .suggests(this::suggestSongs)
                                        .executes(context -> Service.enqueue(StringArgumentType.getString(context, "song")) ? 1 : 0)))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(0))
                                        .executes(context -> Service.removeAt(IntegerArgumentType.getInteger(context, "index")) ? 1 : 0)))
                        .then(ClientCommandManager.literal("clear")
                                .executes(context -> {
                                    Service.clearQueue();
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("songs")
                        .executes(context -> {
                            this.sendSongs();
                            return 1;
                        }))
                .then(ClientCommandManager.literal("refresh")
                        .executes(context -> {
                            Service.refreshSongs();
                            OutputUtil.infoChatOnly("NoteBot songs refreshed");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("loop")
                        .executes(context -> {
                            Service.setLoop(!Service.isLoop());
                            OutputUtil.infoChatOnly("NoteBot loop: " + Service.isLoop());
                            return 1;
                        }));
    }

    @Override
    public String getUsage() {
        return "ardion notebot <start|stop|queue|songs|refresh|loop>";
    }

    @Override
    public String getDescription() {
        return "Manage notebot playback and song queue.";
    }

    private void sendInfo() {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("NoteBot").formatted(Formatting.GRAY));
        lines.add(Text.empty().append(OutputUtil.bullet()).append(Text.literal("playing: " + Service.isPlaying()).formatted(Formatting.YELLOW)));
        lines.add(Text.empty().append(OutputUtil.bullet()).append(Text.literal("loop: " + Service.isLoop()).formatted(Formatting.YELLOW)));
        if (Service.getSong() != null) {
            lines.add(Text.empty().append(OutputUtil.bullet()).append(Text.literal("current: " + Service.getSong().name()).formatted(Formatting.YELLOW)));
        }
        OutputUtil.infoLines(lines);
    }

    private void sendQueue() {
        List<String> queue = Service.getQueue();
        if (queue.isEmpty()) {
            OutputUtil.info("NoteBot queue is empty");
            return;
        }

        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("NoteBot queue").formatted(Formatting.GRAY));
        for (int i = 0; i < queue.size(); i++) {
            lines.add(Text.empty().append(OutputUtil.bullet()).append(Text.literal(i + ": " + queue.get(i)).formatted(Formatting.YELLOW)));
        }
        OutputUtil.infoLines(lines);
    }

    private void sendSongs() {
        List<String> songs = Service.listAvailableSongs();
        if (songs.isEmpty()) {
            if (Service.isVerboseOutput()) {
                OutputUtil.info("No songs found in " + Service.getSongsDirectory());
            } else {
                OutputUtil.info("No songs found.");
            }
            return;
        }

        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Available songs").formatted(Formatting.GRAY));
        for (String song : songs) {
            lines.add(Text.empty().append(OutputUtil.bullet()).append(Text.literal(song).formatted(Formatting.YELLOW)));
        }
        OutputUtil.infoLines(lines);
    }

    private CompletableFuture<Suggestions> suggestSongs(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        for (String song : Service.listAvailableSongs()) {
            if (song.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(song);
            }
        }
        return builder.buildFuture();
    }

    private String enabledState() {
        NoteBot module = ModuleManager.getModule(NoteBot.class);
        return module != null && module.isEnabled() ? "enabled" : "disabled";
    }
}
