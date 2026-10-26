package io.github.wzhy233.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

public final class AgentChat {
    private static volatile Process current;
    private static java.nio.file.Path currentStop;
    private static long generation;
    private static final String session = "ses_" + java.util.UUID.randomUUID().toString().replace("-", "");

    public static void run(String prompt, Consumer<String> thinking, Consumer<String> answer) throws Exception {
        run(prompt, thinking, answer, () -> {});
    }

    public static void run(String prompt, Consumer<String> thinking, Consumer<String> answer, Runnable waiting) throws Exception {
        run(prompt, thinking, answer, waiting, text -> {});
    }
    public static void run(String prompt, Consumer<String> thinking, Consumer<String> answer, Runnable waiting, Consumer<String> activity) throws Exception {
        long started;
        synchronized (AgentChat.class) { started = generation; }
        AgentRuntime.Prepared prepared = AgentRuntime.prepare("chat");
        var workspace = prepared.directory().resolve("chat");
        Files.createDirectories(workspace);
        var errors = prepared.directory().resolve("chat-last-error.log");
        ProcessBuilder builder = new ProcessBuilder(prepared.engine().toString(), "run", "--standalone", "--session", session, "--auto", "--thinking", "--format", "json", "--", prompt);
        builder.directory(workspace.toFile());
        builder.environment().putAll(prepared.environment());
        builder.environment().put("ARDION_ENGINE_EXE", prepared.engine().toString());
        var stopFile = prepared.directory().resolve("chat-stop-" + java.util.UUID.randomUUID());
        builder.environment().put("ARDION_CHAT_STOP", stopFile.toString());
        builder.redirectError(errors.toFile());
        Process process;
        synchronized (AgentChat.class) {
            if (generation != started || Thread.currentThread().isInterrupted()) throw new InterruptedException("AI Chat stopped");
            process = builder.start();
            current = process;
            currentStop = stopFile;
        }
        net.minecraft.client.MinecraftClient.getInstance().execute(GameActionTools::holdFocus);
        process.getOutputStream().close();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            AgentEventStream.read(reader, thinking, answer, waiting, activity);
            if (process.waitFor() != 0) throw new IOException("Agent exited with an error; inspect " + errors + ". Configure its model in /ai open-cli using /connect and /models.");
        } catch (Exception failure) {
            synchronized (AgentChat.class) { if (generation != started) throw new InterruptedException("AI Chat stopped"); }
            throw failure;
        } finally {
            terminate(process);
            synchronized (AgentChat.class) { if (current == process) { current = null; currentStop = null; } }
            Files.deleteIfExists(stopFile);
            net.minecraft.client.MinecraftClient.getInstance().execute(GameActionTools::releaseFocus);
        }
    }

    public static void stop() {
        Process process;
        java.nio.file.Path stopFile;
        synchronized (AgentChat.class) { generation++; process = current; stopFile = currentStop; }
        if (process == null) return;
        Thread.startVirtualThread(() -> {
            var children = process.descendants().toList();
            try {
                Files.writeString(stopFile, "stop");
                process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (IOException | InterruptedException ignored) { }
            children.reversed().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
            terminate(process);
            try { Files.deleteIfExists(stopFile); } catch (IOException ignored) { }
        });
        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
            GameActionTools.cancel("AI Chat stopped");
            GameMovement.release();
        });
    }
    private static void terminate(Process process) {
        if (!process.isAlive()) return;
        var descendants = process.descendants().toList();
        descendants.reversed().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
        process.destroyForcibly();
    }
}
