package io.github.wzhy233.ardion.irc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import static io.github.wzhy233.ardion.Ardion.LOGGER;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class IRCClient {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 16667;
    private static final String SERVICE = "OpenHaedusIRC";
    private static final String PROTOCOL_VERSION = "000";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_PACKET_CHARS = 65536;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 10000L;

    private final String host;
    private final int port;
    private final Gson gson = new Gson();
    private final Object writeLock = new Object();
    private final Object workerLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicLong retryGeneration = new AtomicLong();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile List<IRCUser> onlineUsers = Collections.emptyList();
    private volatile IRCPresence lastSentPresence;
    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private volatile Thread workerThread;

    public IRCClient() {
        this(System.getProperty("openhaedusirc.host", DEFAULT_HOST),
                Integer.getInteger("openhaedusirc.port", DEFAULT_PORT));
    }

    public IRCClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        if (!Boolean.parseBoolean(System.getProperty("openhaedusirc.enabled", "true"))) {
            LOGGER.info("IRC disabled by runtime configuration");
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        startRetryCycle();
    }

    public void retryAfterLogin() {
        reconnect();
    }

    public void reconnect() {
        if (!running.get()) return;
        startRetryCycle();
    }

    private void startRetryCycle() {
        retryGeneration.incrementAndGet();
        closeSocket();
        synchronized (workerLock) {
            Thread worker = workerThread;
            if (worker == null || !worker.isAlive()) {
                startWorkerLocked();
            } else {
                worker.interrupt();
            }
        }
    }

    public void shutdown() {
        running.set(false);
        closeSocket();
        connected.set(false);
        onlineUsers = Collections.emptyList();
        Thread worker = workerThread;
        if (worker != null) worker.interrupt();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public List<IRCUser> getOnlineUsers() {
        return onlineUsers;
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void sendMessage(String message) {
        if (!connected.get()) throw new IllegalStateException("IRC is not connected");
        if (message == null || message.trim().isEmpty()) return;
        if (message.length() > 512) throw new IllegalArgumentException("IRC message is too long");
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "chat");
        packet.addProperty("content", message);
        sendPacket(packet);
    }

    public void syncPresence(IRCPresence presence) {
        if (!connected.get()) return;
        if (presence.equals(lastSentPresence)) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "presence");
        packet.add("content", presence.toJson());
        sendPacket(packet);
        lastSentPresence = presence;
    }

    public void sendCommand(String commandLine) {
        if (!connected.get()) throw new IllegalStateException("IRC is not connected");
        List<String> parts = tokenizeCommand(commandLine);
        if (parts.isEmpty()) return;

        JsonObject packet = new JsonObject();
        packet.addProperty("type", "command");
        packet.addProperty("command", parts.get(0));
        JsonArray arguments = new JsonArray();
        for (int i = 1; i < parts.size(); i++) arguments.add(new JsonPrimitive(parts.get(i)));
        packet.add("arguments", arguments);
        sendPacket(packet);
    }

    private static List<String> tokenizeCommand(String commandLine) {
        if (commandLine == null) return Collections.emptyList();
        String normalized = commandLine.trim();
        if (normalized.isEmpty()) return Collections.emptyList();

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < normalized.length(); i++) {
            char value = normalized.charAt(i);
            if (escaped) {
                current.append(value);
                escaped = false;
            } else if (value == '\\' && quote != 0) {
                escaped = true;
            } else if (quote != 0) {
                if (value == quote) quote = 0;
                else current.append(value);
            } else if (value == '\'' || value == '"') {
                quote = value;
            } else if (Character.isWhitespace(value)) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(value);
            }
        }
        if (escaped || quote != 0) throw new IllegalArgumentException("Unclosed quote in IRC command");
        if (current.length() > 0) parts.add(current.toString());
        return parts;
    }

    private void runConnectionLoop() {
        long activeGeneration = retryGeneration.get();
        int failedAttempts = 0;
        while (running.get()) {
            boolean wasConnected = false;
            try {
                connectAndRead();
            } catch (InterruptedException e) {
                if (!running.get()) break;
                activeGeneration = retryGeneration.get();
                failedAttempts = 0;
                continue;
            } catch (Throwable e) {
                if (running.get() && retryGeneration.get() == activeGeneration) {
                    notifyError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            } finally {
                wasConnected = connected.getAndSet(false);
                closeSocket();
                onlineUsers = Collections.emptyList();
                lastSentPresence = null;
                if (wasConnected && running.get() && retryGeneration.get() == activeGeneration) {
                    notifyDisconnected();
                }
            }

            if (!running.get()) break;
            long requestedGeneration = retryGeneration.get();
            if (requestedGeneration != activeGeneration) {
                Thread.interrupted();
                activeGeneration = requestedGeneration;
                failedAttempts = 0;
                continue;
            }

            if (wasConnected) {
                failedAttempts = 0;
            } else if (++failedAttempts >= MAX_CONNECTION_ATTEMPTS) {
                LOGGER.warn("IRC: stopped connecting after {} failed attempts", MAX_CONNECTION_ATTEMPTS);
                break;
            }

            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                if (!running.get()) break;
                long latestGeneration = retryGeneration.get();
                if (latestGeneration != activeGeneration) {
                    activeGeneration = latestGeneration;
                    failedAttempts = 0;
                }
            }
        }

        synchronized (workerLock) {
            if (workerThread == Thread.currentThread()) workerThread = null;
            if (running.get() && retryGeneration.get() != activeGeneration) startWorkerLocked();
        }
    }

    private void startWorkerLocked() {
        workerThread = new Thread(this::runConnectionLoop);
        workerThread.setName("openhaedusirc-connection");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void connectAndRead() throws IOException, InterruptedException {
        Socket newSocket = openSocket();
        socket = newSocket;
        if (!running.get() || Thread.currentThread().isInterrupted()) {
            closeSocket();
            throw new InterruptedException();
        }
        writer = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));
        MinecraftSession session = IrcLaunchSession.get();
        if (session == null || session.getName() == null || !session.getName().matches("[A-Za-z0-9_]{3,16}")) {
            throw new IOException("A valid launch username is required for openhaedusirc");
        }
        JsonObject login = new JsonObject();
        login.addProperty("type", "login");
        login.addProperty("service", SERVICE);
        login.addProperty("version", PROTOCOL_VERSION);
        login.addProperty("username", session.getName());
        sendPacket(login);
        long welcomeDeadline = System.nanoTime() + CONNECT_TIMEOUT_MS * 1_000_000L;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8))) {
            while (running.get() && !newSocket.isClosed()) {
                if (!connected.get() && System.nanoTime() > welcomeDeadline) throw new IOException("OpenHaedusIRC login timed out");
                String line;
                try {
                    line = readPacket(reader);
                } catch (SocketTimeoutException ignored) {
                    if (connected.get()) sendPing();
                    continue;
                }
                if (line == null) return;
                if (line.length() > MAX_PACKET_CHARS) throw new IOException("IRC packet is too large");
                handlePacket(line);
            }
        }
    }

    private static String readPacket(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        try {
            int value;
            while ((value = reader.read()) != -1) {
                if (value == '\n') return line.toString();
                if (line.length() >= MAX_PACKET_CHARS) throw new IOException("IRC packet is too large");
                line.append((char) value);
            }
        } catch (SocketTimeoutException exception) {
            if (!line.isEmpty()) throw new IOException("IRC packet read timed out", exception);
            throw exception;
        }
        return line.isEmpty() ? null : line.toString();
    }

    private Socket openSocket() throws IOException {
        Socket connection = IrcProxy.enabled()
                ? IrcProxy.connect(host, port, CONNECT_TIMEOUT_MS)
                : directSocket();
        try {
            connection.setSoTimeout(READ_TIMEOUT_MS);
            return connection;
        } catch (IOException exception) {
            connection.close();
            throw exception;
        }
    }

    private Socket directSocket() throws IOException {
        Socket direct = new Socket();
        try {
            direct.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return direct;
        } catch (IOException exception) {
            direct.close();
            throw exception;
        }
    }

    private void handlePacket(String raw) throws IOException {
        JsonObject packet = JsonParser.parseString(raw).getAsJsonObject();
        String type = string(packet, "type", "");
        if (!connected.get() && !type.equals("welcome") && !type.equals("error")) {
            throw new IOException("OpenHaedusIRC packet arrived before welcome");
        }
        switch (type) {
            case "welcome":
                if (connected.get() || !SERVICE.equals(string(packet, "service", ""))
                        || !PROTOCOL_VERSION.equals(string(packet, "version", ""))) {
                    throw new IOException("Unexpected OpenHaedusIRC welcome");
                }
                String username = string(packet, "username", "");
                if (!username.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Invalid OpenHaedusIRC username");
                connected.set(true);
                lastSentPresence = null;
                for (Listener listener : listeners) listener.onConnected(username);
                break;
            case "message":
                IRCUser sender = new IRCUser(string(packet, "sender", "Unknown"), "User");
                long timestamp = packet.has("timestamp") ? packet.get("timestamp").getAsLong() : System.currentTimeMillis();
                IRCMessage message = new IRCMessage(sender, string(packet, "content", ""), timestamp);
                for (Listener listener : listeners) listener.onMessage(message);
                break;
            case "onlineUsers":
                updateOnlineUsers(packet.get("content"));
                break;
            case "system":
                for (Listener listener : listeners) listener.onSystemMessage(string(packet, "content", ""));
                break;
            case "error":
                notifyError(string(packet, "content", "Unknown OpenHaedusIRC error"));
                break;
            default:
                break;
        }
    }

    private void updateOnlineUsers(JsonElement content) {
        if (content == null || !content.isJsonArray()) return;
        JsonArray users = content.getAsJsonArray();
        List<IRCUser> updated = new ArrayList<>();
        for (JsonElement element : users) {
            if (!element.isJsonObject()) continue;
            JsonObject user = element.getAsJsonObject();
            try {
                updated.add(new IRCUser(string(user, "username", "Unknown"),
                        string(user, "permission", "User"), IRCPresence.fromJson(user)));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Ignoring invalid IRC user presence");
            }
        }
        onlineUsers = Collections.unmodifiableList(updated);
        for (Listener listener : listeners) listener.onOnlineUsersChanged(onlineUsers);
    }

    private void sendPing() {
        JsonObject ping = new JsonObject();
        ping.addProperty("type", "ping");
        sendPacket(ping);
    }

    private void sendPacket(JsonObject packet) {
        synchronized (writeLock) {
            BufferedWriter currentWriter = writer;
            if (currentWriter == null) throw new IllegalStateException("IRC connection closed");
            try {
                String payload = gson.toJson(packet);
                currentWriter.write(payload);
                currentWriter.newLine();
                currentWriter.flush();
            } catch (IOException e) {
                closeSocket();
                throw new IllegalStateException("Unable to send IRC packet", e);
            }
        }
    }

    private void closeSocket() {
        Socket currentSocket = socket;
        socket = null;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
            }
        }
        synchronized (writeLock) {
            BufferedWriter currentWriter = writer;
            writer = null;
            if (currentWriter != null) {
                try {
                    currentWriter.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private void notifyError(String message) {
        LOGGER.error("IRC: {}", message);
        for (Listener listener : listeners) listener.onError(message);
    }

    private void notifyDisconnected() {
        for (Listener listener : listeners) listener.onDisconnected();
    }

    public interface Listener {
        default void onConnected(String username) {
        }

        default void onDisconnected() {
        }

        default void onMessage(IRCMessage message) {
        }

        default void onSystemMessage(String message) {
        }

        default void onOnlineUsersChanged(List<IRCUser> users) {
        }

        default void onError(String message) {
        }
    }
}
