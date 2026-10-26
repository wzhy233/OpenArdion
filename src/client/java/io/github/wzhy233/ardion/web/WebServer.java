package io.github.wzhy233.ardion.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.setting.ProxySetting;
import io.github.wzhy233.ardion.setting.TargetSetting;
import io.github.wzhy233.ardion.setting.ThemeSetting;
import io.github.wzhy233.ardion.util.input.KeyUtil;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.session.Session;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo.TextureAsset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebServer {
    private static final int PORT = 23333;
    private static final String URL = "http://127.0.0.1:" + PORT + "/";
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final long SESSION_VALIDATE_INTERVAL_MS = 300_000L;
    private static final long OFFICIAL_TEXTURE_CHECK_INTERVAL_MS = 300_000L;
    private static final Map<WebClient, Boolean> clients = new ConcurrentHashMap<>();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5L))
            .build();
    private static final AtomicBoolean sessionValidationRunning = new AtomicBoolean(false);
    private static final AtomicBoolean officialSkinFetchRunning = new AtomicBoolean(false);
    private static final AtomicBoolean officialCapeFetchRunning = new AtomicBoolean(false);

    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static Thread broadcastThread;
    private static volatile boolean running;
    private static String lastStateJson = "";
    private static volatile SessionValidation sessionValidation = new SessionValidation("", "", "checking", "checking session", 0L);
    private static volatile OfficialSkin officialSkin = new OfficialSkin("", null, null, null, 0L);
    private static volatile OfficialCape officialCape = new OfficialCape("", null, null, null, 0L);

    private WebServer() {
    }

    public static synchronized void start() {
        if (running) return;
        try {
            serverSocket = new ServerSocket(PORT, 32, InetAddress.getByName("127.0.0.1"));
        } catch (IOException exception) {
            throw new IllegalStateException("Web UI port " + PORT + " is unavailable", exception);
        }
        running = true;
        serverThread = new Thread(WebServer::run, "ardion-web-server");
        serverThread.setDaemon(true);
        serverThread.start();
        broadcastThread = new Thread(WebServer::broadcastLoop, "ardion-web-broadcast");
        broadcastThread.setDaemon(true);
        broadcastThread.start();
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (WebClient client : clients.keySet()) {
            client.close();
        }
        clients.clear();
    }

    public static String getUrl() {
        return URL;
    }

    public static String getOotdUrl() {
        return URL + "ootd";
    }

    private static void run() {
        try (ServerSocket socket = serverSocket) {
            while (running) {
                Socket client = socket.accept();
                Thread thread = new Thread(() -> handle(client), "ardion-web-client");
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException exception) {
            if (running) {
                System.err.println("Failed to run server: " + exception.getMessage());
            }
        }
    }

    private static void broadcastLoop() {
        while (running) {
            try {
                if (!clients.isEmpty()) {
                    MinecraftClient.getInstance().execute(WebServer::broadcastState);
                }
                Thread.sleep(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static void handle(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10000);
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            String request = readHttpHeader(input);
            if (request.isEmpty()) {
                socket.close();
                return;
            }

            String[] requestParts = request.split(" ", 3);
            String method = requestParts.length > 0 ? requestParts[0] : "";
            String path = requestParts.length > 1 ? requestParts[1] : "/";
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) path = path.substring(0, queryIndex);

            String host = header(request, "Host");
            if (!host.equals("127.0.0.1:" + PORT) && !host.equals("localhost:" + PORT)) {
                writeHttp(output, "403 Forbidden", "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
                socket.close();
                return;
            }

            if (path.equals("/ws")) {
                String origin = header(request, "Origin");
                if (!origin.equals("http://127.0.0.1:" + PORT)
                        && !origin.equals("http://localhost:" + PORT)) {
                    writeHttp(output, "403 Forbidden", "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
                    socket.close();
                    return;
                }
                socket.setSoTimeout(0);
                upgradeWebSocket(socket, input, output, request);
                return;
            }

            if (!method.equals("GET")) {
                writeHttp(output, "405 Method Not Allowed", "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8));
                socket.close();
                return;
            }
            if (path.startsWith("/player-texture/")) {
                servePlayerTexture(output, path);
                socket.close();
                return;
            }

            serveStatic(output, path);
            socket.close();
        } catch (Exception ignored) {
            try {
                socket.close();
            } catch (IOException ignoredAgain) {
            }
        }
    }

    private static void serveStatic(OutputStream output, String path) throws IOException {
        String resource;
        if (path.equals("/") || path.equals("/modules") || path.equals("/modules/")
                || path.equals("/settings") || path.equals("/settings/") || path.equals("/index.html")) {
            resource = "assets/ardion/web/index.html";

        } else if (path.equals("/ootd") || path.equals("/ootd/") || path.equals("/ootd.html")) {
            resource = "assets/ardion/web/ootd.html";
        } else if (path.equals("/icon.png")) {
            resource = "assets/ardion/icon.png";
        } else {
            String webPath = path;
            if (webPath.contains("..") || webPath.contains("\\")) {
                writeHttp(output, "400 Bad Request", "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
                return;
            }
            resource = "assets/ardion/web" + webPath;
        }

        String contentType = contentType(resource);
        if (contentType == null) {
            writeHttp(output, "404 Not Found", "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        byte[] body = readResource(resource);
        if (body == null) {
            writeHttp(output, "404 Not Found", "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        writeHttp(output, "200 OK", contentType, body);
    }

    private static void servePlayerTexture(OutputStream output, String path) throws IOException {
        String type = path.substring("/player-texture/".length());
        int slashIndex = type.indexOf('/');
        if (slashIndex >= 0) type = type.substring(0, slashIndex);
        if (type.endsWith(".png")) type = type.substring(0, type.length() - 4);

        if (!type.equals("head") && !type.equals("skin") && !type.equals("cape")) {
            writeHttp(output, "404 Not Found", "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        byte[] body = readPlayerTexturePng(type);
        if (body == null) {
            writeHttp(output, "404 Not Found", "text/plain; charset=utf-8", "Texture not available".getBytes(StandardCharsets.UTF_8));
            return;
        }
        writeHttp(output, "200 OK", "image/png", body);
    }

    private static byte[] readPlayerTexturePng(String type) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            byte[] texture;
            if (client.isOnThread()) {
                texture = readPlayerTexturePngOnClientThread(client, type);
            } else {
                CompletableFuture<byte[]> future = client.submit(() -> readPlayerTexturePngOnClientThread(client, type));
                texture = future.get(2L, TimeUnit.SECONDS);
            }

            if (texture != null || !type.equals("cape")) {
                if (texture != null || !type.equals("skin") && !type.equals("head")) {
                    return texture;
                }

                return readPlayerTexturePngFromOfficialSkin(client.getSession(), type);
            }

            return readPlayerTexturePngFromOfficialCape(client.getSession());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readPlayerTexturePngOnClientThread(MinecraftClient client, String type) {
        try {
            if (client.player == null) {
                return null;
            }

            SkinTextures textures = client.player.getSkin();
            TextureAsset asset = type.equals("cape") ? textures.cape() : textures.body();
            if (asset == null) {
                return null;
            }

            AbstractTexture texture = client.getTextureManager().getTexture(asset.texturePath());
            if (texture instanceof NativeImageBackedTexture nativeTexture) {
                NativeImage image = nativeTexture.getImage();
                if (image == null) {
                    return readPlayerTexturePngFromCache(client, type);
                }

                if (type.equals("head")) {
                    return readPlayerHeadPng(image);
                }

                Path tempFile = Files.createTempFile("ardion-player-texture-", ".png");
                try {
                    image.writeTo(tempFile);
                    return Files.readAllBytes(tempFile);
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            }
        } catch (Exception ignored) {
        }
        return readPlayerTexturePngFromCache(client, type);
    }

    private static byte[] readPlayerTexturePngFromCache(MinecraftClient client, String type) {
        try {
            if (client.player == null) {
                return null;
            }

            SkinTextures textures = client.player.getSkin();
            TextureAsset asset = type.equals("cape") ? textures.cape() : textures.body();
            Path cacheFile = localTextureCacheFile(client, type, asset);
            if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
                return null;
            }

            if (type.equals("head")) {
                try (InputStream input = Files.newInputStream(cacheFile); NativeImage image = NativeImage.read(input)) {
                    return readPlayerHeadPng(image);
                }
            }

            return Files.readAllBytes(cacheFile);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readPlayerTexturePngFromOfficialSkin(Session session, String type) {
        OfficialSkin skin = getOfficialSkin(session, true);
        if (skin == null || skin.url() == null || skin.url().isBlank()) {
            return null;
        }

        byte[] bytes = skin.bytes();
        if (bytes == null || bytes.length == 0) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(skin.url()))
                        .timeout(java.time.Duration.ofSeconds(8L))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                    return null;
                }

                bytes = response.body();
                OfficialSkin current = officialSkin;
                if (session.getAccessToken().equals(current.accessToken()) && skin.url().equals(current.url())) {
                    officialSkin = new OfficialSkin(current.accessToken(), current.url(), current.variant(), bytes, current.checkedAt());
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        if (type.equals("head")) {
            try (InputStream input = new java.io.ByteArrayInputStream(bytes); NativeImage image = NativeImage.read(input)) {
                return readPlayerHeadPng(image);
            } catch (Exception ignored) {
                return null;
            }
        }

        return bytes;
    }

    private static byte[] readPlayerTexturePngFromOfficialCape(Session session) {
        OfficialCape cape = getOfficialCape(session, true);
        if (cape == null || cape.url() == null || cape.url().isBlank()) {
            return null;
        }

        if (cape.bytes() != null && cape.bytes().length > 0) {
            return cape.bytes();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cape.url()))
                    .timeout(java.time.Duration.ofSeconds(8L))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                return null;
            }

            OfficialCape current = officialCape;
            if (session.getAccessToken().equals(current.accessToken()) && cape.url().equals(current.url())) {
                officialCape = new OfficialCape(current.accessToken(), current.url(), current.name(), response.body(), current.checkedAt());
            }
            return response.body();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readPlayerHeadPng(NativeImage skin) throws IOException {
        if (skin.getWidth() < 48 || skin.getHeight() < 16) {
            return null;
        }

        BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = skin.getColorArgb(8 + x, 8 + y);
                int overlay = skin.getColorArgb(40 + x, 8 + y);
                head.setRGB(x, y, alphaOverOpaque(base, overlay));
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(head, "png", output);
        return output.toByteArray();
    }

    private static int alphaOverOpaque(int base, int overlay) {
        int alpha = (overlay >>> 24) & 0xFF;
        if (alpha <= 0) {
            return base | 0xFF000000;
        }
        if (alpha >= 255) {
            return overlay;
        }

        int inverse = 255 - alpha;
        int red = ((((overlay >> 16) & 0xFF) * alpha) + (((base >> 16) & 0xFF) * inverse)) / 255;
        int green = ((((overlay >> 8) & 0xFF) * alpha) + (((base >> 8) & 0xFF) * inverse)) / 255;
        int blue = (((overlay & 0xFF) * alpha) + ((base & 0xFF) * inverse)) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (resource.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (resource.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        } else if (resource.endsWith(".png")) {
            return "image/png";
        } else if (resource.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        } else if (resource.endsWith(".json")) {
            return "application/json; charset=utf-8";
        } else {
            return null;
        }
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream stream = WebServer.class.getClassLoader().getResourceAsStream(resource)) {
            return stream == null ? null : stream.readAllBytes();
        }
    }

    private static String readHttpHeader(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous3 = -1;
        int previous2 = -1;
        int previous1 = -1;
        int current;
        while ((current = input.read()) != -1) {
            buffer.write(current);
            if (buffer.size() > 16384) throw new IOException("HTTP header too large");
            if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
                break;
            }
            previous3 = previous2;
            previous2 = previous1;
            previous1 = current;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static String header(String request, String name) {
        for (String line : request.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return "";
    }

    private static void writeJson(OutputStream output, String status, String json) throws IOException {
        writeHttp(output, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeHttp(OutputStream output, String status, String contentType, byte[] body) throws IOException {
        String header = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(body);
        output.flush();
    }

    private static void upgradeWebSocket(Socket socket, InputStream input, OutputStream output, String request) throws Exception {
        String key = null;
        for (String line : request.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon == -1) continue;
            if (line.substring(0, colon).trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
                key = line.substring(colon + 1).trim();
                break;
            }
        }
        if (key == null) {
            socket.close();
            return;
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String accept = Base64.getEncoder().encodeToString(digest.digest((key + WS_MAGIC).getBytes(StandardCharsets.ISO_8859_1)));
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.flush();

        WebClient client = new WebClient(socket, input, output);
        clients.put(client, true);
        MinecraftClient.getInstance().execute(() -> client.send(stateJson()));
        client.readLoop();
    }

    private static void handleMessage(String message) {
        try {
            JsonObject object = JsonParser.parseString(message).getAsJsonObject();
            String type = object.get("type").getAsString();
            if (type.equals("models")) {
                AgentModels.save(object);
                MinecraftClient.getInstance().execute(() -> {
                    lastStateJson = "";
                    broadcastState();
                });
                return;
            }
            if (type.equals("setting")) {
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        applySetting(object.get("setting").getAsString(), object.get("data").getAsString());
                        lastStateJson = "";
                        broadcastState();
                    } catch (Exception ignored) {
                    }
                });
                return;
            }

            String moduleName = object.has("module") ? object.get("module").getAsString() : "";
            if (moduleName.equalsIgnoreCase("clickgui")) return;

            Module module = ModuleManager.getModule(moduleName);
            if (module == null) return;

            MinecraftClient.getInstance().execute(() -> {
                try {
                    switch (type) {
                        case "toggle" -> module.setEnabled(object.get("enabled").getAsBoolean());
                        case "bind" -> applyBind(module, object.get("key").getAsString());
                        case "value" -> applyValue(module, object.get("value").getAsString(), object.get("data").getAsString());
                    }
                    lastStateJson = "";
                    broadcastState();
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void applyBind(Module module, String keyName) {
        if (keyName == null || keyName.isBlank() || keyName.equalsIgnoreCase("none")) {
            module.clearBindKey();
            return;
        }
        module.setBindKey(KeyUtil.parseKey(keyName));
    }

    private static void applyValue(Module module, String valueName, String value) {
        Value<?> moduleValue = module.getValue(valueName);
        if (moduleValue != null) {
            moduleValue.setValueFromString(value);
        }
    }

    private static void applySetting(String settingName, String value) {
        Value<?> setting = GlobalSetting.getValue(settingName);
        if (setting != null) {
            setting.setValueFromString(value);
        }
    }

    private static void broadcastState() {
        String json = stateJson();
        if (json.equals(lastStateJson)) {
            return;
        }
        lastStateJson = json;
        for (WebClient client : clients.keySet()) {
            client.send(json);
        }
    }

    private static String stateJson() {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.player == null ? client.getSession().getUsername() : client.player.getGameProfile().name();
        PlayerTextureProfile textureProfile = getPlayerTextureProfile(client.player);
        SessionValidation validation = getSessionValidation(client.getSession());
        StringBuilder json = new StringBuilder(8192);
        json.append("{\"type\":\"state\",\"version\":\"OpenArdion\",\"player\":{")
                .append("\"name\":\"").append(escape(playerName)).append("\",")
                .append("\"head\":\"").append(escape(textureProfile.headUrl())).append("\",")
                .append("\"skin\":").append(quote(textureProfile.skinUrl())).append(',')
                .append("\"cape\":").append(quote(textureProfile.capeUrl())).append(',')
                .append("\"capeName\":").append(quote(textureProfile.capeName())).append(',')
                .append("\"slim\":").append(textureProfile.slim()).append(',')
                .append("\"uuid\":\"").append(escape(textureProfile.uuid())).append("\",")
                .append("\"sessionStatus\":\"").append(escape(validation.status())).append("\",")
                .append("\"sessionLabel\":\"").append(escape(validation.label())).append("\"},")
                .append("\"settings\":[");

        List<Value<?>> settings = GlobalSetting.getVisibleValues();
        for (int i = 0; i < settings.size(); i++) {
            if (i > 0) json.append(',');
            appendValue(json, settings.get(i));
        }

        json.append("],")
                .append("\"settingGroups\":[");

        appendSettingGroup(json, "global", "Global", "client wide behavior", GlobalSetting.getValues());
        json.append(',');
        appendSettingGroup(json, "proxy", "Proxy", "irc proxy only", ProxySetting.getValues());
        json.append(',');
        appendSettingGroup(json, "hud", "HUD", "overlay and hud rendering", HudSetting.getValues());
        json.append(',');
        appendSettingGroup(json, "target", "Target", "target filtering", TargetSetting.getValues());
        json.append(',');
        appendSettingGroup(json, "theme", "Theme", "colors and visual style", ThemeSetting.getValues());

        json.append("],")
                .append("\"modules\":[");

        boolean first = true;
        for (Module module : ModuleManager.getModules()) {
            if (module.getName().equalsIgnoreCase("clickgui")) continue;
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append("\"name\":\"").append(escape(module.getName())).append("\",")
                    .append("\"display\":\"").append(escape(displayName(module.getName()))).append("\",")
                    .append("\"category\":\"").append(escape(module.getCategory().name())).append("\",")
                    .append("\"enabled\":").append(module.isEnabled()).append(',')
                    .append("\"bind\":\"").append(escape(KeyUtil.getKeyName(module.getBindKey()))).append("\",")
                    .append("\"values\":[");
            List<Value<?>> values = module.getValues();
            for (int j = 0; j < values.size(); j++) {
                Value<?> value = values.get(j);
                if (j > 0) json.append(',');
                appendValue(json, value);
            }
            json.append("]}");
        }

        json.append("],\"agent\":").append(AgentModels.view())
                .append(",\"modelsError\":\"").append(escape(AgentModels.error())).append('"')
                .append(",\"modelsSave\":").append(AgentModels.saveId())
                .append('}');
        return json.toString();
    }

    private static void appendSettingGroup(StringBuilder json, String name, String display, String meta, List<Value<?>> values) {
        json.append('{')
                .append("\"name\":\"").append(escape(name)).append("\",")
                .append("\"display\":\"").append(escape(display)).append("\",")
                .append("\"meta\":\"").append(escape(meta)).append("\",")
                .append("\"values\":[");
        boolean first = true;
        for (Value<?> value : values) {
            if (!GlobalSetting.isVisible(value)) {
                continue;
            }
            if (!first) json.append(',');
            first = false;
            appendValue(json, value);
        }
        json.append("]}");
    }

    private static SessionValidation getSessionValidation(Session session) {
        String accessToken = session.getAccessToken();
        String uuid = session.getUuidOrNull() == null ? "" : session.getUuidOrNull().toString().replace("-", "");
        String name = session.getUsername();

        if (accessToken == null || accessToken.isBlank()) {
            sessionValidation = new SessionValidation("", uuid, "invalid", "invalid session", System.currentTimeMillis());
            return sessionValidation;
        }

        SessionValidation current = sessionValidation;
        long now = System.currentTimeMillis();
        boolean tokenChanged = !accessToken.equals(current.accessToken());
        boolean expired = now - current.checkedAt() >= SESSION_VALIDATE_INTERVAL_MS;
        if ((tokenChanged || expired) && sessionValidationRunning.compareAndSet(false, true)) {
            sessionValidation = new SessionValidation(accessToken, uuid, "checking", "checking session", current.checkedAt());
            Thread thread = new Thread(() -> validateSession(accessToken, uuid, name), "ardion-session-validator");
            thread.setDaemon(true);
            thread.start();
        }

        return sessionValidation;
    }

    private static void validateSession(String accessToken, String expectedUuid, String expectedName) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MINECRAFT_PROFILE_URL))
                    .timeout(java.time.Duration.ofSeconds(8L))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
                officialSkin = officialSkinFromProfile(accessToken, profile, officialSkin);
                officialCape = officialCapeFromProfile(accessToken, profile, officialCape);
                String id = profile.has("id") ? profile.get("id").getAsString() : "";
                String name = profile.has("name") ? profile.get("name").getAsString() : "";
                boolean matchesPlayer = (expectedUuid.isBlank() || expectedUuid.equalsIgnoreCase(id))
                        && (expectedName == null || expectedName.isBlank() || expectedName.equalsIgnoreCase(name));
                sessionValidation = matchesPlayer
                        ? new SessionValidation(accessToken, expectedUuid, "premium", "valid premium session", System.currentTimeMillis())
                        : new SessionValidation(accessToken, expectedUuid, "invalid", "session mismatch", System.currentTimeMillis());
                lastStateJson = "";
                return;
            }

            String status = response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404
                    ? "invalid"
                    : "unknown";
            String label = status.equals("invalid") ? "invalid session" : "session check failed";
            sessionValidation = new SessionValidation(accessToken, expectedUuid, status, label, System.currentTimeMillis());
            lastStateJson = "";
        } catch (Exception ignored) {
            sessionValidation = new SessionValidation(accessToken, expectedUuid, "unknown", "session check failed", System.currentTimeMillis());
            lastStateJson = "";
        } finally {
            sessionValidationRunning.set(false);
        }
    }

    private static void appendValue(StringBuilder json, Value<?> value) {
        json.append('{')
                .append("\"name\":\"").append(escape(value.getName())).append("\",")
                .append("\"display\":\"").append(escape(displayName(value.getName()))).append("\",")
                .append("\"value\":\"").append(escape(value.getValueAsString())).append("\",")
                .append("\"visible\":").append(GlobalSetting.isVisible(value)).append(',');
        if (value instanceof BooleanValue) {
            json.append("\"type\":\"boolean\"");
        } else if (value instanceof NumberValue numberValue) {
            json.append("\"type\":\"number\",")
                    .append("\"min\":").append(numberValue.getMin()).append(',')
                    .append("\"max\":").append(numberValue.getMax()).append(',')
                    .append("\"integer\":").append(numberValue.isInteger());
        } else if (value instanceof ModeValue modeValue) {
            json.append("\"type\":\"mode\",\"modes\":[");
            List<String> modes = new ArrayList<>(modeValue.getModes());
            for (int i = 0; i < modes.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(escape(modes.get(i))).append('"');
            }
            json.append(']');
        } else if (value instanceof ColorValue) {
            json.append("\"type\":\"color\"");
        } else if (value instanceof StringValue) {
            json.append("\"type\":\"string\"");
        } else {
            json.append("\"type\":\"string\"");
        }
        json.append('}');
    }

    private static String displayName(String name) {
        StringBuilder builder = new StringBuilder();
        for (String part : name.split("-")) {
            if (!builder.isEmpty()) builder.append(' ');
            if (!part.isEmpty()) builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String quote(String value) {
        return value == null || value.isBlank() ? "null" : "\"" + escape(value) + "\"";
    }

    private static String officialSkinLocalUrl(Session session) {
        OfficialSkin skin = getOfficialSkin(session, false);
        return officialSkinLocalUrl(skin);
    }

    private static String officialSkinLocalUrl(OfficialSkin skin) {
        if (skin == null || skin.url() == null || skin.url().isBlank()) {
            return null;
        }

        return "/player-texture/skin?v=" + Integer.toHexString(skin.url().hashCode());
    }

    private static OfficialSkin getOfficialSkin(Session session, boolean block) {
        String accessToken = session.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            officialSkin = new OfficialSkin("", null, null, null, System.currentTimeMillis());
            return null;
        }

        OfficialSkin current = officialSkin;
        long now = System.currentTimeMillis();
        boolean tokenChanged = !accessToken.equals(current.accessToken());
        boolean expired = now - current.checkedAt() >= OFFICIAL_TEXTURE_CHECK_INTERVAL_MS;
        if (tokenChanged || expired) {
            if (block) {
                return refreshOfficialSkin(accessToken);
            }
            startOfficialSkinFetch(accessToken);
        }

        current = officialSkin;
        return accessToken.equals(current.accessToken()) ? current : null;
    }

    private static void startOfficialSkinFetch(String accessToken) {
        if (!officialSkinFetchRunning.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                refreshOfficialSkin(accessToken);
            } finally {
                officialSkinFetchRunning.set(false);
            }
        }, "ardion-official-skin-fetcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static OfficialSkin refreshOfficialSkin(String accessToken) {
        OfficialSkin previous = officialSkin;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MINECRAFT_PROFILE_URL))
                    .timeout(java.time.Duration.ofSeconds(8L))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                OfficialSkin fallback = accessToken.equals(previous.accessToken())
                        ? new OfficialSkin(accessToken, previous.url(), previous.variant(), previous.bytes(), System.currentTimeMillis())
                        : new OfficialSkin(accessToken, null, null, null, System.currentTimeMillis());
                officialSkin = fallback;
                lastStateJson = "";
                return fallback;
            }

            JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
            OfficialSkin skin = officialSkinFromProfile(accessToken, profile, previous);
            officialSkin = skin;
            lastStateJson = "";
            return skin;
        } catch (Exception ignored) {
            OfficialSkin fallback = accessToken.equals(previous.accessToken())
                    ? new OfficialSkin(accessToken, previous.url(), previous.variant(), previous.bytes(), System.currentTimeMillis())
                    : new OfficialSkin(accessToken, null, null, null, System.currentTimeMillis());
            officialSkin = fallback;
            lastStateJson = "";
            return fallback;
        }
    }

    private static OfficialSkin officialSkinFromProfile(String accessToken, JsonObject profile, OfficialSkin previous) {
        JsonObject skin = activeProfileTexture(profile, "skins");
        String skinUrl = skin == null ? null : jsonString(skin, "url");
        String variant = skin == null ? null : jsonString(skin, "variant");
        byte[] bytes = accessToken.equals(previous.accessToken()) && Objects.equals(skinUrl, previous.url())
                ? previous.bytes()
                : null;
        return new OfficialSkin(accessToken, skinUrl, variant, bytes, System.currentTimeMillis());
    }

    private static String officialCapeLocalUrl(Session session) {
        OfficialCape cape = getOfficialCape(session, false);
        if (cape == null || cape.url() == null || cape.url().isBlank()) {
            return null;
        }

        return "/player-texture/cape?v=" + Integer.toHexString(cape.url().hashCode());
    }

    private static OfficialCape getOfficialCape(Session session, boolean block) {
        String accessToken = session.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            officialCape = new OfficialCape("", null, null, null, System.currentTimeMillis());
            return null;
        }

        OfficialCape current = officialCape;
        long now = System.currentTimeMillis();
        boolean tokenChanged = !accessToken.equals(current.accessToken());
        boolean expired = now - current.checkedAt() >= OFFICIAL_TEXTURE_CHECK_INTERVAL_MS;
        if (tokenChanged || expired) {
            if (block) {
                return refreshOfficialCape(accessToken);
            }
            startOfficialCapeFetch(accessToken);
        }

        current = officialCape;
        return accessToken.equals(current.accessToken()) ? current : null;
    }

    private static void startOfficialCapeFetch(String accessToken) {
        if (!officialCapeFetchRunning.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                refreshOfficialCape(accessToken);
            } finally {
                officialCapeFetchRunning.set(false);
            }
        }, "ardion-official-cape-fetcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static OfficialCape refreshOfficialCape(String accessToken) {
        OfficialCape previous = officialCape;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MINECRAFT_PROFILE_URL))
                    .timeout(java.time.Duration.ofSeconds(8L))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                OfficialCape fallback = accessToken.equals(previous.accessToken())
                        ? new OfficialCape(accessToken, previous.url(), previous.name(), previous.bytes(), System.currentTimeMillis())
                        : new OfficialCape(accessToken, null, null, null, System.currentTimeMillis());
                officialCape = fallback;
                lastStateJson = "";
                return fallback;
            }

            JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
            OfficialCape cape = officialCapeFromProfile(accessToken, profile, previous);
            officialCape = cape;
            lastStateJson = "";
            return cape;
        } catch (Exception ignored) {
            OfficialCape fallback = accessToken.equals(previous.accessToken())
                    ? new OfficialCape(accessToken, previous.url(), previous.name(), previous.bytes(), System.currentTimeMillis())
                    : new OfficialCape(accessToken, null, null, null, System.currentTimeMillis());
            officialCape = fallback;
            lastStateJson = "";
            return fallback;
        }
    }

    private static OfficialCape officialCapeFromProfile(String accessToken, JsonObject profile, OfficialCape previous) {
        String capeUrl = activeCapeUrl(profile);
        String capeName = activeCapeName(profile);
        byte[] bytes = accessToken.equals(previous.accessToken()) && Objects.equals(capeUrl, previous.url())
                ? previous.bytes()
                : null;
        return new OfficialCape(accessToken, capeUrl, capeName, bytes, System.currentTimeMillis());
    }

    private static String activeCapeUrl(JsonObject profile) {
        JsonObject cape = activeProfileTexture(profile, "capes");
        return cape == null ? null : jsonString(cape, "url");
    }

    private static String activeCapeName(JsonObject profile) {
        JsonObject cape = activeProfileTexture(profile, "capes");
        if (cape == null) {
            return null;
        }

        String alias = jsonString(cape, "alias");
        if (alias != null && !alias.isBlank()) {
            return readableTextureName(alias);
        }

        String id = jsonString(cape, "id");
        return id == null || id.isBlank() ? null : readableTextureName(id);
    }

    private static String readableTextureName(String value) {
        String normalized = value.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return value;
        }

        StringBuilder builder = new StringBuilder(normalized.length());
        for (String part : normalized.split("\\s+")) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static JsonObject activeProfileTexture(JsonObject profile, String key) {
        if (!profile.has(key) || !profile.get(key).isJsonArray()) {
            return null;
        }

        JsonObject fallback = null;
        for (JsonElement element : profile.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject texture = element.getAsJsonObject();
            String url = jsonString(texture, "url");
            if (url == null || url.isBlank()) {
                continue;
            }

            if (fallback == null) {
                fallback = texture;
            }

            if ("ACTIVE".equalsIgnoreCase(jsonString(texture, "state"))) {
                return texture;
            }
        }
        return fallback;
    }

    private static String jsonString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static PlayerTextureProfile getPlayerTextureProfile(AbstractClientPlayerEntity player) {
        Session session = MinecraftClient.getInstance().getSession();
        OfficialCape officialCape = getOfficialCape(session, false);
        if (player == null) {
            OfficialSkin official = getOfficialSkin(session, false);
            return new PlayerTextureProfile("", "/player-texture/head", officialSkinLocalUrl(official),
                    officialCapeLocalUrl(officialCape), officialCapeName(officialCape), isSlimVariant(official));
        }

        String uuid = player.getGameProfile().id() == null ? "" : player.getGameProfile().id().toString();
        SkinTextures textures = player.getSkin();
        String headUrl = localTextureUrl("head", textures.body());
        String skinUrl = localTextureUrl("skin", textures.body());
        OfficialSkin official = null;
        if (skinUrl == null) {
            official = getOfficialSkin(session, false);
            skinUrl = officialSkinLocalUrl(official);
        }
        String capeUrl = localTextureUrl("cape", textures.cape());
        if (capeUrl == null) {
            capeUrl = officialCapeLocalUrl(officialCape);
        }
        boolean slim = official == null ? textures.model() == PlayerSkinType.SLIM : isSlimVariant(official);
        return new PlayerTextureProfile(uuid, headUrl == null ? "/player-texture/head" : headUrl, skinUrl, capeUrl,
                capeUrl == null ? null : officialCapeName(officialCape), slim);
    }

    private static String officialCapeLocalUrl(OfficialCape cape) {
        if (cape == null || cape.url() == null || cape.url().isBlank()) {
            return null;
        }

        return "/player-texture/cape?v=" + Integer.toHexString(cape.url().hashCode());
    }

    private static String officialCapeName(OfficialCape cape) {
        return cape == null || cape.name() == null || cape.name().isBlank() ? null : cape.name();
    }

    private static boolean isSlimVariant(OfficialSkin skin) {
        return skin != null && "SLIM".equalsIgnoreCase(skin.variant());
    }

    private static String localTextureUrl(String type, TextureAsset asset) {
        if (asset == null || !isLocalTextureAvailable(type, asset)) {
            return null;
        }

        String version = Integer.toHexString(asset.texturePath().toString().hashCode());
        return "/player-texture/" + type + "?v=" + version;
    }

    private static boolean isLocalTextureAvailable(String type, TextureAsset asset) {
        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(asset.texturePath());
            if (texture instanceof NativeImageBackedTexture nativeTexture && nativeTexture.getImage() != null) {
                return true;
            }

            Path cacheFile = localTextureCacheFile(MinecraftClient.getInstance(), type, asset);
            return cacheFile != null && Files.isRegularFile(cacheFile);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path localTextureCacheFile(MinecraftClient client, String type, TextureAsset asset) {
        if (asset == null) {
            return null;
        }

        Path cacheDirectory = playerSkinCacheDirectory(client, type);
        if (cacheDirectory == null) {
            return null;
        }

        String path = asset.id().getPath();
        int slash = path.lastIndexOf('/');
        String hash = slash >= 0 ? path.substring(slash + 1) : path;
        if (hash.isBlank()) {
            return null;
        }

        String prefix = hash.length() > 2 ? hash.substring(0, 2) : "xx";
        return cacheDirectory.resolve(prefix).resolve(hash);
    }

    private static Path playerSkinCacheDirectory(MinecraftClient client, String type) {
        try {
            Object provider = client.getSkinProvider();
            Field cacheField = provider.getClass().getDeclaredField(switch (type) {
                case "cape" -> "capeCache";
                case "head", "skin" -> "skinCache";
                default -> "skinCache";
            });
            cacheField.setAccessible(true);

            Object fileCache = cacheField.get(provider);
            Field directoryField = fileCache.getClass().getDeclaredField("directory");
            directoryField.setAccessible(true);
            Object directory = directoryField.get(fileCache);
            return directory instanceof Path path ? path : null;
        } catch (Exception ignored) {
            return client.runDirectory.toPath().resolve("assets").resolve("skins");
        }
    }

    private record PlayerTextureProfile(String uuid, String headUrl, String skinUrl, String capeUrl, String capeName, boolean slim) {
    }

    private record SessionValidation(String accessToken, String uuid, String status, String label, long checkedAt) {
    }

    private record OfficialSkin(String accessToken, String url, String variant, byte[] bytes, long checkedAt) {
    }

    private record OfficialCape(String accessToken, String url, String name, byte[] bytes, long checkedAt) {
    }

    private static final class WebClient {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        private WebClient(Socket socket, InputStream input, OutputStream output) {
            this.socket = socket;
            this.input = input;
            this.output = output;
        }

        private void readLoop() {
            try {
                while (running && !socket.isClosed()) {
                    String message = readFrame();
                    if (message == null) break;
                    handleMessage(message);
                }
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        private String readFrame() throws IOException {
            int b1 = input.read();
            if (b1 == -1) return null;
            int b2 = input.read();
            if (b2 == -1) return null;
            int opcode = b1 & 0x0F;
            if (opcode == 8) return null;
            boolean masked = (b2 & 0x80) != 0;
            long length = b2 & 0x7F;
            if (length == 126) length = (input.read() << 8) | input.read();
            if (length == 127) {
                length = 0L;
                for (int i = 0; i < 8; i++) length = (length << 8) | input.read();
            }
            byte[] mask = new byte[4];
            if (masked) readFully(mask);
            byte[] payload = new byte[(int) length];
            readFully(payload);
            if (masked) {
                for (int i = 0; i < payload.length; i++) payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
            return opcode == 1 ? new String(payload, StandardCharsets.UTF_8) : "";
        }

        private void readFully(byte[] bytes) throws IOException {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read == -1) throw new IOException("Socket closed");
                offset += read;
            }
        }

        private synchronized void send(String message) {
            try {
                byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                output.write(0x81);
                if (payload.length <= 125) {
                    output.write(payload.length);
                } else if (payload.length <= 65535) {
                    output.write(126);
                    output.write((payload.length >>> 8) & 0xFF);
                    output.write(payload.length & 0xFF);
                } else {
                    output.write(127);
                    for (int i = 7; i >= 0; i--) output.write((payload.length >>> (8 * i)) & 0xFF);
                }
                output.write(payload);
                output.flush();
            } catch (IOException exception) {
                close();
            }
        }

        private void close() {
            clients.remove(this);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
