package io.github.wzhy233.ardion.music;

public final class MusicSearchInput {
    private String text = "";
    private int cursor;
    private int anchor;

    public String text() { return text; }
    public int cursor() { return cursor; }
    public int selectionStart() { return Math.min(cursor, anchor); }
    public int selectionEnd() { return Math.max(cursor, anchor); }
    public String selected() { return text.substring(selectionStart(), selectionEnd()); }

    public void setText(String value) {
        text = "";
        cursor = anchor = 0;
        insert(value);
    }

    public void selectAll() { anchor = 0; cursor = text.length(); }

    public void moveTo(int position, boolean select) {
        cursor = Math.max(0, Math.min(position, text.length()));
        if (cursor > 0 && cursor < text.length() && Character.isLowSurrogate(text.charAt(cursor))) cursor--;
        if (!select) anchor = cursor;
    }

    public void move(int direction, boolean select, boolean word) {
        if (!select && cursor != anchor) {
            moveTo(direction < 0 ? selectionStart() : selectionEnd(), false);
            return;
        }
        int next = cursor;
        if (word) {
            if (direction < 0) {
                while (next > 0 && Character.isWhitespace(text.codePointBefore(next))) next = text.offsetByCodePoints(next, -1);
                while (next > 0 && !Character.isWhitespace(text.codePointBefore(next))) next = text.offsetByCodePoints(next, -1);
            } else {
                while (next < text.length() && !Character.isWhitespace(text.codePointAt(next))) next = text.offsetByCodePoints(next, 1);
                while (next < text.length() && Character.isWhitespace(text.codePointAt(next))) next = text.offsetByCodePoints(next, 1);
            }
        } else if (direction < 0 ? next > 0 : next < text.length()) {
            next = text.offsetByCodePoints(next, direction);
        }
        moveTo(next, select);
    }

    public void insert(String value) {
        if (value == null) return;
        String clean = MusicText.safe(value).replaceAll("[\\p{Cntrl}\\r\\n]", " ");
        int start = selectionStart(), end = selectionEnd();
        int remaining = 160 - text.codePointCount(0, start) - text.codePointCount(end, text.length());
        if (clean.codePointCount(0, clean.length()) > remaining)
            clean = clean.substring(0, clean.offsetByCodePoints(0, Math.max(0, remaining)));
        text = text.substring(0, start) + clean + text.substring(end);
        cursor = anchor = start + clean.length();
    }

    public void delete(boolean backwards, boolean word) {
        if (cursor != anchor) { insert(""); return; }
        move(backwards ? -1 : 1, true, word);
        insert("");
    }
}
