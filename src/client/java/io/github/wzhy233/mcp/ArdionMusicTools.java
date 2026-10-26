package io.github.wzhy233.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.wzhy233.ardion.music.MusicPlayerService;
import io.github.wzhy233.ardion.music.netease.NeteaseClient;

final class ArdionMusicTools {
    private static final Gson JSON = new Gson();
    static String call(JsonObject args) throws Exception {
        var api = NeteaseClient.INSTANCE;
        String action = args.has("action") ? ArdionTools.action(args) : "status";
        Object result;
        switch (action) {
            case "status" -> result = status();
            case "search" -> result = api.searchSongs(ArdionTools.required(args, "query"), 40, args.has("offset") ? GameInventoryTools.integer(args, "offset", 0, 100000) : 0);
            case "recommend" -> result = api.recommendSongs();
            case "hot_search" -> result = api.hotSearchKeywords();
            case "playlists" -> result = api.userPlaylists(id(args, "userId"), 100);
            case "playlist_tracks" -> result = api.playlistTracks(id(args, "playlistId"), 1000);
            case "play" -> {
                var song = api.songDetail(id(args, "songId"));
                GameToolSupport.onClient(() -> { MusicPlayerService.play(song); return ""; });
                result = status();
            }
            case "play_playlist" -> {
                var songs = api.playlistTracks(id(args, "playlistId"), 1000);
                if (songs.isEmpty()) throw new IllegalStateException("Playlist has no available tracks");
                GameToolSupport.onClient(() -> { MusicPlayerService.playQueue(songs, songs.getFirst()); return ""; });
                result = status();
            }
            case "lyrics" -> result = MusicPlayerService.lyrics();
            case "account" -> result = java.util.Map.of("loggedIn", api.isLoggedIn(), "userId", api.isLoggedIn() ? api.loginUid() : 0L);
            case "like" -> result = java.util.Map.of("success", api.like(id(args, "songId"), ArdionTools.bool(args, "liked")));
            case "qr_start" -> { String key = api.qrLoginKey(); result = java.util.Map.of("key", key, "url", api.qrLoginContent(key)); }
            case "qr_check" -> result = java.util.Map.of("status", api.qrLoginCheck(ArdionTools.required(args, "key")));
            case "logout" -> { api.logout(); result = status(); }
            default -> {
                GameToolSupport.onClient(() -> {
                    switch (action) {
                        case "toggle" -> MusicPlayerService.toggle();
                        case "pause" -> { if (MusicPlayerService.isPlaying()) MusicPlayerService.toggle(); }
                        case "resume" -> { if (!MusicPlayerService.isPlaying() && MusicPlayerService.hasActiveTrack()) MusicPlayerService.toggle(); }
                        case "stop" -> MusicPlayerService.stop();
                        case "next" -> MusicPlayerService.playAdjacent(1);
                        case "previous" -> MusicPlayerService.playAdjacent(-1);
                        case "seek" -> MusicPlayerService.seek((long) finite(args, "positionMs", 0, Long.MAX_VALUE));
                        case "volume" -> MusicPlayerService.setVolume((float) finite(args, "volume", 0, 1));
                        case "open" -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new io.github.wzhy233.ardion.gui.screen.MusicPlayerScreen());
                        default -> throw new IllegalArgumentException("Unknown music action");
                    }
                    return "";
                });
                result = status();
            }
        }
        return JSON.toJson(result);
    }
    private static JsonObject status() {
        JsonObject result = new JsonObject();
        result.addProperty("playing", MusicPlayerService.isPlaying()); result.addProperty("loading", MusicPlayerService.isLoading());
        result.addProperty("status", MusicPlayerService.status()); result.addProperty("volume", MusicPlayerService.volume());
        result.addProperty("positionMs", MusicPlayerService.positionMs()); result.add("track", JSON.toJsonTree(MusicPlayerService.currentTrack()));
        return result;
    }
    private static long id(JsonObject args, String name) { return (long) finite(args, name, 1, 9_007_199_254_740_991d); }
    private static double finite(JsonObject args, String name, double min, double max) {
        double value = GameToolSupport.number(args, name, Double.NaN);
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}
