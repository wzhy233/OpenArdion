package io.github.wzhy233.mcp;

import io.github.wzhy233.ardion.util.client.ArdionHome;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AgentConsole {
    private static Process console;

    private AgentConsole() {
    }

    public static synchronized void open() throws IOException {
        if (console != null && console.isAlive()) throw new IOException("Agent console is already open");
        AgentRuntime.Prepared prepared = AgentRuntime.prepare("cli");
        Path directory = prepared.directory();
        Path runtime = prepared.engine();
        String suffix = AgentPlatform.executableSuffix();
        Path exe = directory.resolve("ardion-agent" + suffix);
        if (!install(exe)) {
            exe = directory.resolve("ardion-agent-" + ProcessHandle.current().pid() + suffix);
            if (!install(exe)) throw new IOException("Native agent launcher is not built");
        }
        ProcessBuilder builder = new ProcessBuilder(AgentPlatform.consoleCommand(exe.toAbsolutePath(), directory.toAbsolutePath()));
        builder.directory(prepared.game().toFile());
        builder.environment().remove("OPENCODE_CONFIG");
        builder.environment().putAll(prepared.environment());
        builder.environment().put("ARDION_ENGINE_EXE", runtime.toString());
        builder.environment().put("ARDION_PARENT_PID", Long.toString(ProcessHandle.current().pid()));
        builder.environment().put("ARDION_CLI_LOCK", directory.resolve(".cli.lock").toAbsolutePath().toString());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(directory.resolve("console-last-error.log").toFile());
        Process started = builder.start();
        console = started;
        net.minecraft.client.MinecraftClient.getInstance().execute(GameActionTools::consoleOpened);
        started.onExit().thenRun(() -> net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
            synchronized (AgentConsole.class) {
                if (console == started) GameActionTools.consoleClosed();
            }
        }));
    }

    static void installGamePrograms(Path home, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path skill = home.resolve("skills/minecraft/SKILL.md");
        Files.createDirectories(skill.getParent());
        for (String name : new String[]{"minecraft.mjs", "voyager-client.mjs", "minecraft-skill.md", "voyager-skill.md"}) {
            Path target = name.equals("voyager-skill.md") ? home.resolve("skills/voyager/SKILL.md") : name.endsWith(".mjs") ? directory.resolve(name) : skill;
            Files.createDirectories(target.getParent());
            if (name.endsWith("-skill.md") && Files.exists(target)) {
                if (!name.equals("voyager-skill.md") || !java.util.Set.of(
                        "64486f682de043b431179724b2e60ba8a86315a155cfd3de4d1353e3e3f8cccb",
                        "6d2430aa334482931028ee31e7a0804906fe6a00e62ea26d3caefa65a7cd73c1",
                        "f60537517556b78fcb4ddb2e025176f0741da0f02be2d399836c300534198a3a").contains(AgentRuntime.digest(target))) continue;
            }
            try (InputStream input = AgentConsole.class.getClassLoader().getResourceAsStream("assets/ardion/agent/" + name)) {
                if (input == null) throw new IOException("Bundled Minecraft program API is missing");
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static boolean install(Path exe) {
        try {
            String name = "ardion-agent" + AgentPlatform.executableSuffix();
            try (InputStream input = AgentConsole.class.getClassLoader().getResourceAsStream("assets/ardion/native/" + name)) {
                if (input != null) {
                    Path temp = exe.resolveSibling(exe.getFileName().toString() + ".tmp");
                    Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                    AgentPlatform.makeExecutable(temp);
                    Files.move(temp, exe, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            }
            Path dev = Path.of(System.getProperty("user.dir", "."), "natives", AgentPlatform.windows() ? "build" : "build-linux", name);
            if (!Files.isRegularFile(dev)) return false;
            Files.copy(dev, exe, StandardCopyOption.REPLACE_EXISTING);
            AgentPlatform.makeExecutable(exe);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
