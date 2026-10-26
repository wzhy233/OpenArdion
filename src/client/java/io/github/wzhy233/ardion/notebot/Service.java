package io.github.wzhy233.ardion.notebot;

import io.github.wzhy233.ardion.gui.notification.Notification;
import io.github.wzhy233.ardion.gui.notification.NotificationType;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.module.impl.misc.NoteBot;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.util.rotation.Rotation;
import io.github.wzhy233.ardion.util.rotation.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

public final class Service {
    private static final int SONG_SCAN_INTERVAL_TICKS = 3600;
    private static final int NOTEBLOCK_SCAN_RADIUS = 5;
    private static final long MINECRAFT_TICK_MS = 50L;
    private static final long TUNING_PROGRESS_SMOOTH_MS = 180L;
    private static final Deque<String> queue = new ArrayDeque<>();
    private static final Map<BlockPos, Note> blockAssignments = new LinkedHashMap<>();
    private static final AtomicInteger scanTicker = new AtomicInteger();
    private static volatile List<String> scannedSongs = List.of();
    private static boolean playing;
    private static boolean loop;
    private static boolean harmony;
    private static boolean verboseOutput;
    private static boolean allocatedHarmony;
    private static boolean initializing;
    private static Song song;
    private static boolean tuning;
    private static float tuningProgress;
    private static float tuningProgressStart;
    private static float tuningProgressTarget;
    private static long tuningProgressTransitionStart;
    private static boolean postSongPause;
    private static int postSongPauseTicks;
    private static boolean nowPlayingNotificationPending;
    private static int timer = -10;
    private static long timerUpdatedAt;
    private static int tuneDelay;
    private static int tuneDelayThreshold;

    private Service() {
    }

    public static void init() {
        try {
            File.ensureDirectories();
            refreshSongs();
        } catch (IOException exception) {
            OutputUtil.error("Failed to create notebot directories: " + exception.getMessage());
        }
    }

    public static void start() {
        playing = true;
    }

