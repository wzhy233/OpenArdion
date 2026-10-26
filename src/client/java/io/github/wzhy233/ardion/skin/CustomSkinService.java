package io.github.wzhy233.ardion.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wzhy233.ardion.Ardion;
import io.github.wzhy233.ardion.util.client.ArdionHome;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.AssetInfo.TextureAsset;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CustomSkinService {
    private static final int MAX_TEXTURE_BYTES = 2 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ardion-custom-skin");
        thread.setDaemon(true);
        return thread;
    });
    private static final State LOCAL = new State("local", true);
    private static final Map<String, State> SHARED = new HashMap<>();

    private CustomSkinService() {
    }

    public static void request(String name) {
        request(LOCAL, name);
    }

    public static void clear() {
        clear(LOCAL);
        clearShared();
    }

    public static SkinTextures textures(boolean cape, PlayerSkinType modelOverride) {
        return textures(LOCAL, cape, modelOverride);
    }

    public static String loadedName() {
        Loaded loaded = LOCAL.loaded.get();
        return loaded == null ? "" : loaded.meta.name;
    }

    public static void syncShared(Set<String> names) {
        Set<String> wanted = new HashSet<>();
        for (String name : names) {
            if (name != null && isValidName(name)) wanted.add(name.toLowerCase(Locale.ROOT));
        }
        SHARED.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            clear(entry.getValue());
            return true;
        });
        for (String name : wanted) {
            if (SHARED.containsKey(name)) continue;
            State state = new State("shared/" + name, false);
            SHARED.put(name, state);
            request(state, name);
        }
    }

    public static SkinTextures sharedTextures(String name, boolean cape, PlayerSkinType modelOverride) {
        State state = SHARED.get(name.toLowerCase(Locale.ROOT));
        return state == null ? null : textures(state, cape, modelOverride);
    }

    public static void clearShared() {
        SHARED.values().forEach(CustomSkinService::clear);
        SHARED.clear();
    }

    private static void request(State state, String name) {
        long token = state.generation.incrementAndGet();
        state.results.clear();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            state.loaded.set(null);
            invalidatePublished(state);
            return;
        }
        if (!isValidName(trimmed)) {
            state.loaded.set(null);
            invalidatePublished(state);
            if (state.notify) OutputUtil.error("Invalid username \"" + trimmed + "\".");
            return;
        }

        Loaded current = state.loaded.get();
        if (current != null && !current.meta.name.equalsIgnoreCase(trimmed)) {
            state.loaded.set(null);
            invalidatePublished(state);
        }
        EXECUTOR.execute(() -> load(state, trimmed, token));
    }

    private static void clear(State state) {
        state.generation.incrementAndGet();
        state.results.clear();
        state.loaded.set(null);
        invalidatePublished(state);
        if (state.registeredBody != null || state.registeredCape != null || state.registeredElytra != null) {
            var textures = MinecraftClient.getInstance().getTextureManager();
            if (state.registeredBody != null) textures.destroyTexture(state.registeredBody);
            if (state.registeredCape != null) textures.destroyTexture(state.registeredCape);
            if (state.registeredElytra != null) textures.destroyTexture(state.registeredElytra);
        }
        state.registeredBody = null;
        state.registeredCape = null;
        state.registeredElytra = null;
    }

    private static SkinTextures textures(State state, boolean cape, PlayerSkinType modelOverride) {
        drain(state);
        Loaded loaded = state.loaded.get();
        if (loaded == null) {
            return null;
        }

        PlayerSkinType model = modelOverride == null ? loaded.model : modelOverride;
        TextureAsset capeTexture = cape ? loaded.cape : null;
        TextureAsset elytraTexture = cape ? loaded.elytra : null;
        if (state.published.get() != null && state.publishedLoaded == loaded && state.publishedCape == cape && state.publishedModel == model) {
            return state.published.get();
        }

        SkinTextures textures = new SkinTextures(loaded.body, capeTexture, elytraTexture, model, true);
        state.publishedLoaded = loaded;
        state.publishedCape = cape;
        state.publishedModel = model;
        state.published.set(textures);
        return textures;
    }

    private static void load(State state, String name, long token) {
        if (token != state.generation.get()) {
            return;
        }

        Meta cached = readCache(name);
        if (cached != null) {
            finish(state, new Result(token, cached, true, null, false));
        }

        try {
            Meta fresh = fetchProfile(name);
            if (token != state.generation.get()) {
                return;
            }
            if (cached == null || !cached.sameTextures(fresh)) {
                storeTextures(fresh, cached);
                writeMeta(fresh);
            }
            writeName(name, fresh.uuid);
            finish(state, new Result(token, fresh, true, null, false));
        } catch (NotFoundException notFound) {
            finish(state, new Result(token, null, true, "No premium account named " + name + ".", true));
        } catch (Exception exception) {
            if (token != state.generation.get()) {
                return;
            }
            Ardion.LOGGER.warn("Custom skin failed for {}: {}", name, exception.toString());
            String message = cached == null
                    ? "Couldn't load skin for " + name + ": " + exception.getMessage()
                    : "Couldn't refresh skin for " + name + ": " + exception.getMessage();
            finish(state, new Result(token, null, false, message, cached == null));
        }
    }

    private static void finish(State state, Result result) {
        if (result.token == state.generation.get()) {
            state.results.add(result);
        }
    }

    private static void drain(State state) {
        long token = state.generation.get();
        Meta apply = null;
        boolean shouldApply = false;
        Result result;
        while ((result = state.results.poll()) != null) {
            if (result.token != token) {
                continue;
            }
            if (result.apply) {
                apply = result.meta;
                shouldApply = true;
            }
            if (state.notify && result.message != null) {
                if (result.error) {
                    OutputUtil.error(result.message);
                } else {
                    OutputUtil.warn(result.message);
                }
            }
        }
        if (!shouldApply) {
            return;
        }
        if (apply == null) {
            state.loaded.set(null);
            invalidatePublished(state);
            return;
        }

        Loaded existing = state.loaded.get();
        if (existing != null && existing.meta.sameTextures(apply)) {
            return;
        }

        try {
            boolean update = existing != null && existing.meta.name.equalsIgnoreCase(apply.name);
            Loaded loaded = upload(state, apply);
            state.loaded.set(loaded);
            invalidatePublished(state);
            String detail = (loaded.model == PlayerSkinType.SLIM ? "slim" : "wide")
                    + (loaded.cape == null ? "" : ", cape");
            if (state.notify) OutputUtil.info((update ? "Updated skin for " : "Loaded skin for ") + loaded.meta.name + " (" + detail + ").");
        } catch (Exception exception) {
            Ardion.LOGGER.warn("Custom skin apply failed for {}: {}", apply.name, exception.toString());
            clear(state);
            if (state.notify) OutputUtil.error("Couldn't apply skin for " + apply.name + ": " + exception.getMessage());
        }
    }

    private static Loaded upload(State state, Meta meta) throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        Path directory = profileDirectory(meta.uuid);
        NativeImage skin = prepareSkin(readImage(directory.resolve("skin.png")));
        TextureAsset body;
        try {
            body = bind(client, textureId(state, meta.uuid, "body"), state.registeredBody, skin);
            state.registeredBody = textureId(state, meta.uuid, "body");
        } catch (RuntimeException exception) {
            skin.close();
            throw exception;
        }

        TextureAsset cape = null;
        if (meta.capeUrl != null && Files.isRegularFile(directory.resolve("cape.png"))) {
            NativeImage image = readImage(directory.resolve("cape.png"));
            try {
                Identifier id = textureId(state, meta.uuid, "cape");
                cape = bind(client, id, state.registeredCape, image);
                state.registeredCape = id;
            } catch (RuntimeException exception) {
                image.close();
                throw exception;
            }
        } else if (state.registeredCape != null) {
            client.getTextureManager().destroyTexture(state.registeredCape);
            state.registeredCape = null;
        }

        TextureAsset elytra = null;
        boolean distinctElytra = meta.elytraUrl != null && !meta.elytraUrl.equals(meta.capeUrl);
        if (distinctElytra && Files.isRegularFile(directory.resolve("elytra.png"))) {
            NativeImage image = readImage(directory.resolve("elytra.png"));
            try {
                Identifier id = textureId(state, meta.uuid, "elytra");
                elytra = bind(client, id, state.registeredElytra, image);
                state.registeredElytra = id;
            } catch (RuntimeException exception) {
                image.close();
                throw exception;
            }
        } else if (state.registeredElytra != null) {
            client.getTextureManager().destroyTexture(state.registeredElytra);
            state.registeredElytra = null;
        }

        return new Loaded(meta, body, cape, elytra, modelOf(meta.model));
    }

    private static TextureAsset bind(MinecraftClient client, Identifier id, Identifier previous, NativeImage image) {
        if (previous != null && !previous.equals(id)) {
            client.getTextureManager().destroyTexture(previous);
        }
        client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> id.toString(), image));
        return new AssetInfo.TextureAssetInfo(id, id);
    }

    private static Identifier textureId(State state, String uuid, String part) {
        return Identifier.of("ardion", "skin/" + state.scope + "/" + uuid + "/" + part);
    }

    private static Meta fetchProfile(String name) throws IOException, InterruptedException {
        JsonObject lookup = getJson("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (lookup == null) {
            throw new NotFoundException();
        }
        String uuid = lookup.get("id").getAsString().replace("-", "").toLowerCase(Locale.ROOT);
        if (!uuid.matches("[0-9a-f]{32}")) {
            throw new IOException("invalid uuid");
        }
        String officialName = lookup.get("name").getAsString();
        JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (profile == null || !profile.has("properties")) {
            throw new IOException("empty profile");
        }

        String encoded = null;
        for (var element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if ("textures".equals(property.get("name").getAsString())) {
                encoded = property.get("value").getAsString();
                break;
            }
        }
        if (encoded == null) {
            throw new IOException("profile has no textures");
        }

        JsonObject decoded = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded))).getAsJsonObject();
        JsonObject textures = decoded.getAsJsonObject("textures");
        if (textures == null || !textures.has("SKIN")) {
            throw new IOException("profile has no skin");
        }

        JsonObject skin = textures.getAsJsonObject("SKIN");
        String model = "wide";
        if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
            model = PlayerSkinType.byModelMetadata(skin.getAsJsonObject("metadata").get("model").getAsString()) == PlayerSkinType.SLIM
                    ? "slim" : "wide";
        }

        Meta meta = new Meta();
        meta.name = officialName;
        meta.uuid = uuid;
        meta.model = model;
        meta.skinUrl = officialTextureUrl(skin.get("url").getAsString());
        meta.capeUrl = textures.has("CAPE") ? officialTextureUrl(textures.getAsJsonObject("CAPE").get("url").getAsString()) : null;
        meta.elytraUrl = textures.has("ELYTRA") ? officialTextureUrl(textures.getAsJsonObject("ELYTRA").get("url").getAsString()) : null;
        return meta;
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Ardion")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.statusCode() == 204) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void storeTextures(Meta fresh, Meta cached) throws IOException, InterruptedException {
        Path directory = profileDirectory(fresh.uuid);
        Files.createDirectories(directory);
        if (cached == null || !Objects.equals(cached.skinUrl, fresh.skinUrl) || !Files.isRegularFile(directory.resolve("skin.png"))) {
            writeTexture(fresh.skinUrl, directory.resolve("skin.png"));
        }
        storeOptional(fresh.capeUrl, cached == null ? null : cached.capeUrl, directory.resolve("cape.png"));
        if (fresh.elytraUrl != null && fresh.elytraUrl.equals(fresh.capeUrl)) {
            Files.deleteIfExists(directory.resolve("elytra.png"));
            return;
        }
        storeOptional(fresh.elytraUrl, cached == null ? null : cached.elytraUrl, directory.resolve("elytra.png"));
    }

    private static void storeOptional(String url, String cachedUrl, Path file) throws IOException, InterruptedException {
        if (url == null) {
            Files.deleteIfExists(file);
            return;
        }
        if (!url.equals(cachedUrl) || !Files.isRegularFile(file)) {
            writeTexture(url, file);
        }
    }

    private static void writeTexture(String url, Path file) throws IOException, InterruptedException {
        byte[] body = download(url);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, body);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Ardion")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("texture HTTP " + response.statusCode());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_TEXTURE_BYTES) {
                    throw new IOException("texture too large");
                }
                output.write(buffer, 0, read);
            }
            byte[] body = output.toByteArray();
            if (body.length < 8 || (body[0] & 0xFF) != 0x89 || body[1] != 'P' || body[2] != 'N' || body[3] != 'G') {
                throw new IOException("texture is not a png");
            }
            return body;
        }
    }

    private static String officialTextureUrl(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri = URI.create(raw.trim());
        if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) {
            throw new IOException("rejected texture host");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery();
        return "https://textures.minecraft.net" + path + (query == null ? "" : "?" + query);
    }

    private static Meta readCache(String name) {
        try {
            Path pointer = namesDirectory().resolve(name.toLowerCase(Locale.ROOT));
            if (!Files.isRegularFile(pointer)) {
                return null;
            }
            String uuid = Files.readString(pointer).trim().toLowerCase(Locale.ROOT);
            if (!uuid.matches("[0-9a-f]{32}")) {
                return null;
            }
            Path metaFile = profileDirectory(uuid).resolve("meta.json");
            if (!Files.isRegularFile(metaFile) || !Files.isRegularFile(profileDirectory(uuid).resolve("skin.png"))) {
                return null;
            }
            Meta meta = GSON.fromJson(Files.readString(metaFile), Meta.class);
            if (meta == null || meta.uuid == null || !meta.uuid.equals(uuid) || meta.skinUrl == null
                    || meta.name == null || !meta.name.equalsIgnoreCase(name)) {
                return null;
            }
            meta.uuid = uuid;
            return meta;
        } catch (Exception exception) {
            return null;
        }
    }

    private static void writeMeta(Meta meta) throws IOException {
        Path directory = profileDirectory(meta.uuid);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("meta.json"), GSON.toJson(meta));
    }

    private static void writeName(String name, String uuid) throws IOException {
        Path directory = namesDirectory();
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name.toLowerCase(Locale.ROOT)), uuid);
    }

    private static Path profileDirectory(String uuid) {
        return ArdionHome.root().resolve("skins").resolve(uuid);
    }

    private static Path namesDirectory() {
        return ArdionHome.root().resolve("skins").resolve("by-name");
    }

    private static NativeImage readImage(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return NativeImage.read(input);
        }
    }

    private static NativeImage prepareSkin(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != 64 || (height != 32 && height != 64)) {
            image.close();
            throw new IllegalStateException("invalid skin size " + width + "x" + height);
        }

        boolean legacy = height == 32;
        if (legacy) {
            NativeImage scaled = new NativeImage(64, 64, true);
            scaled.copyRect(image, 0, 0, 0, 0, 64, 32, false, false);
            image.close();
            image = scaled;
            image.fillRect(0, 32, 64, 32, 0);
            image.copyRect(4, 16, 16, 32, 4, 4, true, false);
            image.copyRect(8, 16, 16, 32, 4, 4, true, false);
            image.copyRect(0, 20, 24, 32, 4, 12, true, false);
            image.copyRect(4, 20, 16, 32, 4, 12, true, false);
            image.copyRect(8, 20, 8, 32, 4, 12, true, false);
            image.copyRect(12, 20, 16, 32, 4, 12, true, false);
            image.copyRect(44, 16, -8, 32, 4, 4, true, false);
            image.copyRect(48, 16, -8, 32, 4, 4, true, false);
            image.copyRect(40, 20, 0, 32, 4, 12, true, false);
            image.copyRect(44, 20, -8, 32, 4, 12, true, false);
            image.copyRect(48, 20, -16, 32, 4, 12, true, false);
            image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }

        stripAlpha(image, 0, 0, 32, 16);
        if (legacy) {
            stripColor(image, 32, 0, 64, 32);
        }
        stripAlpha(image, 0, 16, 64, 32);
        stripAlpha(image, 16, 48, 48, 64);
        return image;
    }

    private static void stripAlpha(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setColorArgb(x, y, ColorHelper.fullAlpha(image.getColorArgb(x, y)));
            }
        }
    }

    private static void stripColor(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                if (ColorHelper.getAlpha(image.getColorArgb(x, y)) < 128) {
                    return;
                }
            }
        }
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                image.setColorArgb(x, y, image.getColorArgb(x, y) & 0x00FFFFFF);
            }
        }
    }

    private static PlayerSkinType modelOf(String model) {
        return "slim".equalsIgnoreCase(model) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
    }

    private static boolean isValidName(String name) {
        if (name.length() < 3 || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (!((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '_')) {
                return false;
            }
        }
        return true;
    }

    private static void invalidatePublished(State state) {
        state.publishedLoaded = null;
        state.published.set(null);
    }

    private static final class State {
        final String scope;
        final boolean notify;
        final AtomicLong generation = new AtomicLong();
        final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        final AtomicReference<Loaded> loaded = new AtomicReference<>();
        final AtomicReference<SkinTextures> published = new AtomicReference<>();
        Identifier registeredBody;
        Identifier registeredCape;
        Identifier registeredElytra;
        boolean publishedCape;
        PlayerSkinType publishedModel;
        Loaded publishedLoaded;

        State(String scope, boolean notify) {
            this.scope = scope;
            this.notify = notify;
        }
    }

    private static final class Meta {
        String name;
        String uuid;
        String model;
        String skinUrl;
        String capeUrl;
        String elytraUrl;

        private boolean sameTextures(Meta other) {
            return other != null
                    && Objects.equals(this.uuid, other.uuid)
                    && Objects.equals(this.model, other.model)
                    && Objects.equals(this.skinUrl, other.skinUrl)
                    && Objects.equals(this.capeUrl, other.capeUrl)
                    && Objects.equals(this.elytraUrl, other.elytraUrl);
        }
    }

    private static final class Loaded {
        private final Meta meta;
        private final TextureAsset body;
        private final TextureAsset cape;
        private final TextureAsset elytra;
        private final PlayerSkinType model;

        private Loaded(Meta meta, TextureAsset body, TextureAsset cape, TextureAsset elytra, PlayerSkinType model) {
            this.meta = meta;
            this.body = body;
            this.cape = cape;
            this.elytra = elytra;
            this.model = model;
        }
    }

    private record Result(long token, Meta meta, boolean apply, String message, boolean error) {
    }

    private static final class NotFoundException extends RuntimeException {
    }
}
