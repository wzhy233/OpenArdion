package io.github.wzhy233.ardion.music.netease;

import com.google.gson.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.github.wzhy233.ardion.Ardion;
import io.github.wzhy233.ardion.music.MusicHttp;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NeteaseClient {
    public static final NeteaseClient INSTANCE = new NeteaseClient();

    private static final String BASE = "https://music.163.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final Gson gson = new Gson();
    private final JsonParser parser = new JsonParser();
    private final Map<String, String> cookies = new ConcurrentHashMap<>();
    private final Path configDir = io.github.wzhy233.ardion.util.client.ArdionHome.musicPlayer();
    private final Path cookieFile = configDir.resolve("netease-cookies.json");
    private final Path previousCookieFile = FabricLoader.getInstance().getConfigDir().resolve("ardion/music-player/netease-cookies.json");
    private final Path legacyCookieFile = FabricLoader.getInstance().getConfigDir().resolve("ardion/netease-cookies.json");

    private NeteaseClient() {
        loadCookies();
    }

    public JsonObject weapiJson(String path, Map<String, Object> json) throws IOException {
        String resp = weapiCall(path, json);
        JsonElement el = parser.parse(resp);
        if (!el.isJsonObject()) throw new IOException("Netease response is not a JSON object");
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("code") && !obj.get("code").isJsonNull()
                && obj.get("code").getAsInt() == 301
                && isLoggedIn() && !"login/qrcode/client/login".equals(path)) {
            clearCookies();
        }
        return obj;
    }

    public String weapiCall(String path, Map<String, Object> json) throws IOException {
        String csrf = cookies.getOrDefault("__csrf", "");
        Map<String, Object> body = json == null ? new HashMap<>() : new HashMap<>(json);
        body.putIfAbsent("csrf_token", csrf);

        Map<String, String> encrypted;
        try {
            encrypted = NeteaseCrypto.weapi(gson.toJson(body));
        } catch (Exception e) {
            throw new IOException("weapi crypto failed", e);
        }

        String form = formEncode(encrypted);
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/weapi/" + path
                + "?csrf_token=" + urlEnc(csrf)))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", UA).header("Referer", BASE).header("Origin", BASE)
                .header("Cookie", cookieHeader())
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
        try (MusicHttp.Response response = MusicHttp.send(request, Duration.ofSeconds(20))) {
            int code = response.statusCode();
            captureSetCookies(response.headers().allValues("Set-Cookie"));
            String resp = readAll(response.body());
            if (code >= 400) throw new IOException("Netease HTTP " + code + ": " + truncate(resp, 200));
            return resp;
        }
    }

    public String songUrl(long songId, String level) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", "[" + songId + "]");
        body.put("level", level == null ? "standard" : level);
        body.put("encodeType", "mp3");
        JsonObject obj = weapiJson("song/enhance/player/url/v1", body);
        if (!obj.has("data") || !obj.get("data").isJsonArray() || obj.getAsJsonArray("data").size() == 0) return null;
        JsonObject first = obj.getAsJsonArray("data").get(0).getAsJsonObject();
        return first.has("url") && !first.get("url").isJsonNull() ? first.get("url").getAsString() : null;
    }

    public List<NeteaseSong> searchSongs(String keyword, int limit, int offset) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("s", keyword);
        body.put("type", 1);
        body.put("limit", limit);
        body.put("offset", offset);
        JsonObject obj = weapiJson("search/get", body);
        List<NeteaseSong> out = new ArrayList<>();
        if (obj.has("result") && obj.get("result").isJsonObject()) {
            JsonObject result = obj.getAsJsonObject("result");
            if (result.has("songs") && result.get("songs").isJsonArray()) {
                for (JsonElement el : result.getAsJsonArray("songs")) {
                    if (el.isJsonObject()) out.add(parseSong(el.getAsJsonObject()));
                }
            }
        }
        return out;
    }

    public List<String> hotSearchKeywords() throws IOException {
        JsonObject obj = weapiJson("search/hot/detail", new HashMap<>());
        List<String> out = new ArrayList<>();
        if (obj.has("data") && obj.get("data").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("data")) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();
                if (item.has("searchWord") && !item.get("searchWord").isJsonNull()) {
                    String word = item.get("searchWord").getAsString();
                    if (word != null && !word.isBlank()) out.add(word);
                }
            }
        }
        return out;
    }

    public List<NeteasePlaylist> personalizedPlaylists(int limit) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("limit", limit);
        body.put("offset", 0);
        body.put("total", true);
        body.put("n", 1000);
        JsonObject obj = weapiJson("personalized/playlist", body);
        List<NeteasePlaylist> out = new ArrayList<>();
        if (obj.has("result") && obj.get("result").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("result")) {
                if (el.isJsonObject()) out.add(parsePlaylist(el.getAsJsonObject()));
            }
        }
        return out;
    }

    public NeteasePlaylist playlistDetail(long playlistId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("id", playlistId);
        body.put("n", 1000);
        body.put("s", 8);
        JsonObject obj = weapiJson("v6/playlist/detail", body);
        return obj.has("playlist") && obj.get("playlist").isJsonObject()
                ? parsePlaylist(obj.getAsJsonObject("playlist")) : null;
    }

    public List<NeteaseSong> playlistTracks(long playlistId, int limit) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("id", playlistId);
        body.put("n", limit);
        body.put("s", 0);
        JsonObject detail = weapiJson("v6/playlist/detail", body);
        List<NeteaseSong> out = new ArrayList<>();
        if (!detail.has("playlist") || !detail.get("playlist").isJsonObject()) return out;
        JsonObject pl = detail.getAsJsonObject("playlist");
        if (pl.has("tracks") && pl.get("tracks").isJsonArray()) {
            for (JsonElement el : pl.getAsJsonArray("tracks")) {
                if (el.isJsonObject()) out.add(parseSong(el.getAsJsonObject()));
            }
            return out;
        }
        if (!pl.has("trackIds") || !pl.get("trackIds").isJsonArray()) return out;
        StringBuilder ids = new StringBuilder("[");
        int n = 0;
        for (JsonElement el : pl.getAsJsonArray("trackIds")) {
            if (n >= limit) break;
            if (!el.isJsonObject()) continue;
            if (n > 0) ids.append(',');
            ids.append("{\"id\":").append(el.getAsJsonObject().get("id").getAsLong()).append('}');
            n++;
        }
        ids.append(']');
        Map<String, Object> songBody = new HashMap<>();
        songBody.put("c", ids.toString());
        JsonObject songs = weapiJson("v3/song/detail", songBody);
        if (songs.has("songs") && songs.get("songs").isJsonArray()) {
            for (JsonElement el : songs.getAsJsonArray("songs")) {
                if (el.isJsonObject()) out.add(parseSong(el.getAsJsonObject()));
            }
        }
        return out;
    }

    public NeteaseSong songDetail(long songId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("c", "[{\"id\":" + songId + "}]");
        JsonObject obj = weapiJson("v3/song/detail", body);
        if (!obj.has("songs") || !obj.get("songs").isJsonArray() || obj.getAsJsonArray("songs").size() == 0) return null;
        JsonElement first = obj.getAsJsonArray("songs").get(0);
        return first.isJsonObject() ? parseSong(first.getAsJsonObject()) : null;
    }

    public List<NeteaseSong> songDetails(Collection<Long> songIds) throws IOException {
        if (songIds == null || songIds.isEmpty()) return Collections.emptyList();
        StringBuilder ids = new StringBuilder("[");
        int n = 0;
        for (Long songId : songIds) {
            if (songId == null || songId <= 0L) continue;
            if (n > 0) ids.append(',');
            ids.append("{\"id\":").append(songId).append('}');
            n++;
        }
        ids.append(']');
        if (n == 0) return Collections.emptyList();

        Map<String, Object> body = new HashMap<>();
        body.put("c", ids.toString());
        JsonObject obj = weapiJson("v3/song/detail", body);
        List<NeteaseSong> out = new ArrayList<>();
        if (obj.has("songs") && obj.get("songs").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("songs")) {
                if (el.isJsonObject()) out.add(parseSong(el.getAsJsonObject()));
            }
        }
        return out;
    }

    public NeteaseLyric lyric(long songId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("id", songId);
        body.put("lv", 0);
        body.put("kv", 0);
        body.put("tv", 0);
        body.put("rv", 0);
        body.put("yv", 0);
        JsonObject obj = weapiJson("song/lyric/v1", body);
        NeteaseLyric out = new NeteaseLyric();
        out.instrumental = obj.has("nolyric") && obj.get("nolyric").isJsonPrimitive()
                && obj.get("nolyric").getAsBoolean();
        out.yrc = extractLyric(obj, "yrc");
        out.lrc = extractLyric(obj, "lrc");
        out.tlyric = extractLyric(obj, "tlyric");
        out.romalrc = extractLyric(obj, "romalrc");
        return out;
    }

    public List<NeteaseSong> recommendSongs() throws IOException {
        if (!isLoggedIn()) return Collections.emptyList();
        JsonObject obj = weapiJson("v1/discovery/recommend/songs", new HashMap<>());
        List<NeteaseSong> out = new ArrayList<>();
        JsonArray arr = null;
        if (obj.has("data") && obj.get("data").isJsonObject()) {
            JsonObject data = obj.getAsJsonObject("data");
            if (data.has("dailySongs") && data.get("dailySongs").isJsonArray()) arr = data.getAsJsonArray("dailySongs");
        }
        if (arr == null && obj.has("recommend") && obj.get("recommend").isJsonArray()) arr = obj.getAsJsonArray("recommend");
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) out.add(parseSong(el.getAsJsonObject()));
            }
        }
        return out;
    }

    public List<NeteasePlaylist> userPlaylists(long uid, int limit) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        body.put("limit", limit);
        body.put("offset", 0);
        body.put("includeVideo", true);
        JsonObject obj = weapiJson("user/playlist", body);
        List<NeteasePlaylist> out = new ArrayList<>();
        if (obj.has("playlist") && obj.get("playlist").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("playlist")) {
                if (el.isJsonObject()) out.add(parsePlaylist(el.getAsJsonObject()));
            }
        }
        return out;
    }

    public List<NeteaseSong> userRecord(long uid, int type) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        body.put("type", type);
        JsonObject obj = weapiJson("v1/play/record", body);
        List<NeteaseSong> out = new ArrayList<>();
        String key = type == 1 ? "weekData" : "allData";
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                if (!el.isJsonObject()) continue;
                JsonObject row = el.getAsJsonObject();
                if (row.has("song") && row.get("song").isJsonObject()) out.add(parseSong(row.getAsJsonObject("song")));
            }
        }
        return out;
    }

    public boolean like(long songId, boolean like) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("trackId", songId);
        body.put("like", like);
        body.put("alg", "itembased");
        body.put("time", "3");
        JsonObject obj = weapiJson("song/like", body);
        return obj.has("code") && obj.get("code").getAsInt() == 200;
    }

    public Set<Long> likedSongIds(long uid) throws IOException {
        if (uid == 0L) return Collections.emptySet();
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        JsonObject obj = weapiJson("song/like/get", body);
        Set<Long> out = new HashSet<>();
        if (obj.has("ids") && obj.get("ids").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("ids")) {
                if (el.isJsonPrimitive()) out.add(el.getAsLong());
            }
        }
        return out;
    }

    public NeteaseUser userDetail(long uid) throws IOException {
        if (uid == 0L) return null;
        JsonObject obj = weapiJson("v1/user/detail/" + uid, new HashMap<>());
        if (!obj.has("profile") || !obj.get("profile").isJsonObject()) return null;
        JsonObject profile = obj.getAsJsonObject("profile");
        NeteaseUser out = new NeteaseUser();
        out.uid = uid;
        if (profile.has("nickname") && !profile.get("nickname").isJsonNull()) out.nickname = profile.get("nickname").getAsString();
        if (profile.has("avatarUrl") && !profile.get("avatarUrl").isJsonNull()) out.avatarUrl = profile.get("avatarUrl").getAsString();
        if (profile.has("signature") && !profile.get("signature").isJsonNull()) out.signature = profile.get("signature").getAsString();
        if (profile.has("vipType") && !profile.get("vipType").isJsonNull()) out.vipType = profile.get("vipType").getAsInt();
        if (obj.has("level") && !obj.get("level").isJsonNull()) out.level = obj.get("level").getAsInt();
        return out;
    }

    public long loginUid() throws IOException {
        if (!isLoggedIn()) return 0L;
        JsonObject obj = weapiJson("w/nuser/account/get", new HashMap<>());
        if (!obj.has("profile") || obj.get("profile").isJsonNull()) return 0L;
        JsonObject profile = obj.getAsJsonObject("profile");
        return profile.has("userId") && !profile.get("userId").isJsonNull() ? profile.get("userId").getAsLong() : 0L;
    }

    public String qrLoginKey() throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("type", 1);
        JsonObject obj = weapiJson("login/qrcode/unikey", body);
        if (!obj.has("unikey") || obj.get("unikey").isJsonNull()) throw new IOException("No QR unikey in response");
        return obj.get("unikey").getAsString();
    }

    public String qrLoginContent(String unikey) {
        return "https://music.163.com/login?codekey=" + unikey;
    }

    public byte[] qrLoginImageBytes(String unikey) throws IOException {
        try {
            int size = 300;
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(qrLoginContent(unikey), BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IOException("QR image encode failed", e);
        }
    }

    public int qrLoginCheck(String unikey) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("key", unikey);
        body.put("type", 1);
        JsonObject obj = weapiJson("login/qrcode/client/login", body);
        if (!obj.has("code")) throw new IOException("No QR status code in response");
        return obj.get("code").getAsInt();
    }

    public void logout() {
        try {
            weapiCall("logout", new HashMap<>());
        } catch (Throwable ignored) {
        }
        clearCookies();
    }

    public boolean isLoggedIn() {
        return cookies.containsKey("MUSIC_U");
    }

    public boolean importCookieHeader(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) return false;
        Map<String, String> imported = parseCookieInput(cookieHeader);
        if (!imported.containsKey("MUSIC_U")) return false;
        cookies.clear();
        cookies.putAll(imported);
        saveCookies();
        return true;
    }

    public void clearCookies() {
        cookies.clear();
        try {
            Files.deleteIfExists(cookieFile);
        } catch (IOException ignored) {
        }
    }

    private static NeteaseSong parseSong(JsonObject s) {
        NeteaseSong out = new NeteaseSong();
        if (s.has("id")) out.id = s.get("id").getAsLong();
        if (s.has("name") && !s.get("name").isJsonNull()) out.name = s.get("name").getAsString();
        if (s.has("dt")) out.durationMs = s.get("dt").getAsLong();
        else if (s.has("duration")) out.durationMs = s.get("duration").getAsLong();
        if (s.has("fee") && !s.get("fee").isJsonNull()) out.fee = s.get("fee").getAsInt() == 1;
        JsonArray artists = s.has("ar") && s.get("ar").isJsonArray()
                ? s.getAsJsonArray("ar")
                : (s.has("artists") && s.get("artists").isJsonArray() ? s.getAsJsonArray("artists") : null);
        if (artists != null) {
            StringJoiner joiner = new StringJoiner(" / ");
            for (JsonElement el : artists) {
                if (el.isJsonObject() && el.getAsJsonObject().has("name") && !el.getAsJsonObject().get("name").isJsonNull()) {
                    joiner.add(el.getAsJsonObject().get("name").getAsString());
                }
            }
            out.artist = joiner.toString();
        }
        JsonObject album = s.has("al") && s.get("al").isJsonObject()
                ? s.getAsJsonObject("al")
                : (s.has("album") && s.get("album").isJsonObject() ? s.getAsJsonObject("album") : null);
        if (album != null) {
            if (album.has("name") && !album.get("name").isJsonNull()) out.album = album.get("name").getAsString();
            if (album.has("picUrl") && !album.get("picUrl").isJsonNull()) out.coverUrl = album.get("picUrl").getAsString();
            else if (album.has("blurPicUrl") && !album.get("blurPicUrl").isJsonNull()) out.coverUrl = album.get("blurPicUrl").getAsString();
        }
        return out;
    }

    private static NeteasePlaylist parsePlaylist(JsonObject p) {
        NeteasePlaylist out = new NeteasePlaylist();
        if (p.has("id")) out.id = p.get("id").getAsLong();
        if (p.has("name") && !p.get("name").isJsonNull()) out.name = p.get("name").getAsString();
        if (p.has("picUrl") && !p.get("picUrl").isJsonNull()) out.coverUrl = p.get("picUrl").getAsString();
        else if (p.has("coverImgUrl") && !p.get("coverImgUrl").isJsonNull()) out.coverUrl = p.get("coverImgUrl").getAsString();
        if (p.has("trackCount") && !p.get("trackCount").isJsonNull()) out.trackCount = p.get("trackCount").getAsInt();
        if (p.has("playCount") && !p.get("playCount").isJsonNull()) out.playCount = p.get("playCount").getAsLong();
        if (p.has("creator") && p.get("creator").isJsonObject()) {
            JsonObject c = p.getAsJsonObject("creator");
            if (c.has("nickname") && !c.get("nickname").isJsonNull()) out.creatorNickname = c.get("nickname").getAsString();
            if (c.has("userId") && !c.get("userId").isJsonNull()) out.creatorUid = c.get("userId").getAsLong();
        }
        return out;
    }

    private static String extractLyric(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject(key);
        if (!obj.has("lyric") || obj.get("lyric").isJsonNull()) return null;
        String value = obj.get("lyric").getAsString();
        return value.isBlank() ? null : value;
    }

    private String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        if (sb.length() > 0) sb.append("; ");
        sb.append("os=pc; appver=8.10.05");
        return sb.toString();
    }

    private Map<String, String> parseCookieInput(String input) {
        Map<String, String> out = new LinkedHashMap<>();
        String text = unwrapClipboardText(input);
        parseCookieJson(text, out);
        parseCookieText(text, out);
        String decoded = urlDecode(text);
        if (!decoded.equals(text)) {
            parseCookieJson(decoded, out);
            parseCookieText(decoded, out);
        }
        return out;
    }

    private void parseCookieJson(String text, Map<String, String> out) {
        String trimmed = text == null ? "" : text.trim();
        if ((!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                && (!trimmed.startsWith("[") || !trimmed.endsWith("]"))) return;
        try {
            collectCookieJson(parser.parse(trimmed), out);
        } catch (Throwable ignored) {
        }
    }

    private void collectCookieJson(JsonElement element, Map<String, String> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectCookieJson(child, out);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("cookies")) collectCookieJson(obj.get("cookies"), out);
        if (obj.has("cookie")) collectCookieJson(obj.get("cookie"), out);
        if (obj.has("name") && obj.has("value")
                && obj.get("name").isJsonPrimitive() && obj.get("value").isJsonPrimitive()) {
            putCookie(out, obj.get("name").getAsString(), obj.get("value").getAsString());
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (Set.of("name", "value", "domain", "path", "expirationdate", "expires",
                    "hostonly", "httponly", "secure", "session", "samesite", "storeid").contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                putCookie(out, key, value.getAsString());
            }
        }
    }

    private void parseCookieText(String text, Map<String, String> out) {
        if (text == null) return;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            String value = stripCookiePrefix(line.trim());
            if (value.isBlank()) continue;
            if (value.indexOf('\t') >= 0) {
                String[] cells = value.split("\t");
                if (cells.length >= 7) {
                    putCookie(out, cells[5], cells[6]);
                } else if (cells.length >= 2) {
                    putCookie(out, cells[0], cells[1]);
                }
            }
            for (String part : value.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                putCookie(out, part.substring(0, eq), part.substring(eq + 1));
            }
        }
    }

    private static String unwrapClipboardText(String input) {
        String text = input == null ? "" : input.trim();
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t");
    }

    private static String stripCookiePrefix(String line) {
        String value = line;
        String lower = value.toLowerCase(Locale.ROOT);
        int cookie = lower.indexOf("cookie:");
        int setCookie = lower.indexOf("set-cookie:");
        int prefix = cookie >= 0 ? cookie + "cookie:".length() : (setCookie >= 0 ? setCookie + "set-cookie:".length() : -1);
        if (prefix >= 0) {
            value = value.substring(prefix).trim();
            if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private static void putCookie(Map<String, String> out, String rawName, String rawValue) {
        String name = rawName == null ? "" : rawName.trim();
        String value = rawValue == null ? "" : rawValue.trim();
        if (name.isEmpty() || value.isEmpty()) return;
        String lower = name.toLowerCase(Locale.ROOT);
        if (Set.of("domain", "path", "expires", "max-age", "samesite", "secure", "httponly", "priority").contains(lower)) return;
        out.put(name, value);
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s == null ? "" : s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }

    private void captureSetCookies(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;
        boolean changed = false;
        for (String sc : setCookies) {
            int eq = sc.indexOf('=');
            int semi = sc.indexOf(';');
            if (eq <= 0) continue;
            String name = sc.substring(0, eq).trim();
            String val = sc.substring(eq + 1, semi > eq ? semi : sc.length()).trim();
            if (Set.of("domain", "path", "expires", "max-age", "samesite").contains(name.toLowerCase(Locale.ROOT))) continue;
            String prev = cookies.put(name, val);
            if (!Objects.equals(prev, val)) changed = true;
        }
        if (changed) saveCookies();
    }

    private void loadCookies() {
        try {
            io.github.wzhy233.ardion.util.client.ArdionHome.migrateIfMissing(previousCookieFile, cookieFile);
        } catch (IOException e) {
            Ardion.LOGGER.warn("Failed to migrate Netease cookies", e);
        }
        migrateLegacyCookies();
        if (!Files.exists(cookieFile)) return;
        try {
            JsonElement el = parser.parse(Files.readString(cookieFile, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive()) cookies.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Throwable e) {
            Ardion.LOGGER.warn("Failed to load Netease cookies", e);
        }
    }

    private void saveCookies() {
        try {
            Files.createDirectories(configDir);
            Files.writeString(cookieFile, gson.toJson(new LinkedHashMap<>(cookies)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Ardion.LOGGER.warn("Failed to save Netease cookies", e);
        }
    }

    private void migrateLegacyCookies() {
        if (Files.exists(cookieFile) || !Files.exists(legacyCookieFile)) {
            return;
        }
        try {
            Files.createDirectories(configDir);
            Files.move(legacyCookieFile, cookieFile);
        } catch (IOException e) {
            Ardion.LOGGER.warn("Failed to migrate Netease cookies", e);
        }
    }

    private static String formEncode(Map<String, String> kv) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEnc(e.getKey())).append('=').append(urlEnc(e.getValue()));
        }
        return sb.toString();
    }

    private static String urlEnc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (is) {
            int limit = 8 * 1024 * 1024;
            byte[] bytes = is.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Netease response too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
