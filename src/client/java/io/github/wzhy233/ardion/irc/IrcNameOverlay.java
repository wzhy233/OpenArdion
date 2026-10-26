package io.github.wzhy233.ardion.irc;

import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IrcNameOverlay {
    private static volatile Map<String, String> names = Map.of();

    private IrcNameOverlay() {
    }

    public static void setNames(Map<String, String> updated) {
        if (!names.equals(updated)) names = Map.copyOf(updated);
    }

    public static void clear() {
        names = Map.of();
    }

    public static String rewrite(String input) {
        return rewriteString(input, true);
    }

    public static String rewritePlain(String input) {
        return rewriteString(input, false);
    }

    private static String rewriteString(String input, boolean formatted) {
        Map<String, String> snapshot = names;
        if (input == null || input.isEmpty() || snapshot.isEmpty()) return input;
        StringBuilder output = new StringBuilder(input.length());
        boolean changed = false;
        for (int i = 0; i < input.length();) {
            String alias = aliasAt(input, i, snapshot);
            if (alias != null) {
                if (formatted) output.append(Formatting.WHITE).append('(').append(Formatting.AQUA)
                        .append(alias).append(Formatting.WHITE).append(')').append(Formatting.RESET);
                else output.append(input, i, i + alias.length() + 2);
                i += alias.length() + 2;
                changed |= formatted;
                continue;
            }
            Match match = find(input, i, snapshot);
            if (!formatted && match != null && i > 0 && input.charAt(i - 1) == '('
                    && i + match.length < input.length() && input.charAt(i + match.length) == ')') {
                match = null;
            }
            if (match == null) {
                output.append(input.charAt(i++));
            } else {
                if (formatted) output.append(Formatting.WHITE);
                output.append('(');
                if (formatted) output.append(Formatting.AQUA);
                output.append(match.ircName);
                if (formatted) output.append(Formatting.WHITE);
                output.append(')');
                if (formatted) output.append(Formatting.RESET);
                i += match.length;
                changed = true;
            }
        }
        return changed ? output.toString() : input;
    }

    public static Text rewrite(Text input) {
        if (input == null || names.isEmpty()) return input;
        List<Glyph> chars = new ArrayList<>();
        TextVisitFactory.visitFormatted(input, Style.EMPTY, (index, style, cp) -> {
            chars.add(new Glyph(cp, style));
            return true;
        });
        List<Glyph> result = replace(chars, names);
        if (result == chars && input.getString().indexOf('\u00A7') < 0) return input;
        MutableText output = Text.empty();
        for (Glyph glyph : result) {
            output.append(Text.literal(new String(Character.toChars(glyph.codePoint))).setStyle(glyph.style));
        }
        return output;
    }

    public static OrderedText rewrite(OrderedText input) {
        if (input == null || names.isEmpty()) return input;
        List<Glyph> chars = new ArrayList<>();
        input.accept((index, style, cp) -> {
            chars.add(new Glyph(cp, style));
            return true;
        });
        List<Glyph> result = replace(chars, names);
        if (result == chars) return input;
        return visitor -> {
            int index = 0;
            for (Glyph glyph : result) {
                if (!visitor.accept(index, glyph.style, glyph.codePoint)) return false;
                index += Character.charCount(glyph.codePoint);
            }
            return true;
        };
    }

    private static List<Glyph> replace(List<Glyph> chars, Map<String, String> snapshot) {
        StringBuilder plain = new StringBuilder(chars.size());
        for (Glyph glyph : chars) plain.appendCodePoint(glyph.codePoint);
        String value = plain.toString();
        List<Glyph> output = new ArrayList<>(chars.size());
        boolean changed = false;
        int charOffset = 0;
        for (int i = 0; i < chars.size();) {
            String alias = aliasAt(value, charOffset, snapshot);
            if (alias != null) {
                int end = charOffset + alias.length() + 2;
                int start = charOffset;
                while (charOffset < end) {
                    Glyph glyph = chars.get(i++);
                    Style style = glyph.style.withColor(charOffset == start || charOffset == end - 1 ? Formatting.WHITE : Formatting.AQUA);
                    output.add(new Glyph(glyph.codePoint, style));
                    changed |= !style.equals(glyph.style);
                    charOffset += Character.charCount(glyph.codePoint);
                }
                continue;
            }
            Match match = find(value, charOffset, snapshot);
            if (match == null) {
                Glyph glyph = chars.get(i++);
                output.add(glyph);
                charOffset += Character.charCount(glyph.codePoint);
                continue;
            }
            Style base = chars.get(i).style;
            output.add(new Glyph('(', base.withColor(Formatting.WHITE)));
            match.ircName.codePoints().forEach(cp -> output.add(new Glyph(cp, base.withColor(Formatting.AQUA))));
            output.add(new Glyph(')', base.withColor(Formatting.WHITE)));
            int consumed = match.length;
            while (consumed > 0) {
                consumed -= Character.charCount(chars.get(i++).codePoint);
            }
            charOffset += match.length;
            changed = true;
        }
        return changed ? output : chars;
    }

    private static Match find(String value, int start, Map<String, String> snapshot) {
        if (start > 0 && isNameChar(value.charAt(start - 1))) return null;
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            String worldName = entry.getKey();
            int end = start + worldName.length();
            if (end <= value.length() && value.regionMatches(true, start, worldName, 0, worldName.length())
                    && (end == value.length() || !isNameChar(value.charAt(end)))) {
                return new Match(worldName.length(), entry.getValue());
            }
        }
        return null;
    }

    private static String aliasAt(String value, int start, Map<String, String> snapshot) {
        if (value.charAt(start) != '(') return null;
        for (String name : snapshot.values()) {
            if (value.startsWith("(" + name + ")", start)) return name;
        }
        return null;
    }

    private static boolean isNameChar(char value) {
        return value == '_' || value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    private record Glyph(int codePoint, Style style) {
    }

    private record Match(int length, String ircName) {
    }
}