    public static void stop() {
        ModuleManager.getModule(NoteBot.class).setEnabled(false);
        playing = false;
        song = null;
        tuning = false;
        initializing = false;
        nowPlayingNotificationPending = false;
        resetTuningProgress(0.0F);
        postSongPause = false;
        postSongPauseTicks = 0;
        allocatedHarmony = false;
        blockAssignments.clear();
        timer = -10;
        timerUpdatedAt = System.currentTimeMillis();
        tuneDelay = 0;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static boolean isLoop() {
        return loop;
    }

    public static void setLoop(boolean value) {
        loop = value;
    }

    public static void setHarmony(boolean value) {
        harmony = value;
    }

    public static void setVerboseOutput(boolean value) {
        verboseOutput = value;
    }

    public static boolean isVerboseOutput() {
        return verboseOutput;
    }

    public static void setTuneDelayThreshold(int value) {
        tuneDelayThreshold = value;
    }

    public static Song getSong() {
        return song;
    }

    public static boolean hasActiveSong() {
        return playing && song != null && !postSongPause;
    }

    public static boolean isTuning() {
        return hasActiveSong() && tuning;
    }

    public static boolean isInitializing() {
        return hasActiveSong() && initializing;
    }

    public static String getSongName() {
        return song == null ? "" : song.name();
    }

    public static String getSongAuthor() {
        if (song == null || song.author() == null || song.author().isBlank()) {
            return "";
        }
        String author = song.author().trim();
        if (author.equalsIgnoreCase("unknown") || author.equalsIgnoreCase("unknown artist")) {
            return "";
        }
        return author;
    }

    public static int getSongLength() {
        return song == null ? 0 : song.length();
    }

    public static int getPlaybackTick() {
        return song == null ? 0 : Math.max(0, timer);
    }

    public static float getPlaybackProgress() {
        int length = getSongLength();
        if (length <= 0) {
            return 0.0F;
        }
        float tick = Math.max(0.0F, timer);
        if (hasActiveSong() && !isTuning() && !postSongPause && timer >= 0 && timer <= length) {
            tick += Math.min(1.0F, Math.max(0.0F, (System.currentTimeMillis() - timerUpdatedAt) / (float) MINECRAFT_TICK_MS));
        }
        return Math.max(0.0F, Math.min(1.0F, tick / length));
    }

    public static float getDisplayProgress() {
        return isTuning() ? getSmoothedTuningProgress() : getPlaybackProgress();
    }

    public static int getRemainingTicks() {
        if (song == null) {
            return 0;
        }
        return Math.max(0, song.length() - getPlaybackTick());
    }

    public static List<String> getQueue() {
        return List.copyOf(queue);
    }

    public static String getSongsDirectory() {
        return File.getSongsDir().toString();
    }

    public static List<String> listAvailableSongs() {
        refreshSongsIfNeeded();
        return scannedSongs;
    }

    public static void refreshSongs() {
        try {
            scannedSongs = File.listSongs();
        } catch (IOException exception) {
            OutputUtil.error("Failed to list songs: " + exception.getMessage());
            scannedSongs = List.of();
        }
    }

    public static boolean enqueue(String filename) {
        refreshSongsIfNeeded();
        Path path = File.resolveSong(filename);
        if (!path.startsWith(File.getSongsDir())) {
            OutputUtil.error("Invalid song path: " + filename);
            return false;
        }
        if (!scannedSongs.contains(filename) || !File.isSupportedSong(filename) || !java.nio.file.Files.isRegularFile(path)) {
            OutputUtil.error("Song not found: " + filename);
            return false;
        }
        queue.addLast(filename);
        OutputUtil.infoChatOnly("Queued song: " + filename);
        return true;
    }

    public static boolean removeAt(int index) {
        if (index < 0 || index >= queue.size()) {
            OutputUtil.error("Queue index out of range: " + index);
            return false;
        }

        List<String> values = new ArrayList<>(queue);
        String removed = values.remove(index);
        queue.clear();
        queue.addAll(values);
        OutputUtil.info("Removed song: " + removed);
        return true;
    }

    public static void clearQueue() {
        queue.clear();
        OutputUtil.info("Cleared notebot queue");
    }

    public static void onTick(MinecraftClient client) {
        refreshSongsIfNeeded();

        if (!playing || client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        if (postSongPause) {
            if (postSongPauseTicks > 0) {
                postSongPauseTicks--;
                return;
            }

            postSongPause = false;
            if (loop) {
                timer = -10;
                timerUpdatedAt = System.currentTimeMillis();
            } else if (!queue.isEmpty()) {
                song = null;
                if (!loadNextSong(client)) {
                    return;
                }
                return;
            } else {
                OutputUtil.info("NoteBot queue finished");
                stop();
                return;
            }
        }

        tuning = false;
        initializing = false;

        if (song == null) {
            if (!loadNextSong(client)) {
                return;
            }
        }
        if (allocatedHarmony != shouldUseHarmony()) {
            int currentTimer = timer;
            if (!loadSong(client)) {
                song = null;
                return;
            }
            timer = currentTimer;
        }

        int totalBlocks = Math.max(1, blockAssignments.size());
        int checkedBlocks = 0;
        for (Entry<BlockPos, Note> entry : blockAssignments.entrySet()) {
            int note = getNote(client, entry.getKey());
            if (note == -1) {
                checkedBlocks++;
                continue;
            }

            if (note != entry.getValue().pitch()) {
                tuning = true;
                setTuningProgress(checkedBlocks / (float) totalBlocks);
                if (tuneDelay < tuneDelayThreshold) {
                    tuneDelay++;
                    return;
                }

                int neededNote = entry.getValue().pitch() < note ? entry.getValue().pitch() + 25 : entry.getValue().pitch();
                int requiredTunes = Math.min(25, neededNote - note);
                rotateToNoteblock(entry.getKey());
                for (int i = 0; i < requiredTunes; i++) {
                    client.interactionManager.interactBlock(
                            client.player,
                            Hand.MAIN_HAND,
                            new BlockHitResult(Vec3d.ofCenter(entry.getKey(), 1.0D), Direction.UP, entry.getKey(), true)
                    );
                }

                tuneDelay = 0;
                return;
            }
            checkedBlocks++;
        }
        setTuningProgress(1.0F);

        if (timer > song.length()) {
            announceSongTransition(song.name(), upcomingSongName());
            tuning = false;
            initializing = false;
            allocatedHarmony = false;
            blockAssignments.clear();
            postSongPause = true;
            postSongPauseTicks = 100;
            return;
        }

        timer++;
        timerUpdatedAt = System.currentTimeMillis();
        Collection<Note> currentNotes = playableNotesAt(timer);
        if (currentNotes.isEmpty()) {
            return;
        }

        announceNowPlayingIfPending();

        for (Entry<BlockPos, Note> entry : blockAssignments.entrySet()) {
            for (Note note : currentNotes) {
                if (isNoteblock(client, entry.getKey())
                        && note.equals(entry.getValue())) {
                    playBlock(client, entry.getKey());
                }
            }
        }
    }

    private static boolean loadSong(MinecraftClient client) {
        blockAssignments.clear();
        timer = -10;
        timerUpdatedAt = System.currentTimeMillis();
        resetTuningProgress(0.0F);
        initializing = true;
        nowPlayingNotificationPending = false;
        postSongPause = false;
        postSongPauseTicks = 0;

        if (song == null) {
            OutputUtil.error("No song loaded");
            initializing = false;
            return false;
        }
        if (client.interactionManager == null || !client.interactionManager.getCurrentGameMode().isSurvivalLike()) {
            OutputUtil.warn("NoteBot requires a survival-like game mode");
            initializing = false;
            return false;
        }

        BlockPos eyePos = BlockPos.ofFloored(client.player.getEyePos());
        List<BlockPos> noteblocks = BlockPos.streamOutwards(eyePos, NOTEBLOCK_SCAN_RADIUS, NOTEBLOCK_SCAN_RADIUS, NOTEBLOCK_SCAN_RADIUS)
                .filter(pos -> isNoteblock(client, pos))
                .map(BlockPos::toImmutable)
                .toList();

        Map<NoteBlockInstrument, List<Note>> mainNotesByInstrument = isNbsSong()
                ? collectSongRequirementsByInstrument()
                : collectMainNotesByInstrument();
        Map<NoteBlockInstrument, List<Note>> harmonyNotesByInstrument = shouldUseHarmony()
                ? collectHarmonyNotesByInstrument(mainNotesByInstrument)
                : Map.of();
        Map<NoteBlockInstrument, Integer> requiredInstruments = new HashMap<>();
        Map<NoteBlockInstrument, Integer> foundInstruments = new HashMap<>();
        Map<NoteBlockInstrument, List<BlockPos>> blocksByInstrument = new EnumMap<>(NoteBlockInstrument.class);
        List<SurplusBlock> surplusBlocks = new ArrayList<>();

        for (Map.Entry<NoteBlockInstrument, List<Note>> entry : mainNotesByInstrument.entrySet()) {
            requiredInstruments.put(entry.getKey(), entry.getValue().size());
        }

        for (BlockPos pos : noteblocks) {
            NoteBlockInstrument instrument = getInstrumentUnderneath(client, pos);
            blocksByInstrument.computeIfAbsent(instrument, ignored -> new ArrayList<>()).add(pos);
        }

        for (Map.Entry<NoteBlockInstrument, List<BlockPos>> entry : blocksByInstrument.entrySet()) {
            NoteBlockInstrument instrument = entry.getKey();
            List<BlockPos> blocks = entry.getValue();
            List<Note> mainNotes = mainNotesByInstrument.getOrDefault(instrument, List.of());
            int requiredCount = Math.min(blocks.size(), mainNotes.size());
            for (int i = 0; i < requiredCount; i++) {
                blockAssignments.put(blocks.get(i), mainNotes.get(i));
                foundInstruments.put(instrument, foundInstruments.getOrDefault(instrument, 0) + 1);
            }

            if (!shouldUseHarmony() || requiredCount >= blocks.size()) {
                continue;
            }

            List<Note> harmonyNotes = harmonyNotesByInstrument.getOrDefault(instrument, List.of());
            List<Note> extraPool = buildSurplusPool(mainNotes, harmonyNotes);
            if (extraPool.isEmpty()) {
                continue;
            }

            for (int i = requiredCount; i < blocks.size(); i++) {
                surplusBlocks.add(new SurplusBlock(blocks.get(i), instrument, extraPool));
            }
        }

        if (shouldUseHarmony() && !surplusBlocks.isEmpty()) {
            Map<NoteBlockInstrument, Integer> surplusCursors = new EnumMap<>(NoteBlockInstrument.class);
            for (SurplusBlock surplusBlock : surplusBlocks) {
                List<Note> candidates = surplusBlock.candidates();
                if (candidates.isEmpty()) {
                    continue;
                }
                int cursor = surplusCursors.getOrDefault(surplusBlock.instrument(), 0);
                Note note = candidates.get(cursor % candidates.size());
                surplusCursors.put(surplusBlock.instrument(), cursor + 1);
                blockAssignments.put(surplusBlock.position(), note);
            }
        }

        for (NoteBlockInstrument instrument : requiredInstruments.keySet()) {
            int missing = requiredInstruments.get(instrument) - foundInstruments.getOrDefault(instrument, 0);
            if (missing > 0) {
                verboseWarn("Missing " + missing + " noteblocks for instrument " + instrument.asString());
            }
        }

        allocatedHarmony = shouldUseHarmony();
        initializing = false;
        nowPlayingNotificationPending = true;
        return true;
    }

    private static Collection<Note> playableNotesAt(int tick) {
        List<Note> notes = song.notes().get(tick);
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        if (isNbsSong()) {
            return notes;
        }

        Note mainNote = selectMainMelodyNote(notes);
        if (!shouldUseHarmony()) {
            return List.of(mainNote);
        }

        List<Note> playable = new ArrayList<>(notes.size());
        playable.add(mainNote);
        for (Note note : notes) {
            if (!note.equals(mainNote)) {
                playable.add(note);
            }
        }
        return playable;
    }

    private static void setTuningProgress(float value) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        if (Math.abs(value - tuningProgressTarget) < 0.001F) {
            tuningProgress = getSmoothedTuningProgress();
            return;
        }

        tuningProgressStart = getSmoothedTuningProgress();
        tuningProgressTarget = value;
        tuningProgressTransitionStart = System.currentTimeMillis();
        tuningProgress = tuningProgressStart;
    }

    private static float getSmoothedTuningProgress() {
        if (tuningProgressTransitionStart == 0L) {
            return Math.max(0.0F, Math.min(1.0F, tuningProgressTarget));
        }

        float elapsed = (System.currentTimeMillis() - tuningProgressTransitionStart) / (float) TUNING_PROGRESS_SMOOTH_MS;
        float t = Math.max(0.0F, Math.min(1.0F, elapsed));
        float eased = easeOutCubic(t);
        tuningProgress = tuningProgressStart + (tuningProgressTarget - tuningProgressStart) * eased;
        if (t >= 1.0F || Math.abs(tuningProgress - tuningProgressTarget) < 0.005F) {
            tuningProgress = tuningProgressTarget;
            tuningProgressStart = tuningProgressTarget;
        }
        return Math.max(0.0F, Math.min(1.0F, tuningProgress));
    }

    private static void resetTuningProgress(float value) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        tuningProgress = value;
        tuningProgressStart = value;
        tuningProgressTarget = value;
        tuningProgressTransitionStart = System.currentTimeMillis();
    }

