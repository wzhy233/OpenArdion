package io.github.wzhy233.ardion.music.lyric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricParser {
    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]\\s*(.*)");
    private static final Pattern YRC_LINE = Pattern.compile("\\[(\\d+),(\\d+)](.*)");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]+)");

    private LyricParser() {
    }

    public static List<LyricLine> parse(String yrc, String lrc) {
        List<LyricLine> yrcLines = parseYrc(yrc);
        if (!yrcLines.isEmpty()) return yrcLines;
        return parseLrc(lrc);
    }

    public static LyricLine currentLine(List<LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return null;
        LyricLine current = null;
        for (LyricLine line : lines) {
            if (line.startMs() > positionMs) break;
            current = line;
        }
        return current;
    }

    public static int currentWordIndex(LyricLine line, long positionMs) {
        if (line == null || line.words().isEmpty()) return -1;
        int index = -1;
        for (int i = 0; i < line.words().size(); i++) {
            LyricWord word = line.words().get(i);
            if (word.startMs() <= positionMs) index = i;
            else break;
        }
        return index;
    }

    public static float currentWordProgress(LyricLine line, int index, long positionMs) {
        if (line == null || index < 0 || index >= line.words().size()) return 0.0F;
        LyricWord word = line.words().get(index);
        long duration = Math.max(1L, word.durationMs());
        return Math.max(0.0F, Math.min(1.0F, (positionMs - word.startMs()) / (float) duration));
    }

    private static List<LyricLine> parseYrc(String content) {
        List<LyricLine> out = new ArrayList<>();
        if (content == null || content.isBlank()) return out;
        for (String raw : content.split("\\R")) {
            Matcher lineMatcher = YRC_LINE.matcher(raw);
            if (!lineMatcher.matches()) continue;
            long start = parseLong(lineMatcher.group(1));
            long duration = parseLong(lineMatcher.group(2));
            String body = lineMatcher.group(3);
            List<LyricWord> words = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            Matcher wordMatcher = YRC_WORD.matcher(body);
            while (wordMatcher.find()) {
                long wordStart = parseLong(wordMatcher.group(1));
                long wordDuration = parseLong(wordMatcher.group(2));
                String wordText = wordMatcher.group(3);
                if (wordText.isEmpty()) continue;
                text.append(wordText);
                words.add(new LyricWord(wordText, wordStart, wordDuration));
            }
            if (!text.isEmpty()) out.add(new LyricLine(start, duration, text.toString(), List.copyOf(words)));
        }
        out.sort(Comparator.comparingLong(LyricLine::startMs));
        return out;
    }

    private static List<LyricLine> parseLrc(String content) {
        List<LyricLine> out = new ArrayList<>();
        if (content == null || content.isBlank()) return out;
        for (String raw : content.split("\\R")) {
            Matcher matcher = LRC_LINE.matcher(raw);
            if (!matcher.matches()) continue;
            long start = parseTime(matcher.group(1), matcher.group(2), matcher.group(3));
            String text = matcher.group(4).strip();
            if (!text.isEmpty()) out.add(new LyricLine(start, 0L, text, List.of()));
        }
        out.sort(Comparator.comparingLong(LyricLine::startMs));
        for (int i = 0; i < out.size(); i++) {
            LyricLine line = out.get(i);
            long end = i + 1 < out.size() ? out.get(i + 1).startMs() : line.startMs() + 5000L;
            out.set(i, new LyricLine(line.startMs(), Math.max(0L, end - line.startMs()), line.text(), line.words()));
        }
        return out;
    }

    private static long parseTime(String min, String sec, String frac) {
        long ms = parseLong(min) * 60_000L + parseLong(sec) * 1000L;
        if (frac != null && !frac.isEmpty()) {
            String padded = (frac + "000").substring(0, 3);
            ms += parseLong(padded);
        }
        return ms;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
