package io.github.wzhy233.ardion.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.wzhy233.ardion.command.base.Command;
import io.github.wzhy233.ardion.manager.FriendManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendCommand implements Command {
    @Override
    public LiteralArgumentBuilder<FabricClientCommandSource> build() {
        var root = ClientCommandManager.literal("friend").executes(context -> manage("list", ""));
        for (String action : List.of("add", "remove")) {
            root.then(ClientCommandManager.literal(action)
                    .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .suggests((context, builder) -> suggestions(action.equals("remove"), builder))
                            .executes(context -> manage(action, StringArgumentType.getString(context, "player")))));
        }
        return root.then(ClientCommandManager.literal("list").executes(context -> manage("list", "")))
                .then(ClientCommandManager.literal("clear").executes(context -> manage("clear", "")));
    }

    private int manage(String action, String name) {
        try {
            switch (action) {
                case "list" -> {
                    var friends = FriendManager.list();
                    OutputUtil.infoChatOnly("Friends: " + friends.size());
                    friends.forEach((uuid, player) -> OutputUtil.infoChatOnly(player + " (" + uuid + ")"));
                }
                case "clear" -> {
                    FriendManager.clear();
                    OutputUtil.infoChatOnly("Cleared friends list");
                }
                case "add" -> {
                    UUID uuid = resolve(name, false);
                    var handler = MinecraftClient.getInstance().getNetworkHandler();
                    var entry = handler == null ? null : handler.getPlayerListEntry(uuid);
                    String display = entry == null ? name : entry.getProfile().name();
                    FriendManager.add(uuid, display);
                    OutputUtil.infoChatOnly("Added " + display + " to friends list");
                }
                case "remove" -> {
                    if (!FriendManager.remove(resolve(name, true))) throw new IllegalArgumentException("Not a friend: " + name);
                    OutputUtil.infoChatOnly("Removed " + name + " from friends list");
                }
            }
            return 1;
        } catch (IOException | RuntimeException exception) {
            OutputUtil.error(exception.getMessage());
            return 0;
        }
    }

    private UUID resolve(String name, boolean saved) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
        }
        if (saved) {
            var matches = FriendManager.list().entrySet().stream()
                    .filter(entry -> entry.getValue().equalsIgnoreCase(name)).toList();
            if (matches.size() == 1) return matches.getFirst().getKey();
            if (matches.size() > 1) throw new IllegalArgumentException("Duplicate friend name; use the UUID from friend list");
        } else {
            var handler = MinecraftClient.getInstance().getNetworkHandler();
            if (handler != null) {
                for (var entry : handler.getPlayerList()) {
                    if (entry.getProfile().name().equalsIgnoreCase(name)) return entry.getProfile().id();
                }
            }
        }
        throw new IllegalArgumentException(saved ? "Not a friend: " + name : "Player is not online; provide their UUID instead");
    }

    private CompletableFuture<Suggestions> suggestions(boolean saved, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        if (saved) {
            FriendManager.list().forEach((uuid, name) -> {
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(name);
                if (uuid.toString().startsWith(remaining)) builder.suggest(uuid.toString());
            });
        } else {
            var handler = MinecraftClient.getInstance().getNetworkHandler();
            if (handler != null) handler.getPlayerList().forEach(entry -> {
                String name = entry.getProfile().name();
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(name);
            });
        }
        return builder.buildFuture();
    }

    @Override
    public String getUsage() {
        return "ardion friend add <player|uuid> | remove <player|uuid> | list | clear";
    }

    @Override
    public String getDescription() {
        return "Manage saved friends shared with MCF. Names for add must be online; UUIDs work offline.";
    }
}