    private static float easeOutCubic(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static Map<NoteBlockInstrument, List<Note>> collectMainNotesByInstrument() {
        Map<NoteBlockInstrument, List<Note>> notesByInstrument = new EnumMap<>(NoteBlockInstrument.class);
        List<Integer> ticks = new ArrayList<>(song.notes().keySet());
        ticks.sort(Comparator.naturalOrder());
        for (Integer tick : ticks) {
            List<Note> notes = song.notes().get(tick);
            if (notes == null || notes.isEmpty()) {
                continue;
            }
            addUniqueNote(notesByInstrument, selectMainMelodyNote(notes));
        }
        return notesByInstrument;
    }

    private static Map<NoteBlockInstrument, List<Note>> collectSongRequirementsByInstrument() {
        Map<NoteBlockInstrument, List<Note>> notesByInstrument = new EnumMap<>(NoteBlockInstrument.class);
        for (Note note : song.requirements()) {
            addUniqueNote(notesByInstrument, note);
        }
        return notesByInstrument;
    }

    private static Map<NoteBlockInstrument, List<Note>> collectHarmonyNotesByInstrument(Map<NoteBlockInstrument, List<Note>> mainNotesByInstrument) {
        Map<NoteBlockInstrument, List<Note>> notesByInstrument = new EnumMap<>(NoteBlockInstrument.class);
        Set<Note> mainNotes = new HashSet<>();
        for (List<Note> notes : mainNotesByInstrument.values()) {
            mainNotes.addAll(notes);
        }

        List<Integer> ticks = new ArrayList<>(song.notes().keySet());
        ticks.sort(Comparator.naturalOrder());
        for (Integer tick : ticks) {
            List<Note> notes = song.notes().get(tick);
            if (notes == null || notes.isEmpty()) {
                continue;
            }
            for (Note note : notes) {
                if (!mainNotes.contains(note)) {
                    addUniqueNote(notesByInstrument, note);
                }
            }
        }
        return notesByInstrument;
    }

    private static Note selectMainMelodyNote(List<Note> notes) {
        Note selected = notes.getFirst();
        for (Note note : notes) {
            if (note.pitch() > selected.pitch()) {
                selected = note;
            }
        }
        return selected;
    }

    private static void addUniqueNote(Map<NoteBlockInstrument, List<Note>> notesByInstrument, Note note) {
        NoteBlockInstrument instrument = noteInstrument(note);
        List<Note> notes = notesByInstrument.computeIfAbsent(instrument, ignored -> new ArrayList<>());
        if (!notes.contains(note)) {
            notes.add(note);
        }
    }

    private record SurplusBlock(BlockPos position, NoteBlockInstrument instrument, List<Note> candidates) {
    }

    private static boolean loadNextSong(MinecraftClient client) {
        if (queue.isEmpty()) {
            OutputUtil.warn("NoteBot queue is empty");
            stop();
            return false;
        }

        String nextSong = queue.removeFirst();
        try {
            song = Parser.parse(File.resolveSong(nextSong));
        } catch (IOException exception) {
            OutputUtil.error("Failed to load song " + nextSong + ": " + exception.getMessage());
            song = null;
            return false;
        }

        if (!loadSong(client)) {
            song = null;
            nowPlayingNotificationPending = false;
            return false;
        }

        return true;
    }

    private static void verboseInfo(String message) {
        if (verboseOutput) {
            OutputUtil.info(message);
        }
    }

    private static void verboseWarn(String message) {
        if (verboseOutput) {
            OutputUtil.warn(message);
        }
    }

    private static List<Note> buildSurplusPool(List<Note> mainNotes, List<Note> harmonyNotes) {
        List<Note> pool = new ArrayList<>(mainNotes.size() + harmonyNotes.size());
        for (Note note : harmonyNotes) {
            if (!pool.contains(note)) {
                pool.add(note);
            }
        }
        for (Note note : mainNotes) {
            if (!pool.contains(note)) {
                pool.add(note);
            }
        }
        return pool;
    }

    private static void announceSongTransition(String finishedSong, String upcomingSong) {
        if (finishedSong != null && !finishedSong.isBlank()) {
            HUD.postNotification(new Notification("Finished: " + finishedSong, 2000L, NotificationType.INFO));
        }
        if (upcomingSong != null && !upcomingSong.isBlank()) {
            HUD.postNotification(new Notification("Next up: " + upcomingSong, 2000L, NotificationType.INFO));
        }
    }

    private static void announceNowPlayingIfPending() {
        if (!nowPlayingNotificationPending || song == null || initializing || tuning || postSongPause) {
            return;
        }

        OutputUtil.info("Now playing: " + song.name() + " [" + song.format() + "]");
        nowPlayingNotificationPending = false;
    }

    private static boolean shouldUseHarmony() {
        return harmony && !isNbsSong();
    }

    private static boolean isNbsSong() {
        return song != null && song.format().startsWith("NBS");
    }

    private static String upcomingSongName() {
        if (loop && queue.isEmpty() && song != null) {
            return song.name();
        }
        return queue.peekFirst();
    }

    private static void refreshSongsIfNeeded() {
        if (scanTicker.incrementAndGet() >= SONG_SCAN_INTERVAL_TICKS) {
            scanTicker.set(0);
            refreshSongs();
        }
    }

    private static int getNote(MinecraftClient client, BlockPos pos) {
        if (!isNoteblock(client, pos)) {
            return -1;
        }
        return client.world.getBlockState(pos).get(NoteBlock.NOTE);
    }

    private static void playBlock(MinecraftClient client, BlockPos pos) {
        if (!isNoteblock(client, pos)) {
            return;
        }
        rotateToNoteblock(pos);
        client.interactionManager.attackBlock(pos, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void rotateToNoteblock(BlockPos pos) {
        Rotation rotation = RotationUtil.calculate(pos, Direction.UP);
        RotationManager.get().setRotations(rotation, 180.0D, false, false);
    }

    private static boolean isNoteblock(MinecraftClient client, BlockPos pos) {
        return client.world.getBlockState(pos).getBlock() instanceof NoteBlock
                && (client.world.getBlockState(pos.up()).isAir() || instrumentFromHeadAbove(client, pos) != null);
    }

    private static NoteBlockInstrument getInstrumentUnderneath(MinecraftClient client, BlockPos pos) {
        if (!isNoteblock(client, pos)) {
            return NoteBlockInstrument.HARP;
        }
        NoteBlockInstrument headInstrument = instrumentFromHeadAbove(client, pos);
        if (headInstrument != null) {
            return headInstrument;
        }
        Block block = client.world.getBlockState(pos.down()).getBlock();
        return blockToInstrument(block);
    }

    private static NoteBlockInstrument noteInstrument(Note note) {
        NoteBlockInstrument[] values = NoteBlockInstrument.values();
        return note.instrument() >= 0 && note.instrument() < values.length
                ? values[note.instrument()]
                : NoteBlockInstrument.HARP;
    }

    private static NoteBlockInstrument instrumentFromHeadAbove(MinecraftClient client, BlockPos pos) {
        Block block = client.world.getBlockState(pos.up()).getBlock();
        String blockIdString = Registries.BLOCK.getId(block).toString();
        return switch (blockIdString) {
            case "minecraft:zombie_head", "minecraft:zombie_wall_head" -> NoteBlockInstrument.ZOMBIE;
            case "minecraft:skeleton_skull", "minecraft:skeleton_wall_skull" -> NoteBlockInstrument.SKELETON;
            case "minecraft:creeper_head", "minecraft:creeper_wall_head" -> NoteBlockInstrument.CREEPER;
            case "minecraft:dragon_head", "minecraft:dragon_wall_head" -> NoteBlockInstrument.DRAGON;
            case "minecraft:wither_skeleton_skull", "minecraft:wither_skeleton_wall_skull" -> NoteBlockInstrument.WITHER_SKELETON;
            case "minecraft:piglin_head", "minecraft:piglin_wall_head" -> NoteBlockInstrument.PIGLIN;
            case "minecraft:player_head", "minecraft:player_wall_head" -> NoteBlockInstrument.CUSTOM_HEAD;
            default -> null;
        };
    }

    private static NoteBlockInstrument blockToInstrument(Block block) {
        Identifier blockId = Registries.BLOCK.getId(block);
        String blockIdString = blockId.toString();
        if (blockIdString.equals("minecraft:dirt") || blockIdString.equals("minecraft:air")) {
            return NoteBlockInstrument.HARP;
        }
        if (blockIdString.equals("minecraft:clay")) {
            return NoteBlockInstrument.FLUTE;
        }
        if (blockIdString.equals("minecraft:gold_block")) {
            return NoteBlockInstrument.BELL;
        }
        if (blockIdString.equals("minecraft:packed_ice")) {
            return NoteBlockInstrument.CHIME;
        }
        if (blockIdString.equals("minecraft:bone_block")) {
            return NoteBlockInstrument.XYLOPHONE;
        }
        if (blockIdString.equals("minecraft:iron_block")) {
            return NoteBlockInstrument.IRON_XYLOPHONE;
        }
        if (blockIdString.equals("minecraft:soul_sand")) {
            return NoteBlockInstrument.COW_BELL;
        }
        if (blockIdString.equals("minecraft:pumpkin")) {
            return NoteBlockInstrument.DIDGERIDOO;
        }
        if (blockIdString.equals("minecraft:emerald_block")) {
            return NoteBlockInstrument.BIT;
        }
        if (blockIdString.equals("minecraft:hay_block")) {
            return NoteBlockInstrument.BANJO;
        }
        if (blockIdString.equals("minecraft:glowstone")) {
            return NoteBlockInstrument.PLING;
        }
        if (blockIdString.equals("minecraft:sand")
                || blockIdString.equals("minecraft:gravel")
                || blockIdString.endsWith("_concrete_powder")) {
            return NoteBlockInstrument.SNARE;
        }
        if (Arrays.asList(
                "minecraft:stone",
                "minecraft:cobblestone",
                "minecraft:blackstone",
                "minecraft:netherrack",
                "minecraft:nylium",
                "minecraft:obsidian",
                "minecraft:quartz",
                "minecraft:sandstone",
                "minecraft:ores",
                "minecraft:bricks",
                "minecraft:corals",
                "minecraft:respawn_anchor",
                "minecraft:bedrock",
                "minecraft:concrete"
        ).contains(blockIdString) || blockIdString.endsWith("_concrete")) {
            return NoteBlockInstrument.BASEDRUM;
        }
        if (blockIdString.equals("minecraft:glass") || blockIdString.endsWith("_stained_glass")) {
            return NoteBlockInstrument.HAT;
        }

        BlockSoundGroup material = block.getDefaultState().getSoundGroup();
        if (material.equals(BlockSoundGroup.WOOD)) {
            return NoteBlockInstrument.BASS;
        }
        if (material.equals(BlockSoundGroup.WOOL)) {
            return NoteBlockInstrument.GUITAR;
        }
        if (material.equals(BlockSoundGroup.GLASS)) {
            return NoteBlockInstrument.HAT;
        }
        if (material.equals(BlockSoundGroup.STONE)) {
            return NoteBlockInstrument.BASEDRUM;
        }
        return NoteBlockInstrument.HARP;
    }
}
