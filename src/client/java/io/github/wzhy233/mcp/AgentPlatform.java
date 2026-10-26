package io.github.wzhy233.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AgentPlatform {
    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static String executableSuffix() throws IOException {
        String arch = System.getProperty("os.arch", "");
        if (!(arch.equals("amd64") || arch.equals("x86_64"))) throw new IOException("Ardion agent requires x86_64");
        if (windows()) return ".exe";
        if (System.getProperty("os.name", "").equalsIgnoreCase("Linux")) return "";
        throw new IOException("Ardion agent supports Windows and Linux x86_64 (glibc)");
    }

    static void makeExecutable(Path file) throws IOException {
        if (!windows()) Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
    }

    static List<String> consoleCommand(Path launcher, Path directory) throws IOException {
        if (windows()) {
            for (String text : List.of(launcher.toString(), directory.toString())) {
                if (text.chars().anyMatch(c -> c == '"' || c == '\n' || c == '\r' || c == '&')) {
                    throw new IOException("Agent path is not usable");
                }
            }
            return List.of("cmd.exe", "/c", "start", "Ardion Agent", "/wait", "/D", directory.toString(), launcher.toString());
        }
        return linuxConsoleCommand(launcher, System.getenv("PATH"));
    }

    static List<String> linuxConsoleCommand(Path launcher, String searchPath) throws IOException {
        for (var terminal : List.of(
                List.of("gnome-terminal", "--wait", "--"),
                List.of("konsole", "--nofork", "-e"),
                List.of("xfce4-terminal", "--disable-server", "-x"),
                List.of("kitty", "--"),
                List.of("alacritty", "-e"),
                List.of("xterm", "-e"),
                List.of("x-terminal-emulator", "-e"))) {
            for (String entry : (searchPath == null ? "" : searchPath).split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                Path executable = Path.of(entry).resolve(terminal.getFirst());
                if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) continue;
                var command = new ArrayList<>(terminal);
                command.set(0, executable.toString());
                command.add(launcher.toString());
                return command;
            }
        }
        throw new IOException("No Linux terminal found. Install gnome-terminal, konsole, xfce4-terminal, kitty, alacritty or xterm.");
    }
}
