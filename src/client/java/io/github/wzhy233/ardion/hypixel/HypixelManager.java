package io.github.wzhy233.ardion.hypixel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.impl.misc.HypixelUtils;
import io.github.wzhy233.ardion.util.json.JsonUtil;
import io.github.wzhy233.ardion.util.server.Server;
import io.github.wzhy233.ardion.util.server.ServerUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HypixelManager {
    private static final long REQUEST_INTERVAL_MS = 500L;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<String, HypixelUser> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static long lastRequestTime;

    private HypixelManager() {
    }

    public static void init() {
        EventManager.register(new ListenerBridge());
    }

    public static void add(HypixelUser user) {
        if (user == null || user.uuid() == null || user.uuid().isBlank()) {
            return;
        }
        if (CACHE.size() >= MAX_CACHE_SIZE && !CACHE.containsKey(user.uuid())) {
            CACHE.clear();
        }
        CACHE.put(user.uuid(), user);
    }

    public static HypixelUser getByUuid(String uuid) {
        String normalized = normalizeUuid(uuid);
        if (normalized.isBlank()) {
            return HypixelUser.unknown("");
        }

        HypixelUser user = CACHE.computeIfAbsent(normalized, HypixelUser::unknown);
        if (user.isUnknown()) {
            REQUESTS.add(normalized);
        }
        return user;
    }

    public static void clear() {
        REQUESTS.clear();
    }

    private static void update() {
        if (!ServerUtil.isJoined(Server.HYPIXEL)) {
            lastRequestTime = 0L;
            return;
        }

        if (REQUESTS.isEmpty() || FETCHING.get()) {
            return;
        }

        String apiKey = HypixelUtils.getApiKey();
        if (apiKey.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequestTime < REQUEST_INTERVAL_MS) {
            return;
        }

        String uuid = REQUESTS.iterator().next();
        REQUESTS.remove(uuid);
        lastRequestTime = now;
        FETCHING.set(true);
        CompletableFuture.runAsync(() -> fetchFromApi(uuid, apiKey))
                .whenComplete((ignored, throwable) -> FETCHING.set(false));
    }

    private static void fetchFromApi(String uuid, String apiKey) {
        HypixelUser winstreakUser = fetchFromWinstreak(uuid, apiKey);
        if (winstreakUser != null) {
            add(winstreakUser);
            return;
        }

        HypixelUser hypixelUser = fetchFromHypixel(uuid, apiKey);
        if (hypixelUser != null) {
            add(hypixelUser);
        }
    }

    private static HypixelUser fetchFromWinstreak(String uuid, String apiKey) {
        JsonObject json = readJson("https://api.winstreak.ws/v1/stats?uuid=" + encode(uuid) + "&key=" + encode(apiKey));
        if (json == null || !JsonUtil.string(json, "success", "false").equalsIgnoreCase("true")) {
            return null;
        }

        JsonObject data = JsonUtil.object(json, "data");
        JsonObject bedwars = JsonUtil.object(data, "bedwars");
        if (data == null || bedwars == null) {
            return null;
        }

        String networkLevel = JsonUtil.string(data, "network_level", "0");
        String bedwarsLevel = JsonUtil.string(bedwars, "level", "0");
        String wlr = ratio(JsonUtil.flt(bedwars, "wlr", 0.0F));
        String fkdr = ratio(JsonUtil.flt(bedwars, "fkdr", 0.0F));
        String bblr = ratio(JsonUtil.flt(bedwars, "bblr", 0.0F));
        return new HypixelUser(uuid, networkLevel, bedwarsLevel, wlr, fkdr, bblr);
    }

    private static HypixelUser fetchFromHypixel(String uuid, String apiKey) {
        JsonObject json = readJson("https://api.hypixel.net/v2/player?uuid=" + encode(uuid) + "&key=" + encode(apiKey));
        if (json == null || !JsonUtil.string(json, "success", "false").equalsIgnoreCase("true")) {
            return null;
        }

        JsonObject player = JsonUtil.object(json, "player");
        if (player == null) {
            return null;
        }

        String networkLevel = ratio((Math.sqrt(JsonUtil.lng(player, "networkExp", 0L) + 15312.5D) - 125.0D / Math.sqrt(2.0D)) / (25.0D * Math.sqrt(2.0D)), "%.0f");
        JsonObject stats = JsonUtil.object(player, "stats");
        JsonObject bedwars = JsonUtil.object(stats, "Bedwars");
        if (bedwars == null) {
            return new HypixelUser(uuid, networkLevel, "0", "0.00", "0.00", "0.00");
        }

        String bedwarsLevel = String.valueOf(JsonUtil.integer(bedwars, "Experience", 0) / 5000);
        int finalKills = JsonUtil.integer(bedwars, "final_kills_bedwars", 0);
        int finalDeaths = JsonUtil.integer(bedwars, "final_deaths_bedwars", 0);
        int wins = JsonUtil.integer(bedwars, "wins_bedwars", 0);
        int losses = JsonUtil.integer(bedwars, "losses_bedwars", 0);
        int bedsBroken = JsonUtil.integer(bedwars, "beds_broken_bedwars", 0);
        int bedsLost = JsonUtil.integer(bedwars, "beds_lost_bedwars", 0);

        return new HypixelUser(
                uuid,
                networkLevel,
                bedwarsLevel,
                ratio(wins / (double) Math.max(1, losses)),
                ratio(finalKills / (double) Math.max(1, finalDeaths)),
                ratio(bedsBroken / (double) Math.max(1, bedsLost))
        );
    }

    private static JsonObject readJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("User-Agent", "Ardion")
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException | InterruptedException | IllegalStateException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String ratio(double value) {
        return ratio(value, "%.2f");
    }

    private static String ratio(double value, String format) {
        return String.format(Locale.US, format, value);
    }

    private static final class ListenerBridge {
        @Listener
        public void onTick(MinecraftTickEvent event) {
            if (event.getStage() == MinecraftTickEvent.Stage.PRE) {
                HypixelManager.update();
            }
        }
    }
}
