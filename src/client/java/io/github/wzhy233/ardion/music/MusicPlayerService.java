package io.github.wzhy233.ardion.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.humbleui.skija.Image;
import io.github.wzhy233.ardion.Ardion;
import io.github.wzhy233.ardion.music.lyric.LyricLine;
import io.github.wzhy233.ardion.music.lyric.LyricParser;
import io.github.wzhy233.ardion.music.netease.NeteaseClient;
import io.github.wzhy233.ardion.music.netease.NeteaseLyric;
import io.github.wzhy233.ardion.music.netease.NeteaseSong;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public final class MusicPlayerService {
    private static final int MAX_AUDIO_BYTES = 32 * 1024 * 1024;
    private static final ThreadPoolExecutor PLAYBACK = latestExecutor("Ardion-Music-Playback");
    private static final ThreadPoolExecutor LYRICS = latestExecutor("Ardion-Music-Lyrics");
    private static final ExecutorService CLOSER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Ardion-Music-Close");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledThreadPoolExecutor CONFIG_WRITER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "Ardion-Music-Config");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> configWrite;

    static {
        CONFIG_WRITER.setRemoveOnCancelPolicy(true);
    }

    private static ThreadPoolExecutor latestExecutor(String name) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), r -> {
                    Thread thread = new Thread(r, name);
                    thread.setDaemon(true);
                    return thread;
                }, (task, executor) -> {
                    Runnable superseded = executor.getQueue().poll();
                    if (superseded instanceof Future<?> future) future.cancel(false);
                    if (!executor.isShutdown()) executor.execute(task);
                });
    }

    private static volatile MusicTrack currentTrack;
    private static volatile MusicLyrics lyricSnapshot = MusicLyrics.empty(MusicLyrics.State.IDLE);
    private static volatile long lyricGeneration;
    private static volatile boolean playing;
    private static volatile boolean loading;
    private static volatile String status = "Idle";
    private static volatile String streamUrl;
    private static volatile long accumulatedMs;
    private static volatile long playbackBaseMs;
    private static volatile boolean playbackClockRunning;
    private static volatile long lastActivityAtMs;
    private static volatile AdvancedPlayer player;
    private static volatile JavaSoundAudioDevice audioDevice;
    private static volatile Future<?> playbackFuture;
    private static volatile List<MusicTrack> playQueue = List.of();
    private static volatile int playQueueIndex = -1;
    private static final Object PLAYBACK_LOCK = new Object();
    private static final Path CONFIG_DIR = io.github.wzhy233.ardion.util.client.ArdionHome.musicPlayer();
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("music-player.json");
    private static final Path PREVIOUS_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ardion/music-player/music-player.json");
    private static final Path LEGACY_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("ardion/music-player.json");
    private static volatile long playbackGeneration;
    private static volatile float volume = 0.72F;

    private MusicPlayerService() {
    }

    public static void init() {
        loadConfig();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stop();
            PLAYBACK.shutdownNow();
            LYRICS.shutdownNow();
            CLOSER.shutdown();
            CONFIG_WRITER.shutdown();
            MusicArtworkCache.shutdown();
        });
    }

    public static float volume() {
        return volume;
    }

    public static void setVolume(float value) {
        volume = Math.max(0.0F, Math.min(1.0F, value));
        JavaSoundAudioDevice device = audioDevice;
        if (device instanceof ControlledAudioDevice controlled) {
            controlled.setVolume(volume);
        }
        if (configWrite != null) configWrite.cancel(false);
        configWrite = CONFIG_WRITER.schedule(MusicPlayerService::saveConfig, 250, TimeUnit.MILLISECONDS);
    }

    public static void play(NeteaseSong song) {
        if (song == null) return;
        playQueue(List.of(song), song);
    }

    public static void playQueue(List<NeteaseSong> queue, NeteaseSong song) {
        if (song == null) return;
        List<MusicTrack> tracks = queue == null ? List.of() : queue.stream()
                .filter(s -> s != null && s.id > 0L)
                .map(MusicTrack::from)
                .toList();
        if (tracks.isEmpty()) tracks = List.of(MusicTrack.from(song));
        int index = findTrackIndex(tracks, song.id);
        if (index < 0) {
            tracks = new java.util.ArrayList<>(tracks);
            tracks.add(MusicTrack.from(song));
            index = tracks.size() - 1;
        }
        playQueue = List.copyOf(tracks);
        playQueueIndex = index;
        playInternal(playQueue.get(index));
    }

    public static void play(MusicTrack track) {
        if (track == null) return;
        playQueue = List.of(track);
        playQueueIndex = 0;
        playInternal(track);
    }

    private static void playInternal(MusicTrack track) {
        startTrack(track, true);
    }

    private static void startTrack(MusicTrack track, boolean stopExisting) {
        startTrack(track, stopExisting, Math.max(0, playQueue.size() - 1));
    }

    private static void startTrack(MusicTrack track, boolean stopExisting, int remainingSkips) {
        long generation = nextGeneration();
        if (stopExisting) stopCurrent(false);
        long lyricsRequest;
        synchronized (PLAYBACK_LOCK) {
            currentTrack = track;
            lyricSnapshot = MusicLyrics.empty(MusicLyrics.State.LOADING);
            lyricsRequest = ++lyricGeneration;
            streamUrl = null;
            accumulatedMs = 0L;
            playbackBaseMs = 0L;
            playbackClockRunning = false;
            lastActivityAtMs = System.currentTimeMillis();
            loading = true;
            status = "Loading";
        }
        fetchLyrics(track.neteaseId, lyricsRequest);
        playbackFuture = PLAYBACK.submit(() -> {
            try {
                MusicTrack playableTrack = resolveTrackArtwork(track, generation);
                if (!isCurrent(generation)) return;
                String resolvedUrl = NeteaseClient.INSTANCE.songUrl(track.neteaseId, "standard");
                synchronized (PLAYBACK_LOCK) {
                    if (!isCurrent(generation)) return;
                    streamUrl = resolvedUrl;
                }
                if (resolvedUrl == null || resolvedUrl.isBlank()) {
                    skipUnavailable(generation, remainingSkips);
                    return;
                }
                fetchCover(playableTrack.coverUrl);
                boolean finished = playUrlWithRecovery(resolvedUrl, 0L, generation);
                if (finished && isCurrent(generation)) {
                    playNextFromCurrentGeneration(generation);
                }
            } catch (Throwable e) {
                if (isCurrent(generation)) {
                    status = "Playback failed";
                    Ardion.LOGGER.warn("Music playback failed", e);
                }
            } finally {
                synchronized (PLAYBACK_LOCK) {
                    if (isCurrent(generation)) {
                        loading = false;
                        playing = false;
                    }
                }
            }
        });
    }

    public static void stop() {
        nextGeneration();
        stopCurrent(true);
    }

    public static void toggle() {
        if (loading) {
            nextGeneration();
            stopCurrent(false);
            status = "Paused";
            return;
        }
        if (playing) {
            long pausedAt = positionMs();
            nextGeneration();
            stopCurrent(false, pausedAt);
            status = "Paused";
            lastActivityAtMs = System.currentTimeMillis();
        } else if (currentTrack != null) {
            MusicTrack track = currentTrack;
            long resume = accumulatedMs;
            long generation = nextGeneration();
            loading = true;
            playbackFuture = PLAYBACK.submit(() -> {
                try {
                    String url = streamUrl;
                    if (url == null || url.isBlank()) {
                        url = NeteaseClient.INSTANCE.songUrl(track.neteaseId, "standard");
                    }
                    synchronized (PLAYBACK_LOCK) {
                        if (!isCurrent(generation)) return;
                        streamUrl = url;
                    }
                    if (url == null || url.isBlank()) {
                        skipUnavailable(generation, Math.max(0, playQueue.size() - 1));
                        return;
                    }
                    boolean finished = playUrlWithRecovery(url, resume, generation);
                    if (finished && isCurrent(generation)) {
                        playNextFromCurrentGeneration(generation);
                    }
                } catch (Throwable e) {
                    if (isCurrent(generation)) {
                        status = "Playback failed";
                        Ardion.LOGGER.warn("Music resume failed", e);
                    }
                } finally {
                    synchronized (PLAYBACK_LOCK) {
                        if (isCurrent(generation)) { playing = false; loading = false; }
                    }
                }
            });
        }
    }

    public static void playAdjacent(int delta) {
        if (delta == 0) return;
        List<MusicTrack> queue = playQueue;
        MusicTrack current = currentTrack;
        if (current == null || queue.isEmpty()) return;
        int index = playQueueIndex >= 0 && playQueueIndex < queue.size()
                && queue.get(playQueueIndex).neteaseId == current.neteaseId
                ? playQueueIndex
                : findTrackIndex(queue, current.neteaseId);
        if (index < 0) return;
        int next = Math.floorMod(index + delta, queue.size());
        playQueueIndex = next;
        playInternal(queue.get(next));
    }

    public static void seek(long positionMs) {
        seek(positionMs, playing);
    }

    public static void seek(long positionMs, boolean resumeAfterSeek) {
        if (currentTrack == null) return;
        accumulatedMs = Math.max(0L, currentTrack.durationMs > 0L
                ? Math.min(positionMs, currentTrack.durationMs) : positionMs);
        boolean shouldResume = resumeAfterSeek;
        nextGeneration();
        stopCurrent(false, accumulatedMs);
        if (shouldResume) toggle();
    }

    public static boolean hasActiveTrack() {
        return currentTrack != null && (playing || loading || System.currentTimeMillis() - lastActivityAtMs < 30_000L);
    }

    public static MusicTrack currentTrack() {
        return currentTrack;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static boolean isLoading() {
        return loading;
    }

    public static String status() {
        return status;
    }

    public static long positionMs() {
        JavaSoundAudioDevice device = audioDevice;
        if (playing && playbackClockRunning && device != null) {
            return playbackBaseMs + Math.max(0L, device.getPosition());
        }
        return accumulatedMs;
    }

    public static float progress() {
        MusicTrack track = currentTrack;
        if (track == null || track.durationMs <= 0L) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, positionMs() / (float) track.durationMs));
    }

    public static LyricLine currentLyricLine() {
        if (currentTrack == null) return null;
        return LyricParser.currentLine(lyricSnapshot.lines(), positionMs());
    }

    public static List<LyricLine> lyrics() {
        return lyricSnapshot.lines();
    }

    public static MusicLyrics lyricSnapshot() {
        return lyricSnapshot;
    }

    public static int currentLyricWordIndex() {
        if (currentTrack == null) return -1;
        return LyricParser.currentWordIndex(currentLyricLine(), positionMs());
    }

    public static float currentLyricWordProgress() {
        if (currentTrack == null) return 0.0F;
        LyricLine line = currentLyricLine();
        return LyricParser.currentWordProgress(line, LyricParser.currentWordIndex(line, positionMs()), positionMs());
    }

    public static Image coverImage() {
        MusicTrack track = currentTrack;
        if (track == null || track.coverUrl.isBlank()) return null;
        return MusicArtworkCache.get(track.coverUrl);
    }

    public static void ensureCover(String coverUrl) {
        fetchCover(coverUrl);
    }

    private static InputStream openAudioStream(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        try (MusicHttp.Response response = MusicHttp.send(request, Duration.ofSeconds(8))) {
            if (response.statusCode() >= 400) throw new java.io.IOException("Audio HTTP " + response.statusCode());
            long length = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (length > MAX_AUDIO_BYTES) throw new java.io.IOException("Audio too large");
            byte[] body = response.body().readNBytes(MAX_AUDIO_BYTES + 1);
            if (body.length > MAX_AUDIO_BYTES) throw new java.io.IOException("Audio too large");
            return new ByteArrayInputStream(body);
        }
    }

    private static boolean playUrl(String url, long startMs, long generation) throws Exception {
        if (url == null || url.isBlank() || !isCurrent(generation)) return false;
        byte[] audio;
        try (InputStream downloaded = openAudioStream(url)) {
            audio = downloaded.readAllBytes();
        }
        if (!isCurrent(generation)) return false;
        float msPerFrame = probeMsPerFrame(audio);
        if (!isCurrent(generation)) return false;
        int startFrame = Math.max(0, (int) Math.floor(Math.max(0L, startMs) / msPerFrame));
        long actualStartMs = Math.max(0L, Math.round(startFrame * msPerFrame));
        ControlledAudioDevice device = new ControlledAudioDevice(volume, generation);
        AdvancedPlayer localPlayer = null;
        try (InputStream input = new ByteArrayInputStream(audio)) {
            if (!isCurrent(generation)) return false;
            localPlayer = new MusicDecoder(input, device, () -> isCurrent(generation), CLOSER);
            synchronized (PLAYBACK_LOCK) {
                if (!isCurrent(generation)) return false;
                accumulatedMs = actualStartMs;
                playbackBaseMs = actualStartMs;
                playbackClockRunning = false;
                playing = true;
                loading = false;
                status = "Buffering";
                audioDevice = device;
                player = localPlayer;
            }
            localPlayer.setPlayBackListener(new PlaybackListener() {
                @Override
                public void playbackStarted(PlaybackEvent event) {
                    synchronized (PLAYBACK_LOCK) {
                        if (!isCurrent(generation)) return;
                        playbackClockRunning = true;
                        status = "Playing";
                    }
                }
            });
            localPlayer.play(startFrame, Integer.MAX_VALUE);
            return isCurrent(generation);
        } catch (JavaLayerException e) {
            if (isCurrent(generation) && isAtOrPastTrackEnd(startMs)) return true;
            throw e;
        } finally {
            synchronized (PLAYBACK_LOCK) {
                if (player == localPlayer) {
                    player = null;
                    audioDevice = null;
                }
                if (isCurrent(generation)) {
                    accumulatedMs = actualStartMs + device.getPosition();
                    playbackClockRunning = false;
                    lastActivityAtMs = System.currentTimeMillis();
                }
            }
            if (localPlayer != null) localPlayer.close();
            else CLOSER.execute(device::close);
        }
    }

    private static boolean playUrlWithRecovery(String url, long startMs, long generation) throws Exception {
        long resumeMs = Math.max(0L, startMs);
        for (int attempt = 0; attempt < 2 && isCurrent(generation); attempt++) {
            boolean finished = playUrl(url, resumeMs, generation);
            if (finished || !isCurrent(generation)) {
                return finished;
            }
            long reachedMs = accumulatedMs;
            if (isAtOrPastTrackEnd(reachedMs)) {
                return true;
            }
            if (attempt == 0) {
                status = "Reconnecting";
                loading = true;
                resumeMs = reachedMs;
                continue;
            }
            return false;
        }
        return false;
    }

    private static void playNextFromCurrentGeneration(long generation) {
        MinecraftClient.getInstance().execute(() -> advanceQueue(generation, Math.max(0, playQueue.size() - 1)));
    }

    private static void skipUnavailable(long generation, int remainingSkips) {
        synchronized (PLAYBACK_LOCK) {
            if (!isCurrent(generation)) return;
            status = remainingSkips > 0 ? "Skipping unavailable track" : "No playable tracks";
            loading = false;
            playing = false;
        }
        if (remainingSkips > 0) {
            MinecraftClient.getInstance().execute(() -> advanceQueue(generation, remainingSkips - 1));
        }
    }

    private static void advanceQueue(long generation, int remainingSkips) {
        List<MusicTrack> queue = playQueue;
        MusicTrack current = currentTrack;
        if (!isCurrent(generation) || current == null || queue.size() <= 1) return;
        int index = playQueueIndex >= 0 && playQueueIndex < queue.size()
                && queue.get(playQueueIndex).neteaseId == current.neteaseId
                ? playQueueIndex
                : findTrackIndex(queue, current.neteaseId);
        if (index < 0) return;
        int next = Math.floorMod(index + 1, queue.size());
        playQueueIndex = next;
        startTrack(queue.get(next), false, remainingSkips);
    }

    private static boolean isAtOrPastTrackEnd(long fallbackPositionMs) {
        MusicTrack track = currentTrack;
        if (track == null || track.durationMs <= 0L) return false;
        long position = Math.max(fallbackPositionMs, accumulatedMs);
        if (playbackClockRunning) {
            position = Math.max(position, positionMs());
        }
        return position >= Math.max(0L, track.durationMs - 1500L);
    }

    private static void stopCurrent(boolean clear) {
        stopCurrent(clear, -1L);
    }

    private static void stopCurrent(boolean clear, long forcedPositionMs) {
        long generation = playbackGeneration;
        long reachedBeforeClose = forcedPositionMs >= 0L ? forcedPositionMs : positionMs();
        Future<?> future = playbackFuture;
        if (future != null) future.cancel(true);
        playbackFuture = null;
        PLAYBACK.purge();
        AdvancedPlayer closingPlayer;
        JavaSoundAudioDevice closingDevice;
        synchronized (PLAYBACK_LOCK) {
            closingPlayer = player;
            closingDevice = audioDevice;
            long reachedMs = reachedBeforeClose;
            if (generation == playbackGeneration && playing) accumulatedMs = reachedMs;
            else if (forcedPositionMs >= 0L) accumulatedMs = forcedPositionMs;
            player = null;
            audioDevice = null;
            playing = false;
            playbackClockRunning = false;
            lastActivityAtMs = System.currentTimeMillis();
            loading = false;
            if (clear) {
                currentTrack = null;
                ++lyricGeneration;
                lyricSnapshot = MusicLyrics.empty(MusicLyrics.State.IDLE);
                status = "Idle";
                accumulatedMs = 0L;
                playbackBaseMs = 0L;
                streamUrl = null;
                lastActivityAtMs = 0L;
            }
        }
        if (closingPlayer != null) closingPlayer.close();
        else if (closingDevice != null) CLOSER.execute(closingDevice::close);
    }

    private static void fetchLyrics(long songId, long generation) {
        LYRICS.submit(() -> {
            try {
                if (generation != lyricGeneration) return;
                NeteaseLyric lyric = NeteaseClient.INSTANCE.lyric(songId);
                MusicLyrics parsed = MusicLyrics.from(lyric);
                synchronized (PLAYBACK_LOCK) {
                    if (generation != lyricGeneration) return;
                    lyricSnapshot = parsed;
                }
            } catch (Throwable e) {
                synchronized (PLAYBACK_LOCK) {
                    if (generation == lyricGeneration) lyricSnapshot = MusicLyrics.empty(MusicLyrics.State.ERROR);
                }
            }
        });
    }

    private static void fetchCover(String coverUrl) {
        MinecraftClient.getInstance().execute(() -> MusicArtworkCache.get(coverUrl));
    }

    private static MusicTrack resolveTrackArtwork(MusicTrack track, long generation) {
        if (track == null || (track.coverUrl != null && !track.coverUrl.isBlank())) return track;
        try {
            NeteaseSong detail = NeteaseClient.INSTANCE.songDetail(track.neteaseId);
            if (!isCurrent(generation) || detail == null || detail.coverUrl == null || detail.coverUrl.isBlank()) {
                return track;
            }
            MusicTrack updated = track.withCoverUrl(detail.coverUrl);
            synchronized (PLAYBACK_LOCK) {
                if (!isCurrent(generation)) return track;
                currentTrack = updated;
            }
            fetchCover(updated.coverUrl);
            return updated;
        } catch (Throwable ignored) {
            return track;
        }
    }

    private static long nextGeneration() {
        synchronized (PLAYBACK_LOCK) {
            return ++playbackGeneration;
        }
    }

    private static boolean isCurrent(long generation) {
        return generation == playbackGeneration && !Thread.currentThread().isInterrupted();
    }

    private static int findTrackIndex(List<MusicTrack> queue, long id) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).neteaseId == id) return i;
        }
        return -1;
    }

    private static float probeMsPerFrame(byte[] audio) {
        try (InputStream raw = new ByteArrayInputStream(audio)) {
            Bitstream bitstream = new Bitstream(raw);
            try {
                Header header = bitstream.readFrame();
                if (header != null && header.ms_per_frame() > 0.0F) {
                    return header.ms_per_frame();
                }
            } finally {
                try {
                    bitstream.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return 26.12245F;
    }

    private static void loadConfig() {
        try {
            io.github.wzhy233.ardion.util.client.ArdionHome.migrateIfMissing(PREVIOUS_CONFIG_FILE, CONFIG_FILE);
            migrateLegacyConfig(LEGACY_CONFIG_FILE, CONFIG_FILE);
            if (!Files.exists(CONFIG_FILE)) {
                saveConfig();
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_FILE)).getAsJsonObject();
            if (root.has("volume") && !root.get("volume").isJsonNull()) {
                volume = Math.max(0.0F, Math.min(1.0F, root.get("volume").getAsFloat()));
            }
        } catch (Throwable e) {
            Ardion.LOGGER.warn("Music player config load failed", e);
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("volume", volume);
            Files.writeString(CONFIG_FILE, root.toString());
        } catch (Throwable e) {
            Ardion.LOGGER.warn("Music player config save failed", e);
        }
    }

    private static void migrateLegacyConfig(Path from, Path to) {
        if (Files.exists(to) || !Files.exists(from)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to);
        } catch (Throwable e) {
            Ardion.LOGGER.warn("Music player config migration failed", e);
        }
    }

    private static final class ControlledAudioDevice extends JavaSoundAudioDevice {
        private volatile float deviceVolume;
        private final long generation;
        private final PlaybackClock clock = new PlaybackClock();
        private long submittedSamples;
        private float appliedVolume = Float.NaN;

        private ControlledAudioDevice(float volume, long generation) {
            this.generation = generation;
            this.deviceVolume = Math.max(0.0F, Math.min(1.0F, volume));
        }

        private void setVolume(float volume) {
            this.deviceVolume = Math.max(0.0F, Math.min(1.0F, volume));
        }

        @Override
        protected synchronized void createSource() throws JavaLayerException {
            if (!isCurrent(generation)) throw new JavaLayerException("Playback cancelled");
            super.createSource();
            applyVolume();
        }

        @Override
        protected void writeImpl(short[] samples, int offset, int length) throws JavaLayerException {
            if (!isCurrent(generation)) throw new JavaLayerException("Playback cancelled");
            applyVolume();
            super.writeImpl(samples, offset, length);
            submittedSamples += length;
            var format = getAudioFormat();
            double submittedMs = submittedSamples * 1000.0 / (format.getSampleRate() * format.getChannels());
            clock.update(super.getPosition(), submittedMs, System.nanoTime());
        }

        @Override
        public int getPosition() {
            return (int) Math.min(Integer.MAX_VALUE, clock.positionMs(System.nanoTime()));
        }

        @Override
        protected void closeImpl() {
            clock.stop(System.nanoTime());
            super.closeImpl();
        }

        @Override
        public void flush() {
            if (isCurrent(generation)) super.flush();
        }

        @Override
        protected void flushImpl() {
            if (isCurrent(generation)) super.flushImpl();
        }

        private void applyVolume() {
            float requestedVolume = deviceVolume;
            if (Float.compare(appliedVolume, requestedVolume) == 0) return;
            try {
                SourceDataLine line = sourceLine();
                if (line == null || !line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float db = requestedVolume <= 0.0001F ? gain.getMinimum() : (float) (20.0D * Math.log10(requestedVolume));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
                appliedVolume = requestedVolume;
            } catch (Throwable ignored) {
            }
        }

        private SourceDataLine sourceLine() throws ReflectiveOperationException {
            Field field = JavaSoundAudioDevice.class.getDeclaredField("source");
            field.setAccessible(true);
            Object value = field.get(this);
            return value instanceof SourceDataLine line ? line : null;
        }
    }

}
