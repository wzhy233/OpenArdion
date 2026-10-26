package io.github.wzhy233.ardion.music;

import io.github.wzhy233.ardion.music.lyric.LyricLine;
import io.github.wzhy233.ardion.music.lyric.LyricParser;
import io.github.wzhy233.ardion.music.netease.NeteaseLyric;

import java.util.List;
import java.util.Locale;

public record MusicLyrics(State state, List<LyricLine> lines) {
    public enum State { IDLE, LOADING, READY, INSTRUMENTAL, UNAVAILABLE, ERROR }

    public MusicLyrics {
        lines = List.copyOf(lines);
    }

    public static MusicLyrics empty(State state) {
        return new MusicLyrics(state, List.of());
    }

    public static MusicLyrics from(NeteaseLyric response) {
        if (response == null) return empty(State.UNAVAILABLE);
        List<LyricLine> lines = LyricParser.parse(response.yrc, response.lrc);
        boolean markerOnly = !lines.isEmpty() && lines.stream().allMatch(line -> instrumentalMarker(line.text()));
        if (response.instrumental || markerOnly || (lines.isEmpty() && instrumentalMarker(response.lrc))) {
            return empty(State.INSTRUMENTAL);
        }
        return lines.isEmpty() ? empty(State.UNAVAILABLE) : new MusicLyrics(State.READY, lines);
    }

    private static boolean instrumentalMarker(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{Z}]", "");
        return normalized.equals("纯音乐请欣赏") || normalized.equals("纯音乐")
                || normalized.equals("純音樂請欣賞") || normalized.equals("純音樂")
                || normalized.equals("instrumental") || normalized.equals("instrumentalversion")
                || normalized.equals("musiconly");
    }
}
