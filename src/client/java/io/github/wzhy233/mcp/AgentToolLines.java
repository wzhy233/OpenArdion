package io.github.wzhy233.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class AgentToolLines {
    private final List<Row> rows = new ArrayList<>();
    private boolean sealed;

    void seal() {
        sealed = true;
    }

    void start(String tool) {
        String name = name(tool);
        if (explore(name)) {
            Row row = exploreRow();
            row.open++;
            row.mark = '*';
            return;
        }
        Row row = new Row(shell(name) ? "shell" : "tool", name);
        row.mark = '*';
        row.title = shell(name) ? "Ran …" : called(name);
        row.open = 1;
        rows.add(row);
    }

    void finish(JsonObject part) {
        String name = name(part != null && part.has("tool") ? part.get("tool").getAsString() : "tool");
        JsonObject state = part != null && part.has("state") && part.get("state").isJsonObject() ? part.getAsJsonObject("state") : null;
        String error = error(state);
        if (explore(name)) {
            Row row = exploreRow();
            if (row.open > 0) row.open--;
            String line = exploreLine(name, state);
            if (!line.isEmpty()) row.lines.add("  " + line);
            if (error != null) {
                row.failed = true;
                preview(row, error);
            }
            row.mark = row.open > 0 ? '*' : (row.failed ? '!' : '+');
            return;
        }
        Row row = takeOpen(name);
        if (row == null) {
            row = new Row(shell(name) ? "shell" : "tool", name);
            rows.add(row);
        }
        row.open = 0;
        row.lines.clear();
        if (shell(name)) {
            String command = text(state, "command");
            row.title = command.isEmpty() ? "Ran" : "Ran " + clip(command, 80);
        } else {
            row.title = called(name);
        }
        if (error != null) {
            row.mark = '!';
            preview(row, error);
            return;
        }
        row.mark = '+';
        preview(row, output(state));
    }

    String text() {
        StringBuilder out = new StringBuilder();
        for (Row row : rows) {
            if (!out.isEmpty()) out.append('\n');
            out.append(row.mark).append(' ').append(row.title);
            for (String line : row.lines) out.append('\n').append(line);
        }
        return out.toString();
    }

    private Row exploreRow() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (row.kind.equals("explore") && row.open > 0) return row;
        }
        if (!sealed && !rows.isEmpty() && rows.getLast().kind.equals("explore")) return rows.getLast();
        sealed = false;
        Row row = new Row("explore", "explore");
        row.title = "Explored";
        row.mark = '*';
        rows.add(row);
        return row;
    }

    private Row takeOpen(String name) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (row.open > 0 && name.equals(row.tool)) return row;
        }
        return null;
    }

    private static void preview(Row row, String text) {
        if (text == null || text.isBlank()) return;
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int start = 0;
        while (start < lines.length && lines[start].isBlank()) start++;
        int end = lines.length;
        while (end > start && lines[end - 1].isBlank()) end--;
        if (start >= end) return;
        row.lines.add("  " + clip(lines[start].strip(), 120));
        int extra = end - start - 1;
        if (extra > 0) row.lines.add("  + " + extra + (extra == 1 ? " line" : " lines"));
    }

    private static String exploreLine(String name, JsonObject state) {
        if (name.equals("read")) {
            String path = text(state, "path");
            return path.isEmpty() ? "Read" : "Read " + file(path);
        }
        String pattern = text(state, "pattern");
        return pattern.isEmpty() ? "Searched" : "Searched " + clip(pattern, 80);
    }

    private static String file(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? path : name;
    }

    private static String text(JsonObject state, String field) {
        if (state == null || !state.has("input") || !state.get("input").isJsonObject()) return "";
        JsonObject input = state.getAsJsonObject("input");
        if (!input.has(field) || !input.get(field).isJsonPrimitive()) return "";
        return input.get(field).getAsString().replaceAll("\\s+", " ").strip();
    }

    private static String output(JsonObject state) {
        if (state == null || !state.has("output") || state.get("output").isJsonNull()) return "";
        JsonElement output = state.get("output");
        return output.isJsonPrimitive() ? output.getAsString() : "";
    }

    private static String error(JsonObject state) {
        if (state == null || !state.has("error") || state.get("error").isJsonNull()) return null;
        JsonElement error = state.get("error");
        if (error.isJsonPrimitive()) return error.getAsString();
        if (error.isJsonObject() && error.getAsJsonObject().has("message")) return error.getAsJsonObject().get("message").getAsString();
        return null;
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }

    private static String called(String name) {
        String ascii = name.replaceAll("[^A-Za-z0-9_.:/-]", "");
        return "Called " + (ascii.isEmpty() ? "tool" : ascii);
    }

    private static String name(String tool) {
        return tool == null || tool.isBlank() ? "tool" : tool;
    }

    private static boolean shell(String name) {
        return name.equals("bash") || name.equals("shell");
    }

    private static boolean explore(String name) {
        return name.equals("read") || name.equals("grep") || name.equals("glob");
    }

    private static final class Row {
        private final String kind;
        private final String tool;
        private char mark = '*';
        private String title = "";
        private final List<String> lines = new ArrayList<>();
        private int open;
        private boolean failed;

        private Row(String kind, String tool) {
            this.kind = kind;
            this.tool = tool;
        }
    }
}
