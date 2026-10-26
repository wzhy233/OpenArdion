package io.github.wzhy233.ardion.gui.screen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.wzhy233.ardion.gui.shader.Effects;
import io.github.wzhy233.ardion.music.MusicPlayerService;
import io.github.wzhy233.ardion.music.MusicArtworkCache;
import io.github.wzhy233.ardion.music.MusicTrack;
import io.github.wzhy233.ardion.music.MusicSearchInput;
import io.github.wzhy233.ardion.music.MusicText;
import io.github.wzhy233.ardion.music.MusicNowPlayingView;
import io.github.wzhy233.ardion.music.MusicLikeButton;
import io.github.wzhy233.ardion.music.MusicMotion;
import io.github.wzhy233.ardion.music.MusicPageTransition;
import io.github.wzhy233.ardion.music.MusicAccountLayout;
import io.github.wzhy233.ardion.music.lyric.LyricLine;
import io.github.wzhy233.ardion.music.netease.NeteaseClient;
import io.github.wzhy233.ardion.music.netease.NeteasePlaylist;
import io.github.wzhy233.ardion.music.netease.NeteaseSong;
import io.github.wzhy233.ardion.music.netease.NeteaseUser;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MusicPlayerScreen extends io.github.wzhy233.ardion.gui.ui.AnimatedScreen {
    private static final Gson GSON = new Gson();
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static final Path SCREEN_CONFIG_DIR = io.github.wzhy233.ardion.util.client.ArdionHome.musicPlayer();
    private static final Path SCREEN_CONFIG_FILE = SCREEN_CONFIG_DIR.resolve("music-player-screen.json");
    private static final Path PREVIOUS_SCREEN_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ardion/music-player/music-player-screen.json");
    private static final Path LEGACY_SCREEN_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ardion/music-player-screen.json");
    private float sidebarWidth = 156.0F;
    private static final float MINI_PLAYER_H = 102.0F;
    private static final float NAV_ITEM_H = 46.0F;
    private static final float PANEL_RADIUS = 18.0F;
    private static final float TOP_PAD = 24.0F;
    private static final float ROW_H = 56.0F;
    private static final float APP_MAX_W = 1040.0F;
    private static final float APP_MAX_H = 650.0F;
    private static final float APP_MIN_W = 680.0F;
    private static final float APP_MIN_H = 360.0F;
    private static final float HEIGHT_RATIO = 0.90F;
    private static final float RESIZE_HANDLE = 18.0F;
    private static final int SEARCH_PAGE_SIZE = 24;
    private static final int DETAIL_BATCH_SIZE = 24;
    private static final int LIKED_INITIAL_LIMIT = 64;
    private static final int MAX_FRAME_ARTWORK_REQUESTS = 10;
    private static final long IMAGE_RETRY_COOLDOWN_MS = 30_000L;
    private static final ExecutorService DETAIL_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "Ardion-Music-Detail");
        thread.setDaemon(true);
        return thread;
    });
    private static final SavedState SAVED_STATE = new SavedState();

    private static final String ICON_HOME = "\ue9b2";
    private static final String ICON_SEARCH = "\ue8b6";
    private static final String ICON_HEART = "\ue87e";
    private static final String ICON_HISTORY = "\ue8b3";
    private static final String ICON_ALBUM = "\ue019";
    private static final String ICON_QUEUE = "\ue03d";
    private static final String ICON_PLAY = "\ue037";
    private static final String ICON_PAUSE = "\ue034";
    private static final String ICON_PREV = "\ue045";
    private static final String ICON_NEXT = "\ue044";
    private static final String ICON_LYRICS = "\uec0b";
    private static final String ICON_DOWN = "\ue313";
    private static final String ICON_CLOSE = "\ue5cd";
    private static final String ICON_REFRESH = "\ue5d5";
    private static final String ICON_LOGIN = "\uea77";
    private static final String ICON_LOGOUT = "\ue9ba";
    private static final String ICON_VOLUME = "\ue050";

    private enum Page {
        HOME("Home", ICON_HOME),
        SEARCH("Search", ICON_SEARCH),
        LIKED("Liked Songs", ICON_HEART),
        FAVORITES("Playlists", ICON_ALBUM),
        RECENT("Recent", ICON_HISTORY),
        PLAYLIST_DETAIL("Playlist", ICON_QUEUE);

        final String label;
        final String icon;

        Page(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private final SkiaRenderManager skia = SkiaRenderManager.get();
    private static final java.util.concurrent.Semaphore DETAIL_SLOTS = new java.util.concurrent.Semaphore(2);
    private final AtomicBoolean artworkDetailsLoading = new AtomicBoolean();
    private volatile boolean removed;
    private final Set<Long> fetchingSongDetails = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> songDetailRetryAfter = new java.util.LinkedHashMap<>();
    private final List<NeteasePlaylist> playlists = new ArrayList<>();
    private final List<NeteaseSong> songs = new ArrayList<>();
    private final List<NeteaseSong> dailySongs = new ArrayList<>();
    private final List<NeteaseSong> likedSongs = new ArrayList<>();
    private final List<NeteaseSong> recentSongs = new ArrayList<>();
    private final List<NeteaseSong> activeQueue = new ArrayList<>();
    private final List<HitBox> songHits = new ArrayList<>();
    private final List<HitBox> playlistHits = new ArrayList<>();
    private final List<HitBox> navHits = new ArrayList<>();
    private final List<HitBox> hotSearchHits = new ArrayList<>();

    private Page page = Page.HOME;
    private final io.github.wzhy233.ardion.gui.ui.UiSwap<Page> contentTransition = new io.github.wzhy233.ardion.gui.ui.UiSwap<>();
    private Page detailReturnPage = Page.HOME;
    private NeteasePlaylist detailPlaylist;
    private String status = "Ready";
    private String searchText = "";
    private boolean searchFocused = false;
    private final MusicSearchInput searchEditor = new MusicSearchInput();
    private float searchX, searchY, searchW, searchH, searchTextScroll;
    private boolean selectingSearch;
    private boolean draggingScrollbar;
    private float scrollbarY, scrollbarH, scrollbarTravel, scrollbarDragOffset;
    private long previewPositionMs = -1L;
    private long animationNanos;
    private float frameSeconds = 1.0F / 60.0F;
    private final List<HitBox> lyricHits = new ArrayList<>();
    private float lyricX, lyricY, lyricW, lyricH;
    private MusicNowPlayingView nowPlaying;
    private final MusicLikeButton likeButton = new MusicLikeButton();
    private MusicNowPlayingView.Layout nowPlayingLayout;
    private final MusicPageTransition overlayMotion = new MusicPageTransition();
    private final MusicMotion contentMotion = new MusicMotion(0);
    private float contentScrollTarget;

    private boolean loading = false;
    private String activeSearchQuery = "";
    private int searchOffset = 0;
    private boolean searchHasMore = false;
    private String pendingSearchQuery;
    private boolean playlistRequestRunning;
    private NeteasePlaylist pendingPlaylist;
    private float contentScroll = 0.0F;
    private boolean lyricsOpen = false;
    private float lyricsSlide = 1.0F;
    private boolean draggingProgress = false;
    private boolean progressDragResume = false;
    private boolean draggingPanel = false;
    private boolean resizingPanel = false;
    private float appX;
    private float appY;
    private float appW;
    private float appH;
    private float dragOffsetX;
    private float dragOffsetY;
    private float resizeStartMouseX;
    private float resizeStartMouseY;
    private float resizeStartW;
    private float resizeStartH;
    private long lastPanelConfigSaveMs;

    private float progressX;
    private float progressY;
    private float progressW;
    private float progressH;
    private float miniProgressX;
    private float miniProgressY;
    private float miniProgressW;
    private float miniProgressH;
    private float playX;
    private float playY;
    private float prevX;
    private float nextX;
    private float lyricsBtnX;
    private float lyricsBtnY;
    private float likeBtnX;
    private float likeBtnY;
    private float likeBtnR;
    private float volumeX;
    private float volumeY;
    private float volumeW;
    private float volumeH;
    private float closeLyricsX;
    private float closeLyricsY;
    private float loginX;
    private float loginY;
    private float loginW;
    private float loginH;
    private float importLoginX;
    private float importLoginY;
    private float importLoginW;
    private float importLoginH;
    private float logoutX;
    private float logoutY;
    private float logoutR;
    private float loginCloseX;
    private float loginCloseY;
    private float loginCloseR;
    private float loginRefreshX;
    private float loginRefreshY;
    private float loginRefreshW;
    private float loginRefreshH;

    private final AtomicBoolean dailyLoading = new AtomicBoolean(false);
    private final AtomicBoolean userPagesLoading = new AtomicBoolean(false);
    private final AtomicBoolean searchPageLoading = new AtomicBoolean(false);
    private final AtomicBoolean hotSearchLoading = new AtomicBoolean(false);
    private final AtomicBoolean loginPolling = new AtomicBoolean(false);
    private final AtomicBoolean userProfileLoading = new AtomicBoolean(false);
    private final AtomicBoolean likedIdsLoading = new AtomicBoolean(false);
    private final List<String> hotSearches = new ArrayList<>();
    private volatile NeteaseUser userProfile;
    private volatile Set<Long> likedIds = Set.of();
    private volatile long cachedUid = 0L;
    private volatile boolean likedIdsLoaded = false;
    private volatile boolean loginOpen = false;
    private volatile String loginUnikey;
    private volatile byte[] loginQrBytes;
    private volatile int loginStatusCode = -1;
    private volatile long loginExpireAtMs = 0L;
    private boolean draggingVolume = false;

    public MusicPlayerScreen() {
        super(Text.literal("Ardion Music Player"));
        restoreState();
        searchEditor.setText(searchText);
        searchText = searchEditor.text();
    }

    @Override
    protected void init() {
        removed = false;
        if (playlists.isEmpty()) loadHome();
        if (NeteaseClient.INSTANCE.isLoggedIn()) {
            ensureUserProfile();
            ensureLikedIdsLoaded();
            loadDaily();
            if ((page == Page.LIKED && likedSongs.isEmpty()) || (page == Page.RECENT && recentSongs.isEmpty())) {
                loadUserPages();
            }
        }
        if (hotSearches.isEmpty()) loadHotSearches();
        if (page == Page.SEARCH && songs.isEmpty() && !activeSearchQuery.isBlank()) {
            loadSearchPage(activeSearchQuery, true);
        } else if (page == Page.PLAYLIST_DETAIL && detailPlaylist != null && songs.isEmpty()) {
            openPlaylist(detailPlaylist);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        removed = true;
        loginOpen = false;
        if (nowPlaying != null) { nowPlaying.close(); nowPlaying = null; }
        saveState();
    }

    private void restoreState() {
        synchronized (SAVED_STATE) {
            if (!SAVED_STATE.panelConfigLoaded) {
                loadPanelConfig(SAVED_STATE);
                SAVED_STATE.panelConfigLoaded = true;
            }
            page = SAVED_STATE.page;
            detailReturnPage = SAVED_STATE.detailReturnPage;
            detailPlaylist = SAVED_STATE.detailPlaylist;
            status = SAVED_STATE.status;
            searchText = SAVED_STATE.searchText;
            activeSearchQuery = SAVED_STATE.activeSearchQuery;
            searchOffset = SAVED_STATE.searchOffset;
            searchHasMore = SAVED_STATE.searchHasMore;
            contentScroll = SAVED_STATE.contentScroll;
            contentScrollTarget = contentScroll;
            contentMotion.snap(contentScroll);
            lyricsOpen = SAVED_STATE.lyricsOpen;
            lyricsSlide = lyricsOpen ? 0.0F : 1.0F;
            overlayMotion.snap(lyricsSlide);
            appX = SAVED_STATE.panelX;
            appY = SAVED_STATE.panelY;
            appW = SAVED_STATE.panelW;
            appH = SAVED_STATE.panelH;

            playlists.clear();
            playlists.addAll(SAVED_STATE.playlists);
            songs.clear();
            songs.addAll(SAVED_STATE.songs);
            dailySongs.clear();
            dailySongs.addAll(SAVED_STATE.dailySongs);
            likedSongs.clear();
            likedSongs.addAll(SAVED_STATE.likedSongs);
            recentSongs.clear();
            recentSongs.addAll(SAVED_STATE.recentSongs);
            activeQueue.clear();
            activeQueue.addAll(SAVED_STATE.activeQueue);
            hotSearches.clear();
            hotSearches.addAll(SAVED_STATE.hotSearches);
        }
    }

    private void saveState() {
        synchronized (SAVED_STATE) {
            SAVED_STATE.page = page;
            SAVED_STATE.detailReturnPage = detailReturnPage;
            SAVED_STATE.detailPlaylist = detailPlaylist;
            SAVED_STATE.status = status;
            SAVED_STATE.searchText = searchText;
            SAVED_STATE.activeSearchQuery = activeSearchQuery;
            SAVED_STATE.searchOffset = searchOffset;
            SAVED_STATE.searchHasMore = searchHasMore;
            SAVED_STATE.contentScroll = contentScroll;
            SAVED_STATE.lyricsOpen = lyricsOpen;
            SAVED_STATE.panelX = appX;
            SAVED_STATE.panelY = appY;
            SAVED_STATE.panelW = appW;
            SAVED_STATE.panelH = appH;

            SAVED_STATE.playlists.clear();
            SAVED_STATE.playlists.addAll(playlists);
            SAVED_STATE.songs.clear();
            SAVED_STATE.songs.addAll(songs);
            SAVED_STATE.dailySongs.clear();
            SAVED_STATE.dailySongs.addAll(dailySongs);
            SAVED_STATE.likedSongs.clear();
            SAVED_STATE.likedSongs.addAll(likedSongs);
            SAVED_STATE.recentSongs.clear();
            SAVED_STATE.recentSongs.addAll(recentSongs);
            SAVED_STATE.activeQueue.clear();
            SAVED_STATE.activeQueue.addAll(activeQueue);
            SAVED_STATE.hotSearches.clear();
            SAVED_STATE.hotSearches.addAll(hotSearches);
        }
        savePanelConfigNow();
    }

    private void savePanelGeometryState(boolean force) {
        synchronized (SAVED_STATE) {
            SAVED_STATE.panelX = appX;
            SAVED_STATE.panelY = appY;
            SAVED_STATE.panelW = appW;
            SAVED_STATE.panelH = appH;
            SAVED_STATE.panelConfigLoaded = true;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastPanelConfigSaveMs < 250L) return;
        savePanelConfigNow();
    }

    private void savePanelConfigNow() {
        lastPanelConfigSaveMs = System.currentTimeMillis();
        savePanelConfig();
    }

    private static void loadPanelConfig(SavedState state) {
        migrateLegacyPanelConfig();
        if (!Files.exists(SCREEN_CONFIG_FILE)) return;
        try {
            JsonElement element = JSON_PARSER.parse(Files.readString(SCREEN_CONFIG_FILE, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) return;
            JsonObject root = element.getAsJsonObject();
            state.panelX = jsonFloat(root, "x", state.panelX);
            state.panelY = jsonFloat(root, "y", state.panelY);
            state.panelW = jsonFloat(root, "width", state.panelW);
            state.panelH = jsonFloat(root, "height", state.panelH);
        } catch (Throwable ignored) {
        }
    }

    private void savePanelConfig() {
        if (appW <= 0.0F || appH <= 0.0F) return;
        try {
            Files.createDirectories(SCREEN_CONFIG_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("x", appX);
            root.addProperty("y", appY);
            root.addProperty("width", appW);
            root.addProperty("height", appH);
            Files.writeString(SCREEN_CONFIG_FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void migrateLegacyPanelConfig() {
        try {
            io.github.wzhy233.ardion.util.client.ArdionHome.migrateIfMissing(PREVIOUS_SCREEN_CONFIG_FILE, SCREEN_CONFIG_FILE);
            if (Files.exists(SCREEN_CONFIG_FILE) || !Files.exists(LEGACY_SCREEN_CONFIG_FILE)) return;
            Files.createDirectories(SCREEN_CONFIG_DIR);
            Files.copy(LEGACY_SCREEN_CONFIG_FILE, SCREEN_CONFIG_FILE);
        } catch (IOException ignored) {
        }
    }

    private static float jsonFloat(JsonObject object, String key, float fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsFloat();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    @Override
    protected void renderUi(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimations();
        boolean rendered = skia.renderNow(context, canvas -> renderSkia(canvas, width, height, mouseX, mouseY));
        if (!rendered) {
            context.fill(0, 0, width, height, 0xE0101118);
            context.drawTextWithShadow(textRenderer, "Ardion Skia music player failed", 20, 20, 0xFFFF6B6B);
            context.drawTextWithShadow(textRenderer, skia.failureReason(), 20, 38, 0xFFFFFFFF);
        }

    }

    private void updateAnimations() {
        long now = System.nanoTime();
        frameSeconds = animationNanos == 0L ? 1.0F / 60.0F : Math.min(0.1F, (now - animationNanos) / 1_000_000_000.0F);
        animationNanos = now;
        lyricsSlide = overlayMotion.step(lyricsOpen, frameSeconds);
    }

    private void renderSkia(Canvas canvas, float w, float h, float mx, float my) {
        if (w < 420.0F || h < 260.0F) {
            drawText("Window is too small", w * 0.5F - 70.0F, h * 0.5F, 16.0F, 0xCCFFFFFF, true);
            return;
        }

        ensurePanelLayout(w, h);
        sidebarWidth = appW >= 720 ? clamp(appW * 0.21F, 180, 220) : appW >= 600 ? 168 : 132;
        songHits.clear();
        playlistHits.clear();
        hotSearchHits.clear();
        lyricHits.clear();
        contentScrollTarget = clamp(contentScrollTarget, 0, maxContentScroll());
        contentScroll = clamp(contentMotion.step(contentScrollTarget, frameSeconds, 22), 0, maxContentScroll());
        float localMx = mx - appX;
        float localMy = my - appY;

        canvas.save();
        canvas.translate(appX, appY);
        drawMainBackdrop(canvas, appW, appH);
        canvas.save();
        canvas.clipRRect(RRect.makeXYWH(0, 0, appW, appH, PANEL_RADIUS), true);
        float contentW = appW - sidebarWidth;
        float contentH = appH - MINI_PLAYER_H;
        if (lyricsSlide > 0) {
            renderSidebar(canvas, 0.0F, 0.0F, sidebarWidth, contentH, localMx, localMy);
            canvas.save();
            canvas.clipRect(Rect.makeXYWH(sidebarWidth, 0.0F, contentW, contentH));
            renderContent(canvas, sidebarWidth, 0.0F, contentW, contentH, localMx, localMy);
            renderContentScrollbar(contentH);
            canvas.restore();
            renderMiniPlayer(canvas, 0.0F, appH - MINI_PLAYER_H, appW, MINI_PLAYER_H, localMx, localMy);
            drawRoundIconButton(appW - 26, 26, 16, ICON_CLOSE, localMx, localMy, false);

        }
        canvas.restore();

        if (lyricsSlide < 1) {
            MusicPageTransition.draw(canvas, appW, appH, PANEL_RADIUS, lyricsSlide,
                    pageCanvas -> renderLyricsOverlay(pageCanvas, appW, appH,
                            overlayMotion.moving() ? -1000 : localMx,
                            overlayMotion.moving() ? -1000 : localMy - MusicPageTransition.offset(lyricsSlide)));
        }
        float loginOpacity = ui.visible("login-modal", loginOpen);
        if (loginOpacity > 0) {
            try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, null, loginOpacity)) {
                renderLoginModal(canvas, appW, appH, loginOpen ? localMx : -10000, loginOpen ? localMy : -10000);
            }
        }
        if (!loginOpen && lyricsSlide == 1) drawResizeHandle(localMx, localMy);
        canvas.restore();
    }

    private void drawMainBackdrop(Canvas canvas, float w, float h) {
        applyMusicBackdropBlur(0.0F, 0.0F, w, h, PANEL_RADIUS);
        int bgAlpha = HudSetting.backgroundAlpha();
        if (bgAlpha > 0 && HudSetting.shadow()) Skia.drawShadow(0.0F, 0.0F, w, h, PANEL_RADIUS);
        Skia.drawRoundedRect(0.0F, 0.0F, w, h, PANEL_RADIUS, color(0xFA1C1C20));
        Skia.drawRect(0.0F, h - MINI_PLAYER_H, w, 1.0F, color(0x18FFFFFF));
    }

    private void renderContentScrollbar(float contentH) {
        float range = maxContentScroll();
        float top = scrollBodyTop();
        float viewport = Math.max(1, contentH - top - 8);
        if (range <= 0) { scrollbarH = 0; return; }
        scrollbarH = Math.min(viewport, Math.max(24, viewport * viewport / (range + viewport)));
        scrollbarTravel = viewport - scrollbarH;
        scrollbarY = top + scrollbarTravel * contentScroll / range;
        Skia.drawRoundedRect(appW - 8, top, 3, viewport, 1.5F, color(0x10FFFFFF));
        Skia.drawRoundedRect(appW - 8, scrollbarY, 3, scrollbarH, 1.5F, color(draggingScrollbar ? 0xCCFFFFFF : 0x55FFFFFF));
    }

    private void drawResizeHandle(float mx, float my) {
        boolean hover = inside(mx, my, appW - RESIZE_HANDLE, appH - RESIZE_HANDLE, RESIZE_HANDLE, RESIZE_HANDLE)
                || resizingPanel;
        int argb = hoverColor("resize", hover, 0x32FFFFFF, 0x66FFFFFF);
        Skia.drawRoundedRect(appW - 16.0F, appH - 7.0F, 10.0F, 1.0F, 0.5F, color(argb));
        Skia.drawRoundedRect(appW - 7.0F, appH - 16.0F, 1.0F, 10.0F, 0.5F, color(argb));
    }

    private void ensurePanelLayout(float screenW, float screenH) {
        float maxW = Math.max(320.0F, screenW - 28.0F);
        float maxH = Math.max(260.0F, screenH - 28.0F);
        boolean initialized = false;
        if (appW <= 0.0F || appH <= 0.0F) {
            appW = Math.min(APP_MAX_W, maxW);
            appH = Math.min(Math.min(APP_MAX_H, maxH), Math.max(APP_MIN_H, screenH * HEIGHT_RATIO));
            appX = (screenW - appW) * 0.5F;
            appY = (screenH - appH) * 0.5F;
            initialized = true;
        }
        constrainPanel(screenW, screenH);
        if (initialized) {
            savePanelGeometryState(true);
        }
    }

    private void constrainPanel(float screenW, float screenH) {
        float maxW = Math.max(320.0F, screenW - 28.0F);
        float maxH = Math.max(260.0F, screenH - 28.0F);
        float minW = Math.min(APP_MIN_W, maxW);
        float minH = Math.min(APP_MIN_H, maxH);
        appW = clamp(appW, minW, maxW);
        appH = clamp(appH, minH, maxH);
        appX = clamp(appX, 6.0F, Math.max(6.0F, screenW - appW - 6.0F));
        appY = clamp(appY, 6.0F, Math.max(6.0F, screenH - appH - 6.0F));
    }

    private void applyBackdropBlur(float x, float y, float width, float height, float radius) {
        if (!HudSetting.blur()) return;
        Rect bounds = Rect.makeXYWH(x, y, width, height);
        Skia.save();
        Skia.clip(x, y, width, height, radius);
        if (HudSetting.blurSksl()) {
            Effects.blurBackdropSKSL(Skia.getCanvas(), Skia.getSurface(), bounds,
                    HudSetting.blurSigma(), HudSetting.skslShader());
        } else {
            Effects.blurBackdrop(Skia.getCanvas(), bounds, HudSetting.blurSigma());
        }
        Skia.restore();
    }

    private void applyMusicBackdropBlur(float x, float y, float width, float height, float radius) {
        if (!HudSetting.blur()) return;
        if (width * height > 170_000.0F) return;
        applyBackdropBlur(x, y, width, height, radius);
    }

    private void renderSidebar(Canvas canvas, float x, float y, float w, float h, float mx, float my) {
        navHits.clear();
        Skia.drawRect(x, y, w, h, color(0xFF242428));
        Skia.drawRect(x + w - 1.0F, y, 1.0F, h, color(0x0AFFFFFF));
        drawText("Music", x + 22.0F, y + 23.0F, 23.0F, 0xF5FFFFFF, true);

        float rowY = y + (h >= 320 ? 76 : 58);
        float navHeight = Math.min(NAV_ITEM_H, (h - (rowY - y) - 74) / 5);
        for (Page nav : new Page[]{Page.HOME, Page.SEARCH, Page.LIKED, Page.FAVORITES, Page.RECENT}) {
            boolean active = nav == page || (page == Page.PLAYLIST_DETAIL && nav == detailReturnPage);
            boolean hover = inside(mx, my, x + 8.0F, rowY + 4.0F, w - 16.0F, navHeight - 8.0F);
            float emphasis = ui.value("nav:" + nav.name(), active ? 1 : hover ? 0.45F : 0,
                    io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
            if (emphasis > 0.01F) Skia.drawRoundedRect(x + 10, rowY + 4, w - 20, navHeight - 8, 7,
                    color((Math.round(emphasis * 36) << 24) | 0xFFFFFF));
            drawIcon(nav.icon, x + 29.0F, rowY + navHeight * 0.5F, 18.0F, 0xFFFF627D);
            drawTextLeftInRect(limit(nav.label, mediumFont(12.0F), w - 62.0F), x + 50.0F, rowY, w - 62.0F,
                    navHeight, 12.0F,
                    io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x98FFFFFF, 0xF0FFFFFF, emphasis), true);
            navHits.add(new HitBox(x, rowY, w, navHeight, nav));
            rowY += navHeight;
        }

        renderAccountFooter(x, y + h - 66, w, 60, mx, my);
    }

    private void renderAccountFooter(float x, float y, float w, float h, float mx, float my) {
        loginW = 0.0F;
        importLoginW = 0.0F;
        logoutR = 0.0F;
        if (!NeteaseClient.INSTANCE.isLoggedIn()) {
            loginX = x + 12.0F;
            loginY = y + (h - 34.0F) * 0.5F;
            loginW = w - 24.0F;
            loginH = 34.0F;
            boolean hover = inside(mx, my, loginX, loginY, loginW, loginH);
            Skia.drawRoundedRect(loginX, loginY, loginW, loginH, 8.0F, color(hoverColor("account-login", hover, 0x13FFFFFF, 0x28FFFFFF)));
            drawTextCenteredInRect("Sign in", loginX, loginY, loginW, loginH, 12.0F, 0xFFFF627D, true);
            return;
        }
        ensureUserProfile();
        ensureLikedIdsLoaded();
        Skia.drawRect(x + 16, y, w - 32, 1, color(0x0CFFFFFF));
        MusicAccountLayout account = MusicAccountLayout.fit(x, y, w, h);
        NeteaseUser user = userProfile;
        float avatarSize = account.avatarSize();
        float avatarX = account.avatarX();
        float avatarY = account.avatarY();
        if (user != null && user.avatarUrl != null && !user.avatarUrl.isBlank()) {
            drawCover(user.avatarUrl, "avatar:" + user.uid, avatarX, avatarY, avatarSize, avatarSize * 0.5F);
        } else {
            Skia.drawCircle(avatarX + avatarSize * 0.5F, avatarY + avatarSize * 0.5F, avatarSize * 0.5F, color(0x30FFFFFF));
            drawIcon("\uf0d3", avatarX + avatarSize * 0.5F, avatarY + avatarSize * 0.5F, 19.0F, 0xC8FFFFFF);
        }
        String nick = user != null && user.nickname != null && !user.nickname.isBlank() ? user.nickname : "Netease User";
        drawText(limit(nick, mediumFont(11.5F), account.textWidth()), account.textX(), account.nameY(), 11.5F, 0xE8FFFFFF, true);
        String meta = user != null && user.level > 0 ? "Level " + user.level : "Signed in";
        drawText(limit(meta, regularFont(9.5F), account.textWidth()), account.textX(), account.detailY(), 9.5F, 0x76FFFFFF, false);
        logoutX = account.actionX();
        logoutY = account.actionY();
        logoutR = account.actionRadius();
        drawRoundIconButton(logoutX, logoutY, logoutR, ICON_LOGOUT, mx, my, false);
    }

    private void renderLoginModal(Canvas canvas, float w, float h, float mx, float my) {
        loginCloseR = 0.0F;
        loginRefreshW = 0.0F;
        Skia.drawRect(0.0F, 0.0F, w, h, color(0xAA050608));

        float cardW = Math.min(360.0F, w - 40.0F);
        float cardH = Math.min(440.0F, h - 40.0F);
        float cardX = (w - cardW) * 0.5F;
        float cardY = (h - cardH) * 0.5F;
        Skia.drawShadow(cardX, cardY, cardW, cardH, 18.0F);
        Paint bg = Skia.scratchPaint();
        bg.setColor(color(0xFF14171D).getRGB());
        bg.setAntiAlias(true);
        canvas.drawRRect(RRect.makeXYWH(cardX, cardY, cardW, cardH, 18.0F), bg);

        drawCenteredText("Netease Login", cardX, cardY + 30.0F, cardW, 18.0F, 0xF4FFFFFF, true);
        drawCenteredText("Scan with Netease Music app", cardX, cardY + 57.0F, cardW, 11.5F, 0x86FFFFFF, false);

        float qrSize = Math.max(60, Math.min(224, Math.min(cardW - 48, cardH - 180)));
        float qrX = cardX + (cardW - qrSize) * 0.5F;
        float qrY = cardY + 72.0F;
        Skia.drawRoundedRect(qrX, qrY, qrSize, qrSize, 10.0F, color(0xFFFFFFFF));
        byte[] qr = loginQrBytes;
        if (qr != null) {
            drawCoverImageBytes(qr, "netease-qr:" + loginUnikey, qrX + 8.0F, qrY + 8.0F, qrSize - 16.0F, 4.0F);
        } else {
            drawCenteredText("Generating QR", qrX, qrY + qrSize * 0.5F - 8.0F, qrSize, 12.0F, 0xAA111318, true);
        }

        String statusText = switch (loginStatusCode) {
            case 800 -> "QR expired";
            case 801 -> "Waiting for scan";
            case 802 -> "Scanned, confirm in app";
            case 803 -> "Login complete";
            case -2 -> "QR login failed";
            default -> "Preparing QR";
        };
        int statusColor = loginStatusCode == 800 || loginStatusCode == -2 ? 0xFFFFC4A8 : 0xCFFFFFFF;
        drawCenteredText(statusText, cardX, qrY + qrSize + 28.0F, cardW, 13.0F, statusColor, true);

        if (loginStatusCode == 800 || loginStatusCode == -2) {
            String label = "Refresh QR";
            Font font = mediumFont(12.0F);
            float btnW = Math.max(112.0F, Skia.getTextBounds(label, font).getWidth() + 38.0F);
            float btnH = 32.0F;
            loginRefreshX = cardX + (cardW - btnW) * 0.5F;
            loginRefreshY = qrY + qrSize + 54.0F;
            loginRefreshW = btnW;
            loginRefreshH = btnH;
            boolean hover = inside(mx, my, loginRefreshX, loginRefreshY, loginRefreshW, loginRefreshH);
            Skia.drawRoundedRect(loginRefreshX, loginRefreshY, loginRefreshW, loginRefreshH, 16.0F,
                    color(hoverColor("login-refresh", hover, 0xB8D5E3FF, 0xD8D5E3FF)));
            drawTextCenteredInRect(label, loginRefreshX, loginRefreshY, loginRefreshW, loginRefreshH, 12.0F, 0xF0111318, true);
        }

        loginCloseR = 18.0F;
        loginCloseX = cardX + cardW - 24.0F;
        loginCloseY = cardY + 24.0F;
        drawRoundIconButton(loginCloseX, loginCloseY, loginCloseR, ICON_CLOSE, mx, my, false);
    }

    private void renderContent(Canvas canvas, float x, float y, float w, float h, float mx, float my) {
        songHits.clear();
        playlistHits.clear();
        hotSearchHits.clear();
        Page requested = page;
        Page displayed = contentTransition.update(requested, System.nanoTime());
        if (contentTransition.moving()) { mx = -10000; my = -10000; }
        try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, Rect.makeXYWH(x, y, w, h), contentTransition.opacity())) {
        page = displayed;
        switch (displayed) {
            case HOME -> renderHome(canvas, x, y, w, h, mx, my);
            case SEARCH -> renderSearch(canvas, x, y, w, h, mx, my);
            case LIKED -> renderSongListPage(canvas, x, y, w, h, "Liked Songs", likedSongs,
                    NeteaseClient.INSTANCE.isLoggedIn() ? "Liked tracks will appear here" : "Sign in to view liked songs", mx, my);
            case FAVORITES -> renderPlaylistGridPage(canvas, x, y, w, h, "Featured Playlists", playlists, mx, my);
            case RECENT -> renderSongListPage(canvas, x, y, w, h, "Recently Played", recentSongs,
                    NeteaseClient.INSTANCE.isLoggedIn() ? "No recent plays yet" : "Sign in to view recent plays", mx, my);
            case PLAYLIST_DETAIL -> renderPlaylistDetail(canvas, x, y, w, h, mx, my);
        }
        } finally { page = requested; }
    }

    private void renderHome(Canvas canvas, float x, float y, float w, float h, float mx, float my) {
        if (NeteaseClient.INSTANCE.isLoggedIn()) ensureUserProfile();
        drawPageTitle("Home", homeSubtitle(), x, y);
        float bodyTop = y + TOP_PAD + 70.0F;
        float bodyBottom = y + h;
        float cy = bodyTop - contentScroll;
        List<NeteaseSong> featured = dailySongs.isEmpty() ? songs : dailySongs;
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop)), ClipMode.INTERSECT, true);
        if (!featured.isEmpty()) {
            drawSectionTitle("Daily Picks", x + 28.0F, cy);
            cy += 26.0F;
            renderSongRows(canvas, featured, x + 28.0F, cy, w - 56.0F, h, 6, mx, my, bodyTop, bodyBottom);
            cy += Math.min(6, featured.size()) * ROW_H + 22.0F;
        }
        drawSectionTitle("Featured Playlists", x + 28.0F, cy);
        cy += 28.0F;
        renderPlaylistGrid(canvas, playlists, x + 28.0F, cy, w - 56.0F, 2, mx, my, bodyTop, bodyBottom);
        canvas.restore();
        if (loading && playlists.isEmpty()) drawCenteredHint("Loading Netease content", x, bodyTop, w, bodyBottom - bodyTop);
    }

    private String homeSubtitle() {
        if (!NeteaseClient.INSTANCE.isLoggedIn()) return "Netease Music";
        NeteaseUser user = userProfile;
        String name = user == null || user.nickname == null || user.nickname.isBlank()
                ? "Netease User"
                : user.nickname;
        return "Netease Music · " + name;
    }

    private void renderSearch(Canvas canvas, float x, float y, float w, float h, float mx, float my) {
        drawPageTitle("Search", "Find tracks, albums, and artists", x, y);
        float inputX = x + 28.0F;
        float inputY = y + TOP_PAD + 54.0F;
        float inputW = Math.min(460.0F, w - 56.0F);
        float inputH = 34.0F;
        boolean hover = inside(mx, my, inputX, inputY, inputW, inputH);
        float searchHoverAnim = ui.hover("search-input", hover);
        float searchFocusAnim = ui.value("search-focus", searchFocused ? 1 : 0,
                io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
        float lift = searchFocusAnim * 0.8F;
        int fillAlpha = Math.round(28.0F + searchHoverAnim * 12.0F + searchFocusAnim * 20.0F);
        if (searchFocused) Skia.drawShadow(inputX, inputY - lift, inputW, inputH, 11.0F);
        Skia.drawRoundedRect(inputX, inputY - lift, inputW, inputH, 11.0F, new Color(255, 255, 255, fillAlpha));
        drawIcon(ICON_SEARCH, inputX + 20.0F, inputY + inputH * 0.5F - lift, 16.0F, searchFocused ? 0xE8FFFFFF : 0xAAFFFFFF);

        searchX = inputX;
        searchY = inputY;
        searchW = inputW;
        searchH = inputH;
        renderSearchEditor(canvas);
        float actionX = inputX + inputW - 58.0F;
        boolean actionHover = inside(mx, my, actionX, inputY + 4, 54, inputH - 8);
        Skia.drawRoundedRect(actionX, inputY + 4, 54, inputH - 8, 8,
                color(hoverColor("search-action", actionHover, 0xFFDD365E, 0xFFFF5277)));
        drawTextCenteredInRect("Search", actionX, inputY + 4, 54, inputH - 8, 10.5F, 0xFFFFFFFF, true);
        if (!searchText.isEmpty()) drawIcon(ICON_CLOSE, actionX - 16, inputY + inputH / 2, 14, 0xB0FFFFFF);

        float metaY = inputY + 48.0F;
        String helper = status.equals("Search failed") ? "Search failed. Press Search to retry." : activeSearchQuery.isBlank()
                ? "Type a keyword and press Enter"
                : songs.size() + " results for \"" + activeSearchQuery + "\"";
        drawText(helper, inputX + 4.0F, metaY, 11.0F, 0x68FFFFFF, false);

        float resultTop = inputY + 72.0F;
        float resultBottom = y + h;
        float cy = resultTop - contentScroll;
        if (searchPageLoading.get() && songs.isEmpty()) {
            drawCenteredHint("Searching " + searchText, x, resultTop, w, Math.max(1.0F, resultBottom - resultTop));
        } else if (songs.isEmpty()) {
            canvas.save();
            canvas.clipRect(Rect.makeXYWH(x, resultTop, w, Math.max(1.0F, resultBottom - resultTop)), ClipMode.INTERSECT, true);
            renderHotSearches(canvas, x + 28.0F, cy, w - 56.0F, mx, my, resultTop, resultBottom);
            canvas.restore();
        } else {
            canvas.save();
            canvas.clipRect(Rect.makeXYWH(x, resultTop, w, Math.max(1.0F, resultBottom - resultTop)), ClipMode.INTERSECT, true);
            drawSectionTitle("Results", x + 28.0F, cy);
            cy += 26.0F;
            renderSongRows(canvas, songs, x + 28.0F, cy, w - 56.0F, h, songs.size(), mx, my, resultTop, resultBottom);
            renderSearchFooter(canvas, x + 28.0F, cy + songs.size() * ROW_H + 10.0F, w - 56.0F);
            canvas.restore();
        }
    }

    private void renderSearchFooter(Canvas canvas, float x, float y, float w) {
        if (!searchPageLoading.get() && !searchHasMore) return;
        String text = searchPageLoading.get() ? "Loading more results" : "Scroll down to load more";
        Skia.drawRoundedRect(x, y, w, 34.0F, 12.0F, color(0x10FFFFFF));
        drawText(text, x + 14.0F, y + 9.0F, 11.5F, 0x78FFFFFF, false);
    }

    private void renderHotSearches(Canvas canvas, float x, float y, float w, float mx, float my,
                                   float clipTop, float clipBottom) {
        drawSectionTitle("Trending Searches", x, y);
        float chipX = x;
        float chipY = y + 28.0F;
        float maxX = x + w;
        Font font = mediumFont(12.0F);
        List<String> words = hotSearches.isEmpty() ? fallbackHotSearches() : hotSearches;
        int count = Math.min(words.size(), 18);
        for (int i = 0; i < count; i++) {
            String word = words.get(i);
            float chipW = Math.min(180.0F, Skia.getTextBounds(word, font).getWidth() + 24.0F);
            if (chipX + chipW > maxX) {
                chipX = x;
                chipY += 34.0F;
            }
            if (chipY + 26.0F < clipTop || chipY > clipBottom) {
                chipX += chipW + 8.0F;
                continue;
            }
            float hitY = Math.max(chipY, clipTop);
            float hitH = Math.min(chipY + 26.0F, clipBottom) - hitY;
            if (hitH <= 0.0F) {
                chipX += chipW + 8.0F;
                continue;
            }
            boolean hover = inside(mx, my, chipX, hitY, chipW, hitH);
            Skia.drawRoundedRect(chipX, chipY, chipW, 26.0F, 13.0F, color(hoverColor("hot-search:" + word, hover, 0x16FFFFFF, 0x28FFFFFF)));
            drawText(limit(word, font, chipW - 20.0F), chipX + 12.0F, chipY + 7.0F, 12.0F,
                    hoverColor("hot-search:" + word, hover, 0xB8FFFFFF, 0xF2FFFFFF), true);
            hotSearchHits.add(new HitBox(chipX, hitY, chipW, hitH, word));
            chipX += chipW + 8.0F;
        }
        if (hotSearchLoading.get() && hotSearches.isEmpty()) {
            drawText("Loading trends", x, chipY + 42.0F, 11.0F, 0x66FFFFFF, false);
        }
    }

    private List<String> fallbackHotSearches() {
        return List.of("Taylor Swift", "周杰伦", "Ariana Grande", "陈奕迅", "林俊杰", "Billie Eilish");
    }

    private void renderSongListPage(Canvas canvas, float x, float y, float w, float h, String title,
                                    List<NeteaseSong> list, String empty, float mx, float my) {
        drawPageTitle(title, list.size() + " tracks", x, y);
        float bodyTop = y + TOP_PAD + 78.0F;
        float bodyBottom = y + h;
        float cy = bodyTop - contentScroll;
        if (list.isEmpty()) {
            drawCenteredHint(empty, x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop));
            return;
        }
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop)), ClipMode.INTERSECT, true);
        renderSongRows(canvas, list, x + 28.0F, cy, w - 56.0F, h, list.size(), mx, my, bodyTop, bodyBottom);
        canvas.restore();
    }

    private void renderPlaylistGridPage(Canvas canvas, float x, float y, float w, float h, String title,
                                        List<NeteasePlaylist> list, float mx, float my) {
        drawPageTitle(title, list.size() + " playlists", x, y);
        float bodyTop = y + TOP_PAD + 72.0F;
        float bodyBottom = y + h;
        if (list.isEmpty()) {
            drawCenteredHint(loading ? "Loading playlists" : "No playlists yet", x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop));
            return;
        }
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop)), ClipMode.INTERSECT, true);
        renderPlaylistGrid(canvas, list, x + 28.0F, bodyTop - contentScroll, w - 56.0F, 0, mx, my, bodyTop, bodyBottom);
        canvas.restore();
    }

    private void renderPlaylistDetail(Canvas canvas, float x, float y, float w, float h, float mx, float my) {
        String title = detailPlaylist == null ? "Playlist" : detailPlaylist.name;
        drawPageTitle(title, songs.size() + " tracks", x, y);
        float backX = x + 28.0F;
        float backY = y + TOP_PAD + 56.0F;
        boolean hover = inside(mx, my, backX, backY, 70.0F, 30.0F);
        Skia.drawRoundedRect(backX, backY, 70.0F, 30.0F, 15.0F, color(hoverColor("playlist-back", hover, 0x16FFFFFF, 0x26FFFFFF)));
        drawTextCenteredInRect("Back", backX, backY, 70.0F, 30.0F, 12.0F, 0xD8FFFFFF, true);
        float bodyTop = backY + 48.0F;
        float bodyBottom = y + h;
        if (loading && songs.isEmpty()) {
            drawCenteredHint("Loading playlist", x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop));
            return;
        }
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(x, bodyTop, w, Math.max(1.0F, bodyBottom - bodyTop)), ClipMode.INTERSECT, true);
        renderSongRows(canvas, songs, x + 28.0F, bodyTop - contentScroll, w - 56.0F, h, songs.size(), mx, my, bodyTop, bodyBottom);
        canvas.restore();
    }

    private void renderSongRows(Canvas canvas, List<NeteaseSong> list, float x, float y, float w, float viewportH,
                                int maxRows, float mx, float my) {
        renderSongRows(canvas, list, x, y, w, viewportH, maxRows, mx, my, 0.0F, appH - MINI_PLAYER_H);
    }

    private void renderSongRows(Canvas canvas, List<NeteaseSong> list, float x, float y, float w, float viewportH,
                                int maxRows, float mx, float my, float clipTop, float clipBottom) {
        int rows = Math.min(maxRows, list.size());
        int first = Math.max(0, (int) Math.floor((clipTop - y) / ROW_H) - 1);
        int last = Math.min(rows, (int) Math.ceil((clipBottom - y) / ROW_H) + 1);
        if (first >= last) return;
        Font metaFont = mediumFont(11.0F);
        requestSongArtworkBatch(list, first, Math.min(last + 2, rows));
        for (int i = first; i < last; i++) {
            NeteaseSong song = list.get(i);
            float rowY = y + i * ROW_H;
            float hitY = Math.max(rowY, clipTop);
            float hitH = Math.min(rowY + ROW_H - 4.0F, clipBottom) - hitY;
            if (hitH <= 0.0F) continue;
            boolean current = isCurrent(song);
            boolean hover = inside(mx, my, x, hitY, w, hitH);
            float rowEmphasis = ui.hover("song-row:" + song.id, hover);
            int rowColor = current ? 0x1CFFFFFF : io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x00000000, 0x10FFFFFF, rowEmphasis);
            if (current || rowEmphasis > 0.01F) {
                Skia.drawRoundedRect(x, rowY, w, ROW_H - 4.0F, 6.0F, color(rowColor));
            }
            float cover = 36.0F;
            drawCover(song.coverUrl, "song:" + song.id, x + 8.0F, rowY + 8, cover, 6.0F);
            drawText(limit(song.name, mediumFont(12.5F), w - 122), x + 58, rowY + 8, 12.5F, current ? 0xFFFF627D : 0xF0FFFFFF, true);
            drawText(limit(MusicText.safe(song.artist).isBlank() ? song.album : song.artist, metaFont, w - 116),
                    x + 58, rowY + 29, 10.5F, song.fee ? 0xFFFFB071 : 0x86FFFFFF, false);
            String duration = formatMs(song.durationMs);
            drawText(duration, x + w - 54.0F, rowY + 19, 10.5F, 0x82FFFFFF, false);
            songHits.add(new HitBox(x, hitY, w, hitH, song));
        }
    }

    private void renderPlaylistGrid(Canvas canvas, List<NeteasePlaylist> list, float x, float y, float w, int maxRows,
                                    float mx, float my) {
        renderPlaylistGrid(canvas, list, x, y, w, maxRows, mx, my, 0.0F, appH - MINI_PLAYER_H);
    }

    private void renderPlaylistGrid(Canvas canvas, List<NeteasePlaylist> list, float x, float y, float w, int maxRows,
                                    float mx, float my, float clipTop, float clipBottom) {
        float cardW = Math.max(116.0F, Math.min(156.0F, (w - 36.0F) / 4.0F));
        int cols = Math.max(2, (int) Math.floor((w + 18.0F) / (cardW + 18.0F)));
        cardW = (w - (cols - 1) * 18.0F) / cols;
        float cover = Math.min(cardW, 148.0F);
        int count = maxRows > 0 ? Math.min(list.size(), maxRows * cols) : list.size();
        float rowH = cover + 48.0F;
        int firstRow = Math.max(0, (int) Math.floor((clipTop - y) / rowH) - 1);
        int lastRow = Math.max(firstRow, (int) Math.ceil((clipBottom - y) / rowH) + 1);
        int first = Math.min(count, firstRow * cols);
        int last = Math.min(count, lastRow * cols);
        for (int i = first; i < last; i++) {
            int col = i % cols;
            int row = i / cols;
            float cx = x + col * (cardW + 18.0F);
            float cy = y + row * rowH;
            if (cy > clipBottom || cy + cover + 42.0F < clipTop) continue;
            NeteasePlaylist playlist = list.get(i);
            float hitY = Math.max(cy, clipTop);
            float hitH = Math.min(cy + cover + 42.0F, clipBottom) - hitY;
            if (hitH <= 0.0F) continue;
            boolean hover = inside(mx, my, cx, hitY, cardW, hitH);
            drawCover(playlist.coverUrl, "pl:" + playlist.id, cx, cy, cover, 7.0F);
            float cardEmphasis = ui.hover("playlist-card:" + playlist.id, hover);
            if (cardEmphasis > 0.01F) {
                Skia.drawCircle(cx + cover - 23, cy + cover - 23, 16,
                        color(io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x002C2C30, 0xB02C2C30, cardEmphasis)));
                try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, null, cardEmphasis)) {
                    MusicNowPlayingView.transportIcon(canvas, "play", cx + cover - 23, cy + cover - 23, 15, 0xFFFFFFFF, 0);
                }
            }
            drawText(limit(playlist.name, mediumFont(12.0F), cardW), cx, cy + cover + 8.0F, 12.0F, 0xE8FFFFFF, true);
            drawText(playlist.trackCount + " tracks", cx, cy + cover + 25.0F, 10.0F, 0x78FFFFFF, false);
            playlistHits.add(new HitBox(cx, hitY, cardW, hitH, playlist));
        }
    }

    private void renderMiniPlayer(Canvas canvas, float x, float y, float w, float h, float mx, float my) {
        likeBtnR = 0;
        Skia.drawRect(x, y, w, h, color(0xFF29292D));
        Skia.drawRect(x, y, w, 1, color(0x0EFFFFFF));
        MusicTrack track = MusicPlayerService.currentTrack();
        var cover = MusicPlayerService.coverImage();
        if (cover != null) drawImage(cover, x + 16, y + 12, 42, 8);
        else drawPlaceholderCover(x + 16, y + 12, 42, 8);
        boolean wide = w >= 720;
        playX = wide ? w * 0.53F : w - 112;
        prevX = playX - 44;
        nextX = playX + 44;
        playY = y + 33;
        float metaW = Math.max(48, prevX - 100);
        drawText(limit(track == null ? "Choose a song" : track.title, mediumFont(13), metaW), 70, y + 15, 13, 0xFFFFFFFF, true);
        drawText(limit(track == null ? "Your music, your moment" : track.artist, regularFont(11), metaW), 70, y + 35, 11, 0x99FFFFFF, false);
        drawRoundIconButton(prevX, playY, 18, ICON_PREV, mx, my, false);
        drawRoundIconButton(playX, playY, 22, MusicPlayerService.isPlaying() ? ICON_PAUSE : ICON_PLAY, mx, my, true);
        drawRoundIconButton(nextX, playY, 18, ICON_NEXT, mx, my, false);
        lyricsBtnX = w - 28;
        lyricsBtnY = playY;
        drawRoundIconButton(lyricsBtnX, lyricsBtnY, 18, ICON_LYRICS, mx, my, false);
        if (wide && track != null && NeteaseClient.INSTANCE.isLoggedIn()) {
            likeBtnR = 18;
            likeBtnX = w - 78;
            likeBtnY = playY;
            likeButton.draw(canvas, likeBtnX, likeBtnY, 18,
                    distance(mx, my, likeBtnX, likeBtnY) <= likeBtnR,
                    likedIds.contains(track.neteaseId), track.neteaseId);
        }
        miniProgressX = 52;
        miniProgressY = y + 82;
        miniProgressW = w - 218;
        miniProgressH = 4;
        drawProgress(miniProgressX, miniProgressY, miniProgressW, 4, displayedProgress(), draggingProgress
                || inside(mx, my, miniProgressX, miniProgressY - 8, miniProgressW, 20));
        drawTextRightCentered(formatMs(displayedPosition()), miniProgressX - 10, miniProgressY + 2, 10, 0xA0FFFFFF, true);
        drawTextLeftCentered(formatMs(track == null ? 0 : track.durationMs), miniProgressX + miniProgressW + 10, miniProgressY + 2, 10, 0xA0FFFFFF, true);
        volumeX = w - 84;
        volumeY = y + 78;
        volumeW = 60;
        volumeH = 12;
        drawVolumeSlider(volumeX, volumeY, volumeW, volumeH, MusicPlayerService.volume(), mx, my);
    }

    private void renderLyricsOverlay(Canvas canvas, float w, float h, float mx, float my) {
        if (nowPlaying == null) nowPlaying = new MusicNowPlayingView(MusicPlayerScreen::regularFont,
                MusicPlayerScreen::mediumFont, likeButton);
        MusicTrack track = MusicPlayerService.currentTrack();
        nowPlayingLayout = nowPlaying.render(canvas, w, h, mx, my, frameSeconds,
                new MusicNowPlayingView.State(track == null ? -1 : track.neteaseId,
                        track == null ? "" : MusicText.safe(track.title),
                        track == null ? "" : MusicText.safe(track.artist),
                        track == null ? "" : MusicText.safe(track.album), MusicPlayerService.coverImage(),
                        MusicPlayerService.lyricSnapshot(), displayedPosition(), track == null ? 0 : track.durationMs,
                        MusicPlayerService.isPlaying(), MusicPlayerService.isLoading(), MusicPlayerService.volume(),
                        track != null && NeteaseClient.INSTANCE.isLoggedIn(), track != null && likedIds.contains(track.neteaseId),
                        draggingProgress, draggingVolume));
        var layout = nowPlayingLayout;
        closeLyricsX = layout.close().cx(); closeLyricsY = layout.close().cy();
        prevX = layout.previous().cx(); nextX = layout.next().cx();
        playX = layout.play().cx(); playY = layout.play().cy();
        progressX = layout.progress().x(); progressY = layout.progress().y();
        progressW = layout.progress().w(); progressH = layout.progress().h();
        volumeX = layout.volume().x(); volumeY = layout.volume().y();
        volumeW = layout.volume().w(); volumeH = layout.volume().h();
        likeBtnX = layout.like().cx(); likeBtnY = layout.like().cy(); likeBtnR = layout.like().w() / 2;
        lyricX = layout.lyrics().x(); lyricY = layout.lyrics().y();
        lyricW = layout.lyrics().w(); lyricH = layout.lyrics().h();
        for (var hit : layout.hits()) {
            var box = hit.bounds();
            lyricHits.add(new HitBox(box.x(), box.y(), box.w(), box.h(), hit.line()));
        }
    }
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!acceptsInput()) return true;
        float mx = (float) click.x() - appX;
        float my = (float) click.y() - appY;
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        if (!inside(mx, my, 0.0F, 0.0F, appW, appH)) return super.mouseClicked(click, doubled);
        if (loginOpen) {
            if (loginCloseR > 0.0F && distance(mx, my, loginCloseX, loginCloseY) <= loginCloseR) {
                closeLoginModal();
                return true;
            }
            if (loginRefreshW > 0.0F && inside(mx, my, loginRefreshX, loginRefreshY, loginRefreshW, loginRefreshH)) {
                refreshLoginQr();
                return true;
            }
            return true;
        }
        if (overlayMotion.moving()) return true;
        if (lyricsOpen || lyricsSlide < 1) {
            float localY = my - MusicPageTransition.offset(lyricsSlide);
            if (distance(mx, localY, closeLyricsX, closeLyricsY) <= 18.0F) {
                setLyricsOpen(false);
                return true;
            }
            if (MusicPlayerService.currentTrack() == null) return true;
            if (inside(mx, localY, progressX, progressY - 10.0F, progressW, 26.0F)) {
                draggingProgress = true;
                progressDragResume = MusicPlayerService.isPlaying() || MusicPlayerService.isLoading();
                seekFromX(mx, progressX, progressW, progressDragResume);
                return true;
            }
            if (nowPlayingLayout != null && nowPlayingLayout.follow().w() > 0 && nowPlayingLayout.follow().contains(mx, localY)) {
                nowPlaying.follow();
                return true;
            }
            if (volumeW > 0 && inside(mx, localY, volumeX - 16, volumeY - 8, volumeW + 22, volumeH + 16)) {
                draggingVolume = true;
                setVolumeFromX(mx);
                return true;
            }
            if (likeBtnR > 0 && distance(mx, localY, likeBtnX, likeBtnY) <= likeBtnR) {
                toggleLike(MusicPlayerService.currentTrack().neteaseId);
                return true;
            }
            for (HitBox hit : lyricHits) {
                if (hit.contains(mx, localY) && hit.payload instanceof LyricLine line) {
                    MusicPlayerService.seek(line.startMs(), true);
                    nowPlaying.follow();
                    return true;
                }
            }
            if (handleTransportClick(mx, localY)) return true;
            return true;
        }
        if (inside(mx, my, appW - RESIZE_HANDLE, appH - RESIZE_HANDLE, RESIZE_HANDLE, RESIZE_HANDLE)) {
            resizingPanel = true;
            resizeStartMouseX = (float) click.x();
            resizeStartMouseY = (float) click.y();
            resizeStartW = appW;
            resizeStartH = appH;
            return true;
        }
        if (distance(mx, my, appW - 26, 26) <= 20) { close(); return true; }
        if (scrollbarH > 0 && inside(mx, my, appW - 14, scrollbarY, 14, scrollbarH)) {
            draggingScrollbar = true;
            scrollbarDragOffset = my - scrollbarY;
            return true;
        }
        if (my >= 0.0F && my <= 48.0F) {
            draggingPanel = true;
            dragOffsetX = (float) click.x() - appX;
            dragOffsetY = (float) click.y() - appY;
            return true;
        }
        if (inside(mx, my, miniProgressX, miniProgressY - 8.0F, miniProgressW, 20.0F)) {
            progressDragResume = MusicPlayerService.isPlaying() || MusicPlayerService.isLoading();
            draggingProgress = true;
            seekFromX(mx, miniProgressX, miniProgressW, progressDragResume);
            return true;
        }
        if (volumeW > 0.0F && inside(mx, my, volumeX - 18.0F, volumeY - 5.0F, volumeW + 24.0F, volumeH + 10.0F)) {
            draggingVolume = true;
            setVolumeFromX(mx);
            return true;
        }
        if (likeBtnR > 0.0F && distance(mx, my, likeBtnX, likeBtnY) <= likeBtnR + 4.0F) {
            MusicTrack track = MusicPlayerService.currentTrack();
            if (track != null) toggleLike(track.neteaseId);
            return true;
        }
        if (handleTransportClick(mx, my)) return true;
        if (distance(mx, my, lyricsBtnX, lyricsBtnY) <= 18.0F) {
            setLyricsOpen(true);
            return true;
        }
        if (MusicPlayerService.currentTrack() != null && inside(mx, my, 16, appH - MINI_PLAYER_H + 12, 42, 42)) {
            setLyricsOpen(true);
            return true;
        }
        if (loginW > 0.0F && inside(mx, my, loginX, loginY, loginW, loginH)) {
            openLogin();
            return true;
        }
        if (importLoginW > 0.0F && inside(mx, my, importLoginX, importLoginY, importLoginW, importLoginH)) {
            importLoginFromClipboard();
            return true;
        }
        if (logoutR > 0.0F && distance(mx, my, logoutX, logoutY) <= logoutR) {
            CompletableFuture.runAsync(() -> NeteaseClient.INSTANCE.logout());
            likedSongs.clear();
            recentSongs.clear();
            dailySongs.clear();
            userProfile = null;
            likedIds = Set.of();
            likedIdsLoaded = false;
            cachedUid = 0L;
            status = "Logged out";
            return true;
        }
        for (HitBox hit : navHits) {
            if (hit.contains(mx, my) && hit.payload instanceof Page nav) {
                page = nav;
                setContentScroll(0);
                searchFocused = nav == Page.SEARCH;
                if (nav == Page.LIKED || nav == Page.RECENT) loadUserPages();
                return true;
            }
        }
        if (contentTransition.moving()) return true;
        if (page == Page.SEARCH && inside(mx, my, searchX, searchY, searchW, searchH)) {
            searchFocused = true;
            if (mx >= searchX + searchW - 58) runSearch();
            else if (mx >= searchX + searchW - 88) { searchEditor.setText(""); searchText = ""; }
            else {
                if (doubled) searchEditor.selectAll();
                else searchEditor.moveTo(searchCursorAt(mx), false);
                selectingSearch = !doubled;
            }
            return true;
        }
        searchFocused = false;
        if (page == Page.PLAYLIST_DETAIL && inside(mx, my, sidebarWidth + 28.0F, TOP_PAD + 56.0F, 70.0F, 30.0F)) {
            page = detailReturnPage;
            songs.clear();
            setContentScroll(0);
            return true;
        }
        for (HitBox hit : songHits) {
            if (hit.contains(mx, my) && hit.payload instanceof NeteaseSong song) {
                playSong(song);
                return true;
            }
        }
        for (HitBox hit : playlistHits) {
            if (hit.contains(mx, my) && hit.payload instanceof NeteasePlaylist playlist) {
                openPlaylist(playlist);
                return true;
            }
        }
        for (HitBox hit : hotSearchHits) {
            if (hit.contains(mx, my) && hit.payload instanceof String word) {
                searchEditor.setText(word);
                searchText = searchEditor.text();
                searchFocused = false;
                runSearch();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!acceptsInput()) return true;
        if (draggingScrollbar) {
            float relative = (float) click.y() - appY - scrollbarDragOffset - scrollBodyTop();
            setContentScroll(clamp(relative / Math.max(1, scrollbarTravel), 0, 1) * maxContentScroll());
            if (page == Page.SEARCH) maybeLoadMoreSearch();
            return true;
        }
        if (selectingSearch) {
            searchEditor.moveTo(searchCursorAt((float) click.x() - appX), true);
            return true;
        }
        if (resizingPanel) {
            appW = resizeStartW + ((float) click.x() - resizeStartMouseX);
            appH = resizeStartH + ((float) click.y() - resizeStartMouseY);
            constrainPanel(width, height);
            contentScroll = clamp(contentScroll, 0.0F, maxContentScroll());
            savePanelGeometryState(false);
            return true;
        }
        if (draggingPanel) {
            appX = (float) click.x() - dragOffsetX;
            appY = (float) click.y() - dragOffsetY;
            constrainPanel(width, height);
            savePanelGeometryState(false);
            return true;
        }
        if (draggingVolume) {
            float mx = (float) click.x() - appX;
            setVolumeFromX(mx);
            return true;
        }
        if (draggingProgress) {
            float mx = (float) click.x() - appX;
            if (lyricsOpen || lyricsSlide < 1) seekFromX(mx, progressX, progressW, progressDragResume);
            else seekFromX(mx, miniProgressX, miniProgressW, progressDragResume);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!acceptsInput()) return true;
        if (draggingProgress && previewPositionMs >= 0) MusicPlayerService.seek(previewPositionMs, progressDragResume);
        previewPositionMs = -1;
        selectingSearch = false;
        draggingScrollbar = false;
        boolean panelChanged = draggingPanel || resizingPanel;
        draggingProgress = false;
        progressDragResume = false;
        draggingVolume = false;
        draggingPanel = false;
        resizingPanel = false;
        if (panelChanged) {
            savePanelGeometryState(true);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!acceptsInput()) return true;
        float mx = (float) mouseX - appX;
        float my = (float) mouseY - appY;
        if (loginOpen && inside(mx, my, 0.0F, 0.0F, appW, appH)) return true;
        if (overlayMotion.moving()) return true;
        if (lyricsOpen || lyricsSlide < 1) {
            float localY = my - MusicPageTransition.offset(lyricsSlide);
            if (inside(mx, localY, lyricX, lyricY, lyricW, lyricH)) {
                if (nowPlaying != null) nowPlaying.scroll(verticalAmount);
            }
            return true;
        }
        if (contentTransition.moving()) return true;
        if (inside(mx, my, sidebarWidth, 0.0F, appW - sidebarWidth, appH - MINI_PLAYER_H)) {
            if (my < scrollBodyTop()) return true;
            contentScrollTarget = clamp(contentScrollTarget - (float) verticalAmount * 52, 0, maxContentScroll());
            if (page == Page.SEARCH) maybeLoadMoreSearch();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!acceptsInput()) return true;
        int key = input.key();
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (loginOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeLoginModal();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            setLyricsOpen(false);
            page = Page.SEARCH;
            searchFocused = true;
            searchEditor.selectAll();
            return true;
        }
        if (searchFocused && !lyricsOpen) {
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> runSearch();
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> searchFocused = false;
                case GLFW.GLFW_KEY_LEFT -> searchEditor.move(-1, shift, ctrl);
                case GLFW.GLFW_KEY_RIGHT -> searchEditor.move(1, shift, ctrl);
                case GLFW.GLFW_KEY_HOME -> searchEditor.moveTo(0, shift);
                case GLFW.GLFW_KEY_END -> searchEditor.moveTo(searchText.length(), shift);
                case GLFW.GLFW_KEY_BACKSPACE -> searchEditor.delete(true, ctrl);
                case GLFW.GLFW_KEY_DELETE -> searchEditor.delete(false, ctrl);
                case GLFW.GLFW_KEY_A -> { if (ctrl) searchEditor.selectAll(); }
                case GLFW.GLFW_KEY_C -> { if (ctrl) MinecraftClient.getInstance().keyboard.setClipboard(searchEditor.selected()); }
                case GLFW.GLFW_KEY_X -> {
                    if (ctrl) {
                        MinecraftClient.getInstance().keyboard.setClipboard(searchEditor.selected());
                        searchEditor.insert("");
                    }
                }
                case GLFW.GLFW_KEY_V -> { if (ctrl) searchEditor.insert(MinecraftClient.getInstance().keyboard.getClipboard()); }
                default -> { }
            }
            searchText = searchEditor.text();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && lyricsOpen) { setLyricsOpen(false); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { MusicPlayerService.toggle(); return true; }
        if (key == GLFW.GLFW_KEY_L) { setLyricsOpen(!lyricsOpen); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            if (ctrl) MusicPlayerService.playAdjacent(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            else MusicPlayerService.seek(MusicPlayerService.positionMs() + (key == GLFW.GLFW_KEY_LEFT ? -5000 : 5000),
                    MusicPlayerService.isPlaying() || MusicPlayerService.isLoading());
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!acceptsInput()) return true;
        if (loginOpen || lyricsOpen || !searchFocused) return super.charTyped(input);
        if (input.isValidChar()) searchEditor.insert(input.asString());
        searchText = searchEditor.text();
        return true;
    }

    private void renderSearchEditor(Canvas canvas) {
        Font font = regularFont(13);
        float left = searchX + 40, available = Math.max(20, searchW - 132);
        float cursorX = textWidth(font, searchText.substring(0, searchEditor.cursor()));
        searchTextScroll = Math.max(0, Math.min(searchTextScroll, cursorX));
        if (cursorX - searchTextScroll > available - 3) searchTextScroll = cursorX - available + 3;
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(left, searchY, available, searchH));
        float tx = left - searchTextScroll;
        if (searchFocused && searchEditor.selectionStart() != searchEditor.selectionEnd()) {
            float a = textWidth(font, searchText.substring(0, searchEditor.selectionStart()));
            float b = textWidth(font, searchText.substring(0, searchEditor.selectionEnd()));
            Skia.drawRoundedRect(tx + a, searchY + 6, b - a, searchH - 12, 3, color(0x665D89FF));
        }
        if (searchText.isEmpty() && !searchFocused) drawText("Search music", left, searchY + 9, 13, 0x88FFFFFF, false);
        else drawText(searchText, tx, searchY + 9, 13, 0xF5FFFFFF, false);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0)
            Skia.drawRect(tx + cursorX, searchY + 7, 1.3F, searchH - 14, color(0xFFFF5277));
        canvas.restore();
    }

    private int searchCursorAt(float mouseX) {
        Font font = regularFont(13);
        float x = mouseX - searchX - 40 + searchTextScroll;
        for (int i = 0; i < searchText.length();) {
            int end = searchText.offsetByCodePoints(i, 1);
            float a = textWidth(font, searchText.substring(0, i));
            float b = textWidth(font, searchText.substring(0, end));
            if (x < (a + b) / 2) return i;
            i = end;
        }
        return searchText.length();
    }

    private long displayedPosition() {
        return previewPositionMs >= 0 ? previewPositionMs : MusicPlayerService.positionMs();
    }

    private float displayedProgress() {
        MusicTrack track = MusicPlayerService.currentTrack();
        return track == null || track.durationMs <= 0 ? 0 : clamp(displayedPosition() / (float) track.durationMs, 0, 1);
    }

    private void loadHome() {
        loading = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseClient.INSTANCE.personalizedPlaylists(12);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
            loading = false;
            if (error != null) {
                status = "Home load failed";
                OutputUtil.error("Netease home failed: " + error.getMessage());
                return;
            }
            playlists.clear();
            playlists.addAll(result);
            status = "Ready";
        }));
    }

    private void loadDaily() {
        if (!dailySongs.isEmpty() || !NeteaseClient.INSTANCE.isLoggedIn()) return;
        if (!dailyLoading.compareAndSet(false, true)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseClient.INSTANCE.recommendSongs();
            } catch (Exception e) {
                return List.<NeteaseSong>of();
            }
        }).thenAccept(result -> MinecraftClient.getInstance().execute(() -> {
            dailySongs.clear();
            dailySongs.addAll(result);
            dailyLoading.set(false);
        }));
    }

    private void ensureUserProfile() {
        if (userProfile != null || !NeteaseClient.INSTANCE.isLoggedIn()) return;
        if (!userProfileLoading.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                long uid = cachedUid;
                if (uid == 0L) uid = NeteaseClient.INSTANCE.loginUid();
                cachedUid = uid;
                NeteaseUser profile = NeteaseClient.INSTANCE.userDetail(uid);
                MinecraftClient.getInstance().execute(() -> userProfile = profile);
            } catch (Throwable e) {
                OutputUtil.error("Netease profile failed: " + e.getMessage());
            } finally {
                userProfileLoading.set(false);
            }
        });
    }

    private void ensureLikedIdsLoaded() {
        if (likedIdsLoaded || !NeteaseClient.INSTANCE.isLoggedIn()) return;
        if (!likedIdsLoading.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                long uid = cachedUid;
                if (uid == 0L) uid = NeteaseClient.INSTANCE.loginUid();
                cachedUid = uid;
                Set<Long> ids = NeteaseClient.INSTANCE.likedSongIds(uid);
                MinecraftClient.getInstance().execute(() -> {
                    likedIds = ids == null ? Set.of() : Set.copyOf(ids);
                    likedIdsLoaded = true;
                });
            } catch (Throwable e) {
                OutputUtil.error("Netease liked ids failed: " + e.getMessage());
                MinecraftClient.getInstance().execute(() -> likedIdsLoaded = true);
            } finally {
                likedIdsLoading.set(false);
            }
        });
    }

    private void toggleLike(long songId) {
        if (songId == 0L || !NeteaseClient.INSTANCE.isLoggedIn()) return;
        boolean wasLiked = likedIds.contains(songId);
        Set<Long> optimistic = new HashSet<>(likedIds);
        if (wasLiked) optimistic.remove(songId);
        else optimistic.add(songId);
        likedIds = Set.copyOf(optimistic);
        likeButton.clicked(songId, !wasLiked);
        CompletableFuture.runAsync(() -> {
            try {
                boolean ok = NeteaseClient.INSTANCE.like(songId, !wasLiked);
                if (!ok) MinecraftClient.getInstance().execute(() -> revertLike(songId, wasLiked));
            } catch (Throwable e) {
                MinecraftClient.getInstance().execute(() -> revertLike(songId, wasLiked));
                OutputUtil.error("Netease like failed: " + e.getMessage());
            }
        });
    }

    private void revertLike(long songId, boolean shouldBeLiked) {
        Set<Long> reverted = new HashSet<>(likedIds);
        if (shouldBeLiked) reverted.add(songId);
        else reverted.remove(songId);
        likedIds = Set.copyOf(reverted);
    }

    private void loadUserPages() {
        if (!NeteaseClient.INSTANCE.isLoggedIn()) return;
        if (!userPagesLoading.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                long uid = NeteaseClient.INSTANCE.loginUid();
                List<NeteasePlaylist> userPlaylists = NeteaseClient.INSTANCE.userPlaylists(uid, 80);
                List<NeteaseSong> liked = userPlaylists.isEmpty()
                        ? List.of() : NeteaseClient.INSTANCE.playlistTracks(userPlaylists.get(0).id, LIKED_INITIAL_LIMIT);
                List<NeteaseSong> recent = NeteaseClient.INSTANCE.userRecord(uid, 1);
                MinecraftClient.getInstance().execute(() -> {
                    likedSongs.clear();
                    likedSongs.addAll(liked);
                    recentSongs.clear();
                    recentSongs.addAll(recent);
                });
            } catch (Exception ignored) {
            } finally {
                userPagesLoading.set(false);
            }
        });
    }

    private void loadHotSearches() {
        if (!hotSearchLoading.compareAndSet(false, true)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseClient.INSTANCE.hotSearchKeywords();
            } catch (Exception e) {
                return List.<String>of();
            }
        }).thenAccept(result -> MinecraftClient.getInstance().execute(() -> {
            hotSearches.clear();
            hotSearches.addAll(result);
            hotSearchLoading.set(false);
        }));
    }

    private void runSearch() {
        startSearch(searchText.strip());
    }

    private void startSearch(String query) {
        if (query.isEmpty()) return;
        if (searchPageLoading.get()) {
            pendingSearchQuery = query;
            status = "Waiting to search " + query;
            return;
        }
        activeSearchQuery = query;
        searchOffset = 0;
        searchHasMore = true;
        songs.clear();
        setContentScroll(0);
        loadSearchPage(query, true);
    }

    private void maybeLoadMoreSearch() {
        if (page != Page.SEARCH || !searchHasMore || activeSearchQuery.isBlank()) return;
        float viewport = Math.max(1.0F, appH - MINI_PLAYER_H - scrollBodyTop());
        float content = songs.size() * ROW_H;
        if (Math.max(contentScroll, contentScrollTarget) + viewport >= content - ROW_H * 4.0F) {
            loadSearchPage(activeSearchQuery, false);
        }
    }

    private void loadSearchPage(String query, boolean firstPage) {
        if (!searchPageLoading.compareAndSet(false, true)) return;
        loading = firstPage;
        page = Page.SEARCH;
        status = firstPage ? "Searching " + query : "Loading more";
        int offset = firstPage ? 0 : searchOffset;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseClient.INSTANCE.searchSongs(query, SEARCH_PAGE_SIZE, offset);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
            loading = false;
            searchPageLoading.set(false);
            if (removed || page != Page.SEARCH) { pendingSearchQuery = null; return; }
            if (pendingSearchQuery != null) {
                String next = pendingSearchQuery;
                pendingSearchQuery = null;
                startSearch(next);
                return;
            }
            if (!query.equals(activeSearchQuery)) return;
            if (error != null) {
                status = "Search failed";
                OutputUtil.error("Netease search failed: " + error.getMessage());
                return;
            }
            if (firstPage) songs.clear();
            songs.addAll(result);
            searchOffset = songs.size();
            searchHasMore = result.size() >= SEARCH_PAGE_SIZE;
            status = songs.size() + " songs";
            requestSongArtworkBatch(result, 0, result.size());
        }));
    }

    private void openPlaylist(NeteasePlaylist playlist) {
        detailPlaylist = playlist;
        detailReturnPage = page == Page.PLAYLIST_DETAIL ? detailReturnPage : page;
        page = Page.PLAYLIST_DETAIL;
        songs.clear();
        setContentScroll(0);
        loading = true;
        if (playlistRequestRunning) { pendingPlaylist = playlist; return; }
        playlistRequestRunning = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NeteaseClient.INSTANCE.playlistTracks(playlist.id, 120);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
            playlistRequestRunning = false;
            if (removed || page != Page.PLAYLIST_DETAIL) { pendingPlaylist = null; return; }
            if (pendingPlaylist != null) {
                NeteasePlaylist next = pendingPlaylist;
                pendingPlaylist = null;
                openPlaylist(next);
                return;
            }
            if (detailPlaylist != playlist) return;
            loading = false;
            if (error != null) {
                status = "Playlist failed";
                OutputUtil.error("Netease playlist failed: " + error.getMessage());
                return;
            }
            songs.addAll(result);
            status = playlist.name;
        }));
    }

    private void playSong(NeteaseSong song) {
        ensureSongArtwork(song);
        activeQueue.clear();
        activeQueue.addAll(currentVisibleSongs());
        MusicPlayerService.playQueue(activeQueue, song);
        status = "Playing " + song.name;
    }

    private void ensureSongArtwork(NeteaseSong song) {
        if (song == null) return;
        requestSongArtworkBatch(List.of(song), 0, 1);
    }

    private void requestSongArtworkBatch(List<NeteaseSong> list, int first, int last) {
        if (removed || artworkDetailsLoading.get() || list == null || list.isEmpty()) return;
        first = Math.max(0, first);
        last = Math.min(list.size(), last);
        if (first >= last) return;

        long now = System.currentTimeMillis();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        int limit = Math.min(DETAIL_BATCH_SIZE, MAX_FRAME_ARTWORK_REQUESTS);
        for (int i = first; i < last && ids.size() < limit; i++) {
            NeteaseSong song = list.get(i);
            if (song == null || song.id == 0L || (song.coverUrl != null && !song.coverUrl.isBlank())) continue;
            Long retryAfter = songDetailRetryAfter.get(song.id);
            if (retryAfter != null && retryAfter > now) continue;
            if (fetchingSongDetails.add(song.id)) ids.add(song.id);
        }
        if (ids.isEmpty()) return;
        if (!DETAIL_SLOTS.tryAcquire()) {
            fetchingSongDetails.removeAll(ids);
            return;
        }
        artworkDetailsLoading.set(true);

        DETAIL_EXECUTOR.execute(() -> {
            Map<Long, String> covers = new HashMap<>();
            try {
                if (!removed) {
                    for (NeteaseSong detail : NeteaseClient.INSTANCE.songDetails(ids)) {
                        if (detail != null && detail.coverUrl != null && !detail.coverUrl.isBlank())
                            covers.put(detail.id, detail.coverUrl);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        if (removed) return;
                        for (NeteaseSong song : list) {
                            String cover = song == null ? null : covers.get(song.id);
                            if (cover != null) song.coverUrl = cover;
                        }
                        long retryAt = System.currentTimeMillis() + IMAGE_RETRY_COOLDOWN_MS;
                        for (Long id : ids) {
                            if (covers.containsKey(id)) songDetailRetryAfter.remove(id);
                            else songDetailRetryAfter.put(id, retryAt);
                        }
                        while (songDetailRetryAfter.size() > 256)
                            songDetailRetryAfter.remove(songDetailRetryAfter.keySet().iterator().next());
                    } finally {
                        fetchingSongDetails.removeAll(ids);
                        artworkDetailsLoading.set(false);
                        DETAIL_SLOTS.release();
                    }
                });
            }
        });
    }

    private List<NeteaseSong> currentVisibleSongs() {
        return switch (page) {
            case LIKED -> new ArrayList<>(likedSongs);
            case RECENT -> new ArrayList<>(recentSongs);
            case HOME -> dailySongs.isEmpty() ? new ArrayList<>(songs) : new ArrayList<>(dailySongs);
            default -> new ArrayList<>(songs);
        };
    }

    private boolean handleTransportClick(float mx, float my) {
        if ((lyricsOpen || lyricsSlide < 1) && nowPlayingLayout != null) {
            if (nowPlayingLayout.play().contains(mx, my)) { MusicPlayerService.toggle(); return true; }
            if (nowPlayingLayout.previous().contains(mx, my)) { playAdjacent(-1); return true; }
            if (nowPlayingLayout.next().contains(mx, my)) { playAdjacent(1); return true; }
            return false;
        }
        if (distance(mx, my, playX, playY) <= 24.0F) {
            MusicPlayerService.toggle();
            return true;
        }
        if (distance(mx, my, prevX, playY) <= 18.0F) {
            playAdjacent(-1);
            return true;
        }
        if (distance(mx, my, nextX, playY) <= 18.0F) {
            playAdjacent(1);
            return true;
        }
        return false;
    }

    private void playAdjacent(int delta) {
        MusicPlayerService.playAdjacent(delta);
    }

    private void seekFromX(float mx, float x, float w) {
        seekFromX(mx, x, w, MusicPlayerService.isPlaying());
    }

    private void seekFromX(float mx, float x, float w, boolean resumeAfterSeek) {
        MusicTrack track = MusicPlayerService.currentTrack();
        if (track == null || track.durationMs <= 0L || w <= 0.0F) return;
        float frac = Math.max(0.0F, Math.min(1.0F, (mx - x) / w));
        long position = (long) (track.durationMs * frac);
        if (draggingProgress) previewPositionMs = position;
        else MusicPlayerService.seek(position, resumeAfterSeek);
    }

    private void setVolumeFromX(float mx) {
        if (volumeW <= 0.0F) return;
        MusicPlayerService.setVolume(clamp((mx - volumeX) / volumeW, 0.0F, 1.0F));
    }

    private float maxContentScroll() {
        float viewport = Math.max(1.0F, appH - MINI_PLAYER_H - scrollBodyTop());
        int homeRows = Math.min(6, dailySongs.isEmpty() ? songs.size() : dailySongs.size());
        float content = switch (page) {
            case HOME -> (homeRows == 0 ? 0 : homeRows * ROW_H + 48) + playlistGridHeight(playlists, 2) + 44;
            case SEARCH -> songs.isEmpty() ? hotSearchHeight() : songs.size() * ROW_H + 76;
            case LIKED -> likedSongs.size() * ROW_H + 16;
            case RECENT -> recentSongs.size() * ROW_H + 16;
            case FAVORITES -> playlistGridHeight(playlists, 0) + 16;
            case PLAYLIST_DETAIL -> songs.size() * ROW_H + 16;
        };
        return Math.max(0.0F, content - viewport);
    }

    private float hotSearchHeight() {
        float width = appW - sidebarWidth - 56, x = 0, height = 54;
        Font font = mediumFont(12);
        List<String> words = hotSearches.isEmpty() ? fallbackHotSearches() : hotSearches;
        for (int i = 0; i < Math.min(18, words.size()); i++) {
            float chip = Math.min(180, textWidth(font, words.get(i)) + 24);
            if (x + chip > width) { x = 0; height += 34; }
            x += chip + 8;
        }
        return height + 16;
    }

    private void setContentScroll(float value) {
        contentScroll = contentScrollTarget = value;
        contentMotion.snap(value);
    }

    private void setLyricsOpen(boolean open) {
        lyricsOpen = open;
        lyricsSlide = overlayMotion.step(open, 0);
        draggingProgress = draggingVolume = selectingSearch = draggingScrollbar = draggingPanel = resizingPanel = false;
        previewPositionMs = -1;
        if (open && nowPlaying != null) nowPlaying.follow();
    }

    private float scrollBodyTop() {
        return switch (page) {
            case SEARCH -> TOP_PAD + 126.0F;
            case PLAYLIST_DETAIL -> TOP_PAD + 104.0F;
            case HOME -> TOP_PAD + 70.0F;
            case FAVORITES -> TOP_PAD + 72.0F;
            default -> TOP_PAD + 78.0F;
        };
    }

    private float playlistGridHeight(List<NeteasePlaylist> list, int maxRows) {
        if (list == null || list.isEmpty()) return 0.0F;
        float contentW = Math.max(1.0F, appW - sidebarWidth - 56.0F);
        float cardW = Math.max(116.0F, Math.min(156.0F, (contentW - 36.0F) / 4.0F));
        int cols = Math.max(2, (int) Math.floor((contentW + 18.0F) / (cardW + 18.0F)));
        cardW = (contentW - (cols - 1) * 18.0F) / cols;
        float cover = Math.min(cardW, 148.0F);
        int count = maxRows > 0 ? Math.min(list.size(), maxRows * cols) : list.size();
        int rows = (int) Math.ceil(count / (float) cols);
        return rows * (cover + 48.0F);
    }

    private void openLogin() {
        loginOpen = true;
        refreshLoginQr();
    }

    private void importLoginFromClipboard() {
        openLogin();
    }

    private void closeLoginModal() {
        loginOpen = false;
    }

    private void refreshLoginQr() {
        loginOpen = true;
        loginUnikey = null;
        loginQrBytes = null;
        loginStatusCode = -1;
        loginExpireAtMs = 0L;
        status = "Preparing QR login";
        startLoginPolling();
    }

    private void startLoginPolling() {
        if (!loginPolling.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                while (loginOpen) {
                    if (loginUnikey == null || System.currentTimeMillis() > loginExpireAtMs) {
                        try {
                            String unikey = NeteaseClient.INSTANCE.qrLoginKey();
                            byte[] image = NeteaseClient.INSTANCE.qrLoginImageBytes(unikey);
                            loginUnikey = unikey;
                            loginQrBytes = image;
                            loginExpireAtMs = System.currentTimeMillis() + 4L * 60_000L;
                            loginStatusCode = 801;
                        } catch (Throwable e) {
                            loginStatusCode = -2;
                            status = "QR login failed";
                            OutputUtil.error("Failed to create Netease QR login: " + e.getMessage());
                            sleepLoginPoll(3000L);
                            continue;
                        }
                    }

                    try {
                        int code = NeteaseClient.INSTANCE.qrLoginCheck(loginUnikey);
                        loginStatusCode = code;
                        if (code == 803) {
                            long uid = NeteaseClient.INSTANCE.loginUid();
                            MinecraftClient.getInstance().execute(() -> {
                                loginOpen = false;
                                status = "Netease connected";
                                OutputUtil.info("Netease QR login completed for uid " + uid + ".");
                                cachedUid = uid;
                                userProfile = null;
                                likedIds = Set.of();
                                likedIdsLoaded = false;
                                ensureUserProfile();
                                ensureLikedIdsLoaded();
                                loadDaily();
                                loadUserPages();
                            });
                            break;
                        }
                        if (code == 800) {
                            loginUnikey = null;
                            loginQrBytes = null;
                            loginExpireAtMs = 0L;
                            continue;
                        }
                    } catch (Throwable e) {
                        loginStatusCode = -2;
                        OutputUtil.error("Netease QR login poll failed: " + e.getMessage());
                    }
                    sleepLoginPoll(2500L);
                }
            } finally {
                loginPolling.set(false);
            }
        });
    }

    private void sleepLoginPoll(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void drawPageTitle(String title, String subtitle, float x, float y) {
        float max = Math.max(80.0F, appW - sidebarWidth - 100.0F);
        drawText(limit(title, mediumFont(28.0F), max), x + 28.0F, y + TOP_PAD - 2, 28.0F, 0xF4FFFFFF, true);
        drawText(limit(subtitle, regularFont(11.0F), max), x + 30.0F, y + TOP_PAD + 35.0F, 11.0F, 0x72FFFFFF, false);
    }

    private void drawSectionTitle(String title, float x, float y) {
        drawText(title, x, y, 16.0F, 0xEAFFFFFF, true);
    }

    private void drawCenteredHint(String text, float x, float y, float w, float h) {
        Font font = mediumFont(15.0F);
        Rect bounds = Skia.getTextBounds(text, font);
        drawText(text, x + (w - bounds.getWidth()) * 0.5F, y + (h - bounds.getHeight()) * 0.5F, 15.0F, 0x8AFFFFFF, true);
    }

    private void drawCenteredText(String text, float x, float y, float w, float size, int argb, boolean medium) {
        Font font = medium ? mediumFont(size) : regularFont(size);
        Rect bounds = Skia.getTextBounds(text, font);
        drawText(text, x + (w - bounds.getWidth()) * 0.5F, y, size, argb, medium);
    }

    private void drawTextCenteredInRect(String text, float x, float y, float w, float h, float size, int argb, boolean medium) {
        text = MusicText.safe(text);
        if (text == null || text.isEmpty()) return;
        Font font = medium ? mediumFont(size) : regularFont(size);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color(argb).getRGB());
        paint.setAntiAlias(true);
        float textW = font.measureTextWidth(text);
        float metricMid = (font.getMetrics().getAscent() + font.getMetrics().getDescent()) * 0.5F;
        Skia.getCanvas().drawString(text, x + (w - textW) * 0.5F, y + h * 0.5F - metricMid, font, paint);
    }

    private void drawTextLeftInRect(String text, float x, float y, float w, float h, float size, int argb, boolean medium) {
        if (text == null || text.isEmpty()) return;
        Font font = medium ? mediumFont(size) : regularFont(size);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color(argb).getRGB());
        paint.setAntiAlias(true);
        float metricMid = (font.getMetrics().getAscent() + font.getMetrics().getDescent()) * 0.5F;
        Skia.getCanvas().drawString(limit(text, font, w), x, y + h * 0.5F - metricMid, font, paint);
    }

    private void drawTextLeftCentered(String text, float x, float cy, float size, int argb, boolean medium) {
        drawTextAlignedCentered(text, x, cy, size, argb, medium, false);
    }

    private void drawTextRightCentered(String text, float right, float cy, float size, int argb, boolean medium) {
        drawTextAlignedCentered(text, right, cy, size, argb, medium, true);
    }

    private void drawTextAlignedCentered(String text, float x, float cy, float size, int argb, boolean medium, boolean rightAligned) {
        text = MusicText.safe(text);
        if (text == null || text.isEmpty()) return;
        Font font = medium ? mediumFont(size) : regularFont(size);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color(argb).getRGB());
        paint.setAntiAlias(true);
        float textX = rightAligned ? x - font.measureTextWidth(text) : x;
        float metricMid = (font.getMetrics().getAscent() + font.getMetrics().getDescent()) * 0.5F;
        Skia.getCanvas().drawString(text, textX, cy - metricMid, font, paint);
    }

    private void drawProgress(float x, float y, float w, float h, float progress, boolean active) {
        float emphasis = ui.hover("progress", active);
        float trackH = h + 2 * emphasis;
        float trackY = y + (h - trackH) * 0.5F;
        Skia.drawRoundedRect(x, trackY, w, trackH, trackH * 0.5F, color(io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x2CFFFFFF, 0x4AFFFFFF, emphasis)));
        float fill = w * Math.max(0.0F, Math.min(1.0F, progress));
        if (fill > 0.0F) Skia.drawRoundedRect(x, trackY, fill, trackH, trackH * 0.5F, color(0xECFFFFFF));
        if (emphasis > 0.01F) Skia.drawCircle(x + fill, y + h * 0.5F, 4.5F * emphasis, color(0xFFFFFFFF));
    }

    private void drawVolumeSlider(float x, float y, float w, float h, float value, float mx, float my) {
        boolean hover = inside(mx, my, x - 18.0F, y - 5.0F, w + 24.0F, h + 10.0F) || draggingVolume;
        float emphasis = ui.hover("volume", hover);
        drawIcon(ICON_VOLUME, x - 12.0F, y + h * 0.5F, 12.0F, io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x8EFFFFFF, 0xD8FFFFFF, emphasis));
        float trackY = y + h * 0.5F - 1.5F;
        Skia.drawRoundedRect(x, trackY, w, 3.0F, 1.5F, color(io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x24FFFFFF, 0x3AFFFFFF, emphasis)));
        float fill = w * Math.max(0.0F, Math.min(1.0F, value));
        if (fill > 0.0F) Skia.drawRoundedRect(x, trackY, fill, 3.0F, 1.5F, color(0xD8FFFFFF));
        if (emphasis > 0.01F) Skia.drawCircle(x + fill, y + h * 0.5F, 4 * emphasis, color(0xFFFFFFFF));
    }

    private void drawCover(String url, String key, float x, float y, float size, float radius) {
        io.github.humbleui.skija.Image image = imageFor(url, key);
        if (image != null) drawImage(image, x, y, size, radius);
        else drawPlaceholderCover(x, y, size, radius);
    }

    private void drawCoverImageBytes(byte[] bytes, String key, float x, float y, float size, float radius) {
        io.github.humbleui.skija.Image image = bytes == null ? null : MusicArtworkCache.load(key, bytes);
        if (image != null) drawImage(image, x, y, size, radius);
    }

    private io.github.humbleui.skija.Image imageFor(String url, String key) {
        return MusicArtworkCache.get(url);
    }

    private void drawImage(io.github.humbleui.skija.Image image, float x, float y, float size, float radius) {
        if (image == null) return;
        Skia.save();
        Skia.clip(x, y, size, size, radius);
        Skia.getCanvas().drawImageRect(image, Rect.makeXYWH(x, y, size, size));
        Skia.restore();
    }

    private void drawPlaceholderCover(float x, float y, float size, float radius) {
        Skia.drawRoundedRect(x, y, size, size, radius, color(0xFF282A32));
        drawIcon(ICON_ALBUM, x + size * 0.5F, y + size * 0.5F, Math.max(16.0F, size * 0.34F), 0x62FFFFFF);
    }

    private int hoverColor(String key, boolean hover, int idle, int active) {
        return io.github.wzhy233.ardion.gui.ui.UiManager.mix(idle, active, ui.hover(key, hover));
    }

    private void drawRoundIconButton(float cx, float cy, float r, String icon, float mx, float my, boolean filled) {
        boolean hover = distance(mx, my, cx, cy) <= r + 3.0F;
        float amount = ui.hover("icon:" + cx + ":" + cy, hover);
        if (amount > 0.01F) Skia.drawCircle(cx, cy, r, color((Math.round(amount * 25) << 24) | 0xFFFFFF));
        String transport = switch (icon) {
            case ICON_PLAY -> MusicPlayerService.isLoading() ? "loading" : "play";
            case ICON_PAUSE -> "pause";
            case ICON_PREV -> "previous";
            case ICON_NEXT -> "next";
            default -> null;
        };
        if (transport != null) {
            MusicNowPlayingView.transportIcon(Skia.getCanvas(), transport, cx, cy,
                    (filled ? 25 : 21) * (1 + amount * 0.06F), 0xF2FFFFFF, (animationNanos % 12_000_000_000L) / 1_000_000_000.0F);
        } else {
            drawIcon(icon, cx, cy, r * 1.12F * (1 + amount * 0.04F), io.github.wzhy233.ardion.gui.ui.UiManager.mix(0xA8FFFFFF, 0xF0FFFFFF, amount));
        }
    }

    private void drawIcon(String value, float cx, float cy, float size, int argb) {
        Font font = Fonts.getIconFill(size * 2);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color(argb).getRGB());
        paint.setAntiAlias(true);
        float textWidth = font.measureTextWidth(value);
        float metricMid = (font.getMetrics().getAscent() + font.getMetrics().getDescent()) * 0.5F;
        Skia.getCanvas().drawString(value, cx - textWidth * 0.5F, cy - metricMid, font, paint);
    }


    private static Font regularFont(float size) { return Fonts.getRegular(size * 2); }
    private static Font mediumFont(float size) { return Fonts.getMedium(size * 2); }
    private void drawText(String value, float x, float y, float size, int argb, boolean medium) {
        value = MusicText.safe(value);
        if (value == null || value.isEmpty()) return;
        Font font = medium ? mediumFont(size) : regularFont(size);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color(argb).getRGB());
        paint.setAntiAlias(true);
        Skia.getCanvas().drawString(value, x, y - font.getMetrics().getAscent(), font, paint);
    }

    private String limit(String text, Font font, float width) {
        if (text == null) return "";
        return MusicText.limit(text, Math.max(0.0F, width), font::measureTextWidth);
    }

    private static float textWidth(Font font, String text) {
        return MusicText.width(text, font::measureTextWidth);
    }

    private boolean isCurrent(NeteaseSong song) {
        MusicTrack track = MusicPlayerService.currentTrack();
        return track != null && song != null && track.neteaseId == song.id;
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Color color(int argb) {
        return new Color(argb, true);
    }

    private static String formatMs(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        return total / 60L + ":" + String.format("%02d", total % 60L);
    }

    private static final class SavedState {
        private Page page = Page.HOME;
        private Page detailReturnPage = Page.HOME;
        private NeteasePlaylist detailPlaylist;
        private String status = "Ready";
        private String searchText = "";
        private String activeSearchQuery = "";
        private int searchOffset;
        private boolean searchHasMore;
        private float contentScroll;
        private boolean lyricsOpen;
        private float panelX;
        private float panelY;
        private float panelW;
        private float panelH;
        private boolean panelConfigLoaded;
        private final List<NeteasePlaylist> playlists = new ArrayList<>();
        private final List<NeteaseSong> songs = new ArrayList<>();
        private final List<NeteaseSong> dailySongs = new ArrayList<>();
        private final List<NeteaseSong> likedSongs = new ArrayList<>();
        private final List<NeteaseSong> recentSongs = new ArrayList<>();
        private final List<NeteaseSong> activeQueue = new ArrayList<>();
        private final List<String> hotSearches = new ArrayList<>();
    }

    private record HitBox(float x, float y, float w, float h, Object payload) {
        boolean contains(float mx, float my) {
            return inside(mx, my, x, y, w, h);
        }
    }
}
