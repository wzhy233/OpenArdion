package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.util.client.ArdionHome;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentRuntime {
    private AgentRuntime() {
    }

    static Path install(Path directory) throws IOException {
        return install(directory, AgentRuntime.class.getClassLoader());
    }

    static Path install(Path directory, ClassLoader resources) throws IOException {
        String suffix = AgentPlatform.executableSuffix();
        String prefix = "assets/ardion/native/ardion-engine";
        String hash;
        try (var input = resources.getResourceAsStream(prefix + ".sha256")) {
            if (input == null) throw new IOException("Bundled Ardion agent is missing; rebuild the mod with buildAgent");
            hash = new String(input.readNBytes(256), java.nio.charset.StandardCharsets.US_ASCII).strip();
        }
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid bundled agent checksum");
        Files.createDirectories(directory);
        Path executable = directory.resolve("ardion-engine-" + hash.substring(0, 16) + suffix).toAbsolutePath();
        if (Files.isRegularFile(executable) && digest(executable).equals(hash)) {
            AgentPlatform.makeExecutable(executable);
            return executable;
        }
        Path temp = Files.createTempFile(directory, "ardion-engine-", ".tmp");
        try {
            try (var input = resources.getResourceAsStream(prefix + suffix)) {
                if (input == null) throw new IOException("Bundled Ardion agent executable is missing");
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!digest(temp).equals(hash)) throw new IOException("Bundled Ardion agent checksum mismatch");
            AgentPlatform.makeExecutable(temp);
            Files.move(temp, executable, StandardCopyOption.REPLACE_EXISTING);
            return executable;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String digest(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    record Prepared(Path engine, Path directory, Path game, Map<String, String> environment) { }

    static Prepared prepare(String channel) throws IOException {
        Path home = ArdionHome.root();
        Path directory = home.resolve("agent");
        Path engine = install(directory);
        Path voyager = VoyagerRuntime.install(directory);
        AgentConsole.installGamePrograms(home, directory);
        McpServer.Endpoint endpoint = McpServer.start();
        Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        Path instructions = directory.resolve("minecraft-" + channel + ".md");
        String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        Files.writeString(instructions, "# Ardion agent\n\nMinecraft Java Edition " + version + "\n"
                + "You control the live Minecraft player and Ardion through the minecraft MCP. Choose observations according to the task; no mandatory initial status call. "
                + "The minecraft MCP and Voyager target client work in a private singleplayer world without Open to LAN. LAN is only needed for a separate Mineflayer bot to join an integrated server. Never ask to open LAN to repair current-player MCP errors. A client class/loading incompatibility requires restarting the updated Minecraft client, not repeated calls to different tools. "
                + "When the user explicitly asks to summon Alex into their current singleplayer world, call voyager with target bot, action connect, args [{currentWorld:true}]. This automatically prepares LAN through the live client, preserves game mode and cheats, obtains the actual port and connects Alex. Do not ask the user to open LAN manually, guess a port, or use a saved remote server config for that request. Verify the returned spawned bot observation. Existing LAN is reused. Never open LAN for target client. "
                + "Use the voyager MCP for executable Minecraft behaviors. Before any Voyager operation the user must explicitly choose target client (their current Ardion player) or bot (a separate Mineflayer login) for this task. If unspecified, ask whether to control their current player or create independent bot Alex, and wait; never infer a target from config, an existing bot or an unrelated task. Keep an explicit choice within that workflow and never silently switch players. Offline bots are named Alex; Microsoft servers display the authenticated account's profile name, so explain that exception before that login. Load the voyager skill for programs, observations and reusable skills. Raw keyboard/mouse simulation is retired. Modules can be enabled or disabled and their settings changed, but module key bindings must not change. Debugging remains read-only; no JVM evaluation, writes or hot replacement. "
                + "The lua tool writes, checks, loads and hot-reloads scripts under the Ardion lua directory without restarting. Call lua action api before writing. A script may be repaired at most 5 times. Lua has full JVM access through luajava.bindClass, including private fields and methods, and can send packets by calling the game. "
                + "If the target is missing, ask in a normal reply and end this workflow to wait for the user. An unavailable or cancelled question tool is never permission to guess a player. Do not narrate tool calls; the client shows them in English. "
                + "You have full authorization to manage Ardion module parameters and enabled state through ardion_modules, global settings through ardion_settings, and MusicPlayer through ardion_music, including search, playback, playlists, volume, seeking and account actions. Carry out the user's requested operations without asking for permission again. These ecosystem operations address Ardion itself and do not require a Voyager player/bot choice. Discover valid settings and ranges, apply changes, and verify the returned results; never change module key bindings. "
                + "Verify game/Ardion changes through their actual results. Use ARDION_GAME_DIR for game files and ARDION_SKILLS_DIR for verified persistent skills. "
                + "This is the " + channel + " conversation. AI Chat and CLI have independent histories; never retrieve or import the other channel's conversation. Reply in the user's language. "
                + (channel.equals("chat") ? "Output appears in vanilla chat; use concise plain text. You retain the full agent tools and automatic context compaction.\n" : "\n"));
        var env = environment(endpoint, directory, game, instructions, channel);
        env.put("ARDION_VOYAGER_SERVER", voyager.resolve("server.mjs").toString());
        env.put("ARDION_VOYAGER_API", directory.resolve("voyager-client.mjs").toUri().toString());
        return new Prepared(engine, directory, game, env);
    }

    static Map<String, String> environment(McpServer.Endpoint endpoint, Path directory, Path game, Path instructions, String channel) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("ARDION_HOME", directory.getParent().toAbsolutePath().toString());
        env.put("ARDION_CHANNEL", channel);
        env.put("ARDION_CONFIG_CONTENT", config(endpoint.url(), directory.getParent(), instructions, channel).toString());
        env.put("ARDION_GAME_DIR", game.toAbsolutePath().toString());
        env.put("ARDION_MCP_URL", endpoint.url());
        env.put("ARDION_MCP_TOKEN", endpoint.token());
        env.put("ARDION_MINECRAFT_API", directory.resolve("minecraft.mjs").toAbsolutePath().toUri().toString());
        env.put("ARDION_SKILLS_DIR", directory.getParent().resolve("skills").toAbsolutePath().toString());
        return env;
    }

    static JsonObject config(String endpoint, Path home, Path instructions) {
        return config(endpoint, home, instructions, "cli");
    }

    static JsonObject config(String endpoint, Path home, Path instructions, String channel) {
        JsonObject config = new JsonObject();
        config.addProperty("default_agent", "ardion");
        config.addProperty("share", "disabled");
        config.addProperty("update", "disable");
        JsonObject experimental = new JsonObject(); experimental.addProperty("todo", true); config.add("experimental", experimental);
        JsonObject compaction = new JsonObject();
        compaction.addProperty("auto", true);
        if (channel.equals("chat")) compaction.addProperty("buffer", 168_000);
        config.add("compaction", compaction);
        JsonObject agent = new JsonObject(); agent.addProperty("mode", "primary");
        agent.addProperty("description", "Ardion complete Minecraft and client ecosystem agent");
        JsonArray permissions = new JsonArray();
        for (String tool : List.of("ardion_modules", "ardion_settings", "ardion_music", "prepare_bot", "lua")) {
            JsonObject rule = new JsonObject();
            rule.addProperty("action", "minecraft_" + tool);
            rule.addProperty("resource", "*");
            rule.addProperty("effect", "allow");
            permissions.add(rule);
        }
        agent.add("permissions", permissions);
        JsonObject agents = new JsonObject(); agents.add("ardion", agent); config.add("agents", agents);
        JsonArray files = new JsonArray(); files.add(instructions.toAbsolutePath().toString()); config.add("instructions", files);
        JsonArray skills = new JsonArray(); skills.add(home.resolve("skills").toAbsolutePath().toString()); config.add("skills", skills);
        JsonObject remote = new JsonObject(); remote.addProperty("type", "remote"); remote.addProperty("url", endpoint);
        remote.addProperty("oauth", false); remote.addProperty("codemode", false);
        JsonObject headers = new JsonObject(); headers.addProperty("Authorization", "Bearer {env:ARDION_MCP_TOKEN}"); remote.add("headers", headers);
        JsonObject servers = new JsonObject(); servers.add("minecraft", remote);
        JsonObject voyager = new JsonObject(); voyager.addProperty("type", "local"); voyager.addProperty("codemode", false);
        JsonArray command = new JsonArray(); command.add("{env:ARDION_ENGINE_EXE}"); command.add("minecraft"); command.add("{env:ARDION_VOYAGER_SERVER}");
        voyager.add("command", command); servers.add("voyager", voyager);
        JsonObject mcp = new JsonObject(); mcp.add("servers", servers); config.add("mcp", mcp);
        JsonObject websearch = new JsonObject(); websearch.addProperty("provider", "exa"); config.add("websearch", websearch);
        return config;
    }
}
