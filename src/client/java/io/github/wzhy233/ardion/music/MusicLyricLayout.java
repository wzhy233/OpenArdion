package io.github.wzhy233.ardion.music;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class MusicLyricLayout {
    public record Segment(String text, int offset) {}

    private MusicLyricLayout() {}

    public static List<Segment> wrap(String text, float width, ToDoubleFunction<String> measure) {
        text = MusicText.safe(text);
        List<Segment> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = start, wordBreak = -1;
            while (end < text.length()) {
                int next = text.offsetByCodePoints(end, 1);
                if (end > start && measure.applyAsDouble(text.substring(start, next)) > width) break;
                if (Character.isWhitespace(text.codePointAt(end))) wordBreak = next;
                end = next;
            }
            if (end < text.length() && wordBreak > start) end = wordBreak;
            result.add(new Segment(text.substring(start, end), start));
            start = end;
        }
        if (result.isEmpty()) result.add(new Segment("", 0));
        return List.copyOf(result);
    }
}
