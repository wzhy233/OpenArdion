package io.github.wzhy233.ardion.util.text;

public final class TextUtil {

    private TextUtil() {
    }

    public static String safe(String text) {
        return text == null ? "" : text;
    }
}
