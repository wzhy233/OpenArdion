package io.github.wzhy233.ardion.notebot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import javax.sound.midi.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class Parser {
    private static final int DEFAULT_TEMPO_US_PER_QUARTER = 500_000;
    private static final double MINECRAFT_TICK_US = 50_000D;

    private Parser() {
    }

    public static Song parse(Path path) throws IOException {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".mid") || filename.endsWith(".midi")) {
            return parseMidi(path);
        }
        if (filename.endsWith(".nbs")) {
            return parseNbs(path);
        }
        return parseTxt(path);
    }

    private static Song parseTxt(Path path) throws IOException {
        Map<Integer, List<Note>> notes = new HashMap<>();
        String name = stripExtension(path.getFileName().toString());
        String author = "Unknown";

        for (String line : Files.readAllLines(path)) {
            if (line.startsWith("// Name: ")) {
                name = line.substring("// Name: ".length()).trim();
                continue;
            }
            if (line.startsWith("// Author: ")) {
                author = line.substring("// Author: ".length()).trim();
                continue;
            }
            if (line.isBlank() || line.startsWith("//")) {
                continue;
            }

            String[] split = line.split(":");
            if (split.length < 3) {
                continue;
            }

            try {
                addNote(notes, Integer.parseInt(split[0]), new Note(Integer.parseInt(split[1]), Integer.parseInt(split[2])));
            } catch (NumberFormatException ignored) {
            }
        }

        return new Song(path.getFileName().toString(), name, author, "Text", notes);
    }

    private static Song parseMidi(Path path) throws IOException {
        Map<Integer, List<Note>> notes = new HashMap<>();
        List<MidiNoteEvent> midiNotes = new ArrayList<>();
        List<RawMidiEvent> rawEvents = new ArrayList<>();
        String name = stripExtension(path.getFileName().toString());
        String author = "Unknown";
        int minKey = Integer.MAX_VALUE;
        int maxKey = Integer.MIN_VALUE;

        try {
            MidiFileFormat midiFormat = MidiSystem.getMidiFileFormat(path.toFile());
            Sequence sequence = MidiSystem.getSequence(path.toFile());
            MidiMetadata metadata = readMidiMetadata(path, midiFormat, sequence);
            name = metadata.name();
            author = metadata.author();
            int resolution = sequence.getResolution();
            int order = 0;

            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    rawEvents.add(new RawMidiEvent(event.getTick(), order++, event.getMessage()));
                }
            }

            rawEvents.sort(Comparator
                    .comparingLong(RawMidiEvent::tick)
                    .thenComparingInt(Parser::midiEventPriority)
                    .thenComparingInt(RawMidiEvent::order));

            int[] channelInstruments = new int[16];
            if (sequence.getDivisionType() == Sequence.PPQ) {
                long lastTick = 0;
                double elapsedUs = 0D;
                int tempoUsPerQuarter = DEFAULT_TEMPO_US_PER_QUARTER;

                for (RawMidiEvent event : rawEvents) {
                    long tickDelta = event.tick() - lastTick;
                    if (tickDelta > 0) {
                        elapsedUs += tickDelta * (tempoUsPerQuarter / (double) resolution);
                        lastTick = event.tick();
                    }

                    if (event.message() instanceof MetaMessage metaMessage && metaMessage.getType() == 0x51) {
                        int tempo = readTempoUsPerQuarter(metaMessage);
                        if (tempo > 0) {
                            tempoUsPerQuarter = tempo;
                        }
                        continue;
                    }

                    if (event.message() instanceof ShortMessage shortMessage) {
                        minKey = collectMidiShortMessage(midiNotes, channelInstruments, shortMessage, elapsedUs, minKey);
                        maxKey = collectMidiShortMessageMaxKey(shortMessage, maxKey);
                    }
                }
            } else {
                double ticksPerSecond = sequence.getDivisionType() * resolution;
                for (RawMidiEvent event : rawEvents) {
                    if (event.message() instanceof ShortMessage shortMessage) {
                        double elapsedUs = ticksPerSecond <= 0 ? 0D : event.tick() * 1_000_000D / ticksPerSecond;
                        minKey = collectMidiShortMessage(midiNotes, channelInstruments, shortMessage, elapsedUs, minKey);
                        maxKey = collectMidiShortMessageMaxKey(shortMessage, maxKey);
                    }
                }
            }

            if (!midiNotes.isEmpty()) {
                int transpose = chooseMidiTranspose(midiNotes, minKey, maxKey);
                for (MidiNoteEvent midiNote : midiNotes) {
                    int pitch = fitMidiPitchToNoteblockRange(midiNote.key(), transpose);
                    addNote(notes, midiNote.tick(), new Note(pitch, midiNote.instrument()));
                }
            }
        } catch (Exception exception) {
            throw new IOException("Failed to parse MIDI: " + path.getFileName(), exception);
        }

        return new Song(path.getFileName().toString(), name, author, "MIDI", notes);
    }

    private static MidiMetadata readMidiMetadata(Path path, MidiFileFormat midiFormat, Sequence sequence) {
        String fallbackName = stripExtension(path.getFileName().toString());
        String name = cleanMidiMetadataValue(midiStringProperty(midiFormat, "title", "name"));
        String author = cleanMidiMetadataValue(midiStringProperty(midiFormat, "author", "artist", "composer"));
        String firstCopyright = "";
        String firstAuthorText = "";

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiMessage message = track.get(i).getMessage();
                if (!(message instanceof MetaMessage metaMessage)) {
                    continue;
                }

                String text = decodeMidiText(metaMessage.getData());
                if (text.isBlank()) {
                    continue;
                }

                if (metaMessage.getType() == 0x02 && firstCopyright.isBlank()) {
                    firstCopyright = text;
                }

                String parsedName = cleanMidiMetadataValue(parseTaggedMidiText(text, "title", "name", "song"));
                if (name.isBlank() && !parsedName.isBlank()) {
                    name = parsedName;
                }

                String parsedAuthor = cleanMidiMetadataValue(parseTaggedMidiText(text, "author", "artist", "composer", "creator", "by"));
                if (firstAuthorText.isBlank() && !parsedAuthor.isBlank()) {
                    firstAuthorText = parsedAuthor;
                }
            }
        }

        if (author.isBlank()) {
            author = !firstAuthorText.isBlank() ? firstAuthorText : cleanMidiMetadataValue(cleanMidiAuthor(firstCopyright));
        }
        if (author.isBlank()) {
            author = "Unknown";
        }
        return new MidiMetadata(name.isBlank() ? fallbackName : name, author);
    }

    private static String midiStringProperty(MidiFileFormat midiFormat, String... keys) {
        Map<String, Object> properties = midiFormat.properties();
        for (String key : keys) {
            Object value = properties.get(key);
            if (value instanceof String string && !string.isBlank()) {
                return string.trim();
            }
        }
        return "";
    }

    private static String cleanMidiMetadataValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.trim();
        return isLikelyMidiInstrumentName(cleaned) ? "" : cleaned;
    }

    private static boolean isLikelyMidiInstrumentName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.equals("piano")
                || normalized.equals("\u94A2\u7434")
                || normalized.equals("acoustic grand piano")
                || normalized.equals("bright acoustic piano")
                || normalized.equals("electric grand piano")
                || normalized.equals("honky tonk piano")
                || normalized.equals("electric piano")
                || normalized.equals("electric piano 1")
                || normalized.equals("electric piano 2");
    }

    private static String decodeMidiText(byte[] data) {
        return new String(data, StandardCharsets.UTF_8).replace('\u0000', ' ').trim();
    }

    private static String parseTaggedMidiText(String text, String... tags) {
        String normalized = text.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String tag : tags) {
            String prefix = tag.toLowerCase(Locale.ROOT);
            if (lower.startsWith(prefix + ":")) {
                return normalized.substring(prefix.length() + 1).trim();
            }
            if (lower.startsWith(prefix + "=")) {
                return normalized.substring(prefix.length() + 1).trim();
            }
            if (prefix.equals("by") && lower.startsWith("by ")) {
                return normalized.substring(3).trim();
            }
        }
        return "";
    }

    private static String cleanMidiAuthor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.trim();
        String lower = cleaned.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("copyright", "(c)", "\u00A9")) {
            if (lower.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length()).trim();
                if (cleaned.startsWith(":")) {
                    cleaned = cleaned.substring(1).trim();
                }
                break;
            }
        }
        return cleaned;
    }

    private static int collectMidiShortMessage(List<MidiNoteEvent> midiNotes, int[] channelInstruments, ShortMessage message, double elapsedUs, int minKey) {
        if (message.getCommand() == ShortMessage.PROGRAM_CHANGE) {
            channelInstruments[message.getChannel()] = mapMidiInstrument(message.getData1());
            return minKey;
        }

        if (message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0) {
            int key = message.getData1();
            midiNotes.add(new MidiNoteEvent((int) Math.round(elapsedUs / MINECRAFT_TICK_US), key, channelInstruments[message.getChannel()]));
            return Math.min(minKey, key);
        }

        return minKey;
    }

    private static int collectMidiShortMessageMaxKey(ShortMessage message, int maxKey) {
        if (message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0) {
            return Math.max(maxKey, message.getData1());
        }
        return maxKey;
    }

    private static int midiEventPriority(RawMidiEvent event) {
        MidiMessage message = event.message();
        if (message instanceof MetaMessage metaMessage && metaMessage.getType() == 0x51) {
            return 0;
        }
        if (message instanceof ShortMessage shortMessage && shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE) {
            return 1;
        }
        if (message instanceof ShortMessage shortMessage && shortMessage.getCommand() == ShortMessage.NOTE_ON) {
            return 2;
        }
        return 3;
    }

    private static int readTempoUsPerQuarter(MetaMessage message) {
        byte[] data = message.getData();
        if (data.length < 3) {
            return -1;
        }
        return (data[0] & 0xFF) << 16 | (data[1] & 0xFF) << 8 | (data[2] & 0xFF);
    }

    private static Song parseNbs(Path path) throws IOException {
        Map<Integer, List<Note>> notes = new HashMap<>();
        String name = stripExtension(path.getFileName().toString());
        String author = "Unknown";
        int version;

        try (InputStream input = Files.newInputStream(path)) {
            version = readShort(input) != 0 ? 0 : input.read();
            input.skip(version >= 3 ? 5 : version >= 1 ? 3 : 2);

            String fileName = readString(input);
            String songAuthor = readString(input);
            String originalAuthor = readString(input);
            if (!fileName.isEmpty()) {
                name = fileName;
            }
            if (!originalAuthor.isEmpty()) {
                author = originalAuthor;
            } else if (!songAuthor.isEmpty()) {
                author = songAuthor;
            }

            readString(input);
            float tempo = readShort(input) / 100.0F;
            input.skip(23);
            readString(input);
            if (version >= 4) {
                input.skip(4);
            }

            double tick = -1;
            short jump;
            while ((jump = readShort(input)) != 0) {
                tick += jump * (20.0F / tempo);
                while (readShort(input) != 0) {
                    int instrument = mapNbsInstrument(input.read());
                    int key = input.read() - 33;
                    if (key < 0) {
                        warnOutOfRange(tick, key, true);
                        key = Math.floorMod(key, 12);
                    } else if (key > 24) {
                        warnOutOfRange(tick, key, false);
                        key = Math.floorMod(key, 12) + 12;
                    }

                    addNote(notes, (int) Math.round(tick), new Note(key, instrument));
                    if (version >= 4) {
                        input.skip(4);
                    }
                }
            }
        }

        return new Song(path.getFileName().toString(), name, author, "NBS v" + version, notes);
    }

    private static void warnOutOfRange(double tick, int key, boolean below) {
        if (!Service.isVerboseOutput()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.sendMessage(Text.literal("Note @" + tick + " key " + key + (below ? " is below" : " is above") + " the two-octave range."), false);
    }

    private static void addNote(Map<Integer, List<Note>> notes, int tick, Note note) {
        notes.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(note);
    }

    private static int mapMidiInstrument(int program) {
        if (program <= 20) {
            return 0;
        }
        if (program <= 51) {
            return 7;
        }
        return 0;
    }

    private static int mapNbsInstrument(int instrument) {
        return switch (instrument) {
            case 1 -> 4;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 7;
            case 6 -> 5;
            case 7 -> 6;
            default -> instrument > 22 ? 0 : instrument;
        };
    }

    private static int fitPitchToNoteblockRange(int pitch) {
        while (pitch < 0) {
            pitch += 12;
        }
        while (pitch > 24) {
            pitch -= 12;
        }
        return pitch;
    }

    private static int chooseMidiTranspose(List<MidiNoteEvent> midiNotes, int minKey, int maxKey) {
        int lowerAnchor = Math.min(minKey, maxKey - 24);
        int upperAnchor = Math.max(minKey, maxKey - 24);
        int candidateStart = lowerAnchor - 24;
        int candidateEnd = upperAnchor + 24;

        MidiTransposeChoice best = null;
        for (int transpose = candidateStart; transpose <= candidateEnd; transpose++) {
            long octaveAdjustments = 0L;
            long totalPitch = 0L;
            int maxPitch = 0;

            for (MidiNoteEvent midiNote : midiNotes) {
                int rawPitch = midiNote.key() - transpose;
                int pitch = fitPitchToNoteblockRange(rawPitch);
                octaveAdjustments += Math.abs((pitch - rawPitch) / 12);
                totalPitch += pitch;
                maxPitch = Math.max(maxPitch, pitch);
            }

            MidiTransposeChoice candidate = new MidiTransposeChoice(transpose, octaveAdjustments, totalPitch, maxPitch);
            if (isBetterMidiTransposeChoice(candidate, best)) {
                best = candidate;
            }
        }

        return best == null ? minKey : best.transpose();
    }

    private static boolean isBetterMidiTransposeChoice(MidiTransposeChoice candidate, MidiTransposeChoice current) {
        if (current == null) {
            return true;
        }
        if (candidate.octaveAdjustments() != current.octaveAdjustments()) {
            return candidate.octaveAdjustments() < current.octaveAdjustments();
        }
        if (candidate.totalPitch() != current.totalPitch()) {
            return candidate.totalPitch() < current.totalPitch();
        }
        if (candidate.maxPitch() != current.maxPitch()) {
            return candidate.maxPitch() < current.maxPitch();
        }
        return candidate.transpose() > current.transpose();
    }

    private static int fitMidiPitchToNoteblockRange(int key, int transpose) {
        return fitPitchToNoteblockRange(key - transpose);
    }

    private static short readShort(InputStream input) throws IOException {
        return (short) ((input.read() & 0xFF) | (input.read() << 8));
    }

    private static int readInt(InputStream input) throws IOException {
        return input.read() | input.read() << 8 | input.read() << 16 | input.read() << 24;
    }

    private static String readString(InputStream input) throws IOException {
        return new String(input.readNBytes(readInt(input)));
    }

    private static String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(0, index) : filename;
    }

    private record RawMidiEvent(long tick, int order, MidiMessage message) {
    }

    private record MidiNoteEvent(int tick, int key, int instrument) {
    }

    private record MidiTransposeChoice(int transpose, long octaveAdjustments, long totalPitch, int maxPitch) {
    }

    private record MidiMetadata(String name, String author) {
    }
}
