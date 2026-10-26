package io.github.wzhy233.ardion.music.lyric;

import java.util.List;

public record LyricLine(long startMs, long durationMs, String text, List<LyricWord> words) {
    public long endMs() {
        return startMs + Math.max(durationMs, 0L);
    }
}
