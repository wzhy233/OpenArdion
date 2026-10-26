package io.github.wzhy233.ardion.music;

import java.util.function.ToDoubleFunction;

public final class MusicText {
    private MusicText() {}

    public static String safe(String text) {
        if (text == null) return "";
        StringBuilder repaired = null;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                i++;
            } else if (Character.isSurrogate(ch)) {
                if (repaired == null) repaired = new StringBuilder(text);
                repaired.setCharAt(i, '\uFFFD');
            }
        }
        return repaired == null ? text : repaired.toString();
    }

    public static String limit(String text, float width, ToDoubleFunction<String> measure) {
        text = safe(text);
        if (text.isEmpty() || width <= 0) return "";
        if (measure.applyAsDouble(text) <= width) return text;
        String suffix = "...";
        if (measure.applyAsDouble(suffix) > width) return "";
        int end = text.length();
        do {
            end = text.offsetByCodePoints(end, -1);
        } while (end > 0 && measure.applyAsDouble(text.substring(0, end) + suffix) > width);
        return text.substring(0, end) + suffix;
    }

    public static float width(String text, ToDoubleFunction<String> measure) {
        text = safe(text);
        return text.isEmpty() ? 0 : (float) measure.applyAsDouble(text);
    }
}
