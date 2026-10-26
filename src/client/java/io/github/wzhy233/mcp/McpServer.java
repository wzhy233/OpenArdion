package io.github.wzhy233.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class McpServer {
    private static final int BODY_LIMIT = 1_000_000;
    private static final Object LOCK = new Object();
    private static volatile HttpServer server;
    private static volatile ExecutorService executor;
    private static volatile String token;
    private static volatile String session;

    private McpServer() {
    }

    public static Endpoint start() throws IOException {
        synchronized (LOCK) {
            if (server != null && token != null) {
                return endpoint();
            }
            GameTools.ensureTick();
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            Arrays.fill(raw, (byte) 0);
            session = UUID.randomUUID().toString();
            ExecutorService pool = Executors.newThreadPerTaskExecutor(task -> {
                Thread thread = Thread.ofVirtual().name("ardion-mcp").unstarted(task);
                thread.setContextClassLoader(McpServer.class.getClassLoader());
                return thread;
            });
            HttpServer created = null;
            try {
                created = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
                created.setExecutor(pool);
                created.createContext("/mcp", McpServer::handle);
                created.start();
            } catch (IOException | RuntimeException exception) {
                token = null;
                session = null;
                if (created != null) created.stop(0);
                pool.shutdownNow();
                if (exception instanceof IOException io) throw io;
                throw new IOException("Agent server failed", exception);
            }
            executor = pool;
            server = created;
            return endpoint();
        }
    }

    public static void stop() {
        AgentChat.stop();
        HttpServer current;
        ExecutorService pool;
        synchronized (LOCK) {
            current = server;
            pool = executor;
            server = null;
            executor = null;
            token = null;
            session = null;
        }
        GameTools.releaseAll();
        if (current != null) current.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private static Endpoint endpoint() {
        int port = server.getAddress().getPort();
        return new Endpoint("http://127.0.0.1:" + port + "/mcp", token);
    }

    private static void handle(HttpExchange exchange) {
        boolean sent = false;
        try {
            if (!loopback(exchange)) {
                sent = write(exchange, 403, "{\"error\":\"local only\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                sent = write(exchange, 405, "{\"error\":\"POST only\"}");
                return;
            }
            if (!authorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
                sent = write(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            byte[] body = readLimited(exchange.getRequestBody());
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                sent = write(exchange, 400, error(null, -32700, "Parse error"));
                return;
            }
            if (!parsed.isJsonObject()) {
                sent = write(exchange, 400, error(null, -32600, "Invalid request"));
                return;
            }
            JsonObject request = parsed.getAsJsonObject();
            JsonElement id = request.get("id");
            boolean notification = id == null || id.isJsonNull();
            String method = text(request, "method");
            JsonObject params = request.has("params") && request.get("params").isJsonObject()
                    ? request.getAsJsonObject("params") : new JsonObject();
            if (!method.equals("initialize") && !sessionMatches(exchange)) {
                sent = write(exchange, 404, error(notification ? null : id, -32001, "Session not found"));
                return;
            }
            if (notification) {
                exchange.sendResponseHeaders(202, -1);
                sent = true;
                return;
            }
            JsonElement result = switch (method) {
                case "initialize" -> initialize(params);
                case "ping" -> new JsonObject();
                case "tools/list" -> tools();
                case "tools/call" -> call(params);
                default -> null;
            };
            if (result == null) {
                sent = write(exchange, 200, error(id, -32601, "Method not found"));
                return;
            }
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            response.add("result", result);
            exchange.getResponseHeaders().set("Mcp-Session-Id", session);
            exchange.getResponseHeaders().set("Mcp-Protocol-Version", "2025-03-26");
            sent = write(exchange, 200, response.toString());
        } catch (Exception exception) {
            if (!sent) {
                try {
                    write(exchange, 500, error(null, -32603, "Request failed"));
                } catch (IOException ignored) {
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static JsonObject initialize(JsonObject params) {
        String version = text(params, "protocolVersion");
        if (!version.matches("\\d{4}-\\d{2}-\\d{2}")) version = "2025-03-26";
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", version);
        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);
        JsonObject info = new JsonObject();
        info.addProperty("name", "ardion");
        info.addProperty("version", "1");
        result.add("serverInfo", info);
        result.addProperty("instructions", "Choose game tools according to the user task; no initial state/debug call is required. Read game data, inspect screenshots, execute semantic Minecraft actions and Voyager programs, send chat and server commands. Verify relevant results with game or server feedback.");
        return result;
    }

    private static JsonObject tools() {
        JsonArray list = new JsonArray();
        for (GameTools.Spec spec : GameTools.specs()) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", spec.name());
            tool.addProperty("description", spec.description());
            tool.add("inputSchema", spec.schema());
            list.add(tool);
        }
        JsonObject result = new JsonObject();
        result.add("tools", list);
        return result;
    }

    private static JsonObject call(JsonObject params) {
        String name = text(params, "name");
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        GameTools.Result result;
        try {
            if (name.isBlank()) throw new IllegalArgumentException("Tool name is required");
            result = GameTools.call(name, arguments);
        } catch (Exception exception) {
            result = GameTools.Result.fail(safe(exception));
        } catch (LinkageError failure) {
            result = GameTools.Result.fail("Ardion client classes are unavailable or incompatible (" + failure.getClass().getSimpleName()
                    + "). Restart Minecraft with the rebuilt mod. Do not retry other game tools or open LAN: the current-player MCP does not require LAN.");
        }
        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", result.text() == null ? "" : result.text());
        content.add(textPart);
        if (result.png() != null && result.png().length > 0) {
            JsonObject image = new JsonObject();
            image.addProperty("type", "image");
            image.addProperty("data", Base64.getEncoder().encodeToString(result.png()));
            image.addProperty("mimeType", "image/png");
            content.add(image);
        }
        JsonObject response = new JsonObject();
        response.add("content", content);
        response.addProperty("isError", result.error());
        return response;
    }

    private static boolean loopback(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress() == null ? null : exchange.getRemoteAddress().getAddress();
        return address != null && address.isLoopbackAddress();
    }

    private static boolean authorized(String header) {
        String expected = token;
        if (expected == null || header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        byte[] given = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, expected.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean sessionMatches(HttpExchange exchange) {
        String expected = session;
        String given = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (expected == null || given == null) return false;
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        byte[] body = input.readNBytes(BODY_LIMIT + 1);
        if (body.length > BODY_LIMIT) throw new IOException("Request is too large");
        return body;
    }

    private static boolean write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
        return true;
    }

    private static String error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null || id.isJsonNull() ? JsonNull.INSTANCE : id);
        response.add("error", error);
        return response.toString();
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }

    private static String safe(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Tool failed";
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        String secret = token;
        if (secret != null && message.contains(secret)) return "Tool failed";
        if (message.length() > 300) message = message.substring(0, 300);
        return message;
    }

    public record Endpoint(String url, String token) {
    }
}
