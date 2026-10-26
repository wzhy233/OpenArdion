package io.github.wzhy233.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

final class AgentEventStream {
    static void read(BufferedReader reader, Consumer<String> thinking, Consumer<String> answer) throws IOException {
        read(reader, thinking, answer, () -> {});
    }
    static void read(BufferedReader reader, Consumer<String> thinking, Consumer<String> answer, Runnable waiting) throws IOException {
        read(reader, thinking, answer, waiting, text -> {});
    }
    static void read(BufferedReader reader, Consumer<String> thinking, Consumer<String> answer, Runnable waiting, Consumer<String> activity) throws IOException {
        AgentToolLines tools = new AgentToolLines();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("{")) continue;
            JsonObject event;
            try { event = JsonParser.parseString(line).getAsJsonObject(); }
            catch (RuntimeException failure) { throw new IOException("Invalid agent event", failure); }
            String type = event.has("type") ? event.get("type").getAsString() : "";
            if (type.equals("waiting")) { waiting.run(); continue; }
            if (type.equals("error")) {
                var error = event.getAsJsonObject("error");
                throw new IOException(error != null && error.has("message") ? error.get("message").getAsString() : "Agent execution failed");
            }
            JsonObject part = event.has("part") && event.get("part").isJsonObject() ? event.getAsJsonObject("part") : null;
            if (part == null) continue;
            String text = part.has("text") ? part.get("text").getAsString() : "";
            switch (type) {
                case "text_delta", "text" -> { tools.seal(); answer.accept(text); }
                case "reasoning_delta", "reasoning" -> { tools.seal(); thinking.accept(text); }
                case "tool_start", "tool_use" -> {
                    if (type.equals("tool_start")) tools.start(part.has("tool") ? part.get("tool").getAsString() : "tool");
                    else tools.finish(part);
                    AgentNotify.tool(type, part);
                    activity.accept(tools.text());
                    waiting.run();
                }
            }
        }
    }
}
