package io.github.wzhy233.ardion.ai;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiChatText {
    private static final Pattern HEADING = Pattern.compile("#{1,6}\\s+(.+)");
    private static final Pattern QUOTE = Pattern.compile(">\\s?(.*)");
    private static final Pattern ORDERED = Pattern.compile("(\\d+)\\.\\s+(.+)");
    private static final Pattern BULLET = Pattern.compile("[-*+]\\s+(.+)");
    private static final Pattern TOOL_TOKEN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'|\\S+");

    private AiChatText() {
    }

    public static Text toolLine(String line) {
        MutableText out = Text.empty();
        Matcher tokens = TOOL_TOKEN.matcher(line);
        int end = 0, index = 0;
        while (tokens.find()) {
            out.append(Text.literal(line.substring(end, tokens.start())).formatted(Formatting.GRAY));
            String token = tokens.group();
            Formatting color = index == 0 ? Formatting.GRAY : index == 1 ? Formatting.AQUA
                    : token.startsWith("\"") || token.startsWith("'") ? Formatting.GREEN
                    : token.startsWith("-") ? Formatting.YELLOW : Formatting.WHITE;
            out.append(Text.literal(token).formatted(color));
            index++;
            end = tokens.end();
        }
        return out.append(Text.literal(line.substring(end)).formatted(Formatting.GRAY));
    }

    public static Text format(String raw, Formatting color) {
        MutableText out = Text.empty();
        if (raw == null || raw.isEmpty()) return out;
        String[] lines = clean(raw).split("\n", -1);
        boolean code = false;
        boolean first = true;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                code = !code;
                continue;
            }
            if (!first) out.append(Text.literal("\n").formatted(color));
            first = false;
            if (code) {
                chunk(out, line, Formatting.GRAY, false, false, false, true);
            } else {
                appendLine(out, line, color);
            }
        }
        return out;
    }

    private static String clean(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll("(?s)\\$\\$(.+?)\\$\\$", "$1");
        text = text.replaceAll("(?s)\\\\\\[(.+?)\\\\\\]", "$1");
        text = text.replaceAll("(?s)\\\\\\((.+?)\\\\\\)", "$1");
        text = text.replaceAll("(?<!\\\\)\\$([^$\\n]+)\\$", "$1");
        text = text.replaceAll("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}", "$1/$2");
        text = text.replaceAll("\\\\[a-zA-Z]+\\{([^{}]*)\\}", "$1");
        text = text.replaceAll("\\\\[a-zA-Z]+", "");
        text = text.replaceAll("!\\[([^\\]]*)\\]\\([^)\\n]*\\)", "$1");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p\\s*>", "\n");
        text = text.replaceAll("(?i)</?(?:b|i|em|strong|code|pre|p|div|span|ul|ol|li|h[1-6])\\b[^>]*>", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text;
    }

    private static void appendLine(MutableText out, String line, Formatting color) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) return;
        Matcher heading = HEADING.matcher(trimmed);
        if (heading.matches()) {
            Formatting headingColor = color == Formatting.WHITE ? Formatting.GOLD : color;
            appendInline(out, heading.group(1), headingColor, true, false, false, false);
            return;
        }
        Matcher quote = QUOTE.matcher(trimmed);
        if (quote.matches()) {
            appendInline(out, quote.group(1), Formatting.GRAY, false, true, false, false);
            return;
        }
        String body = line.stripLeading();
        Matcher ordered = ORDERED.matcher(body);
        if (ordered.matches()) {
            chunk(out, ordered.group(1) + ". ", color, false, false, false, false);
            appendInline(out, ordered.group(2), color, false, false, false, false);
            return;
        }
        Matcher bullet = BULLET.matcher(body);
        if (bullet.matches()) {
            chunk(out, "• ", color, false, false, false, false);
            appendInline(out, bullet.group(1), color, false, false, false, false);
            return;
        }
        appendInline(out, line.stripTrailing(), color, false, false, false, false);
    }

    private static void appendInline(MutableText out, String text, Formatting color,
                                      boolean bold, boolean italic, boolean strike, boolean code) {
        int index = 0;
        StringBuilder plain = new StringBuilder();
        while (index < text.length()) {
            if (!code && text.startsWith("**", index)) {
                flush(out, plain, color, bold, italic, strike, code);
                int close = text.indexOf("**", index + 2);
                if (close < 0) {
                    appendInline(out, text.substring(index + 2), color, true, italic, strike, false);
                    return;
                }
                appendInline(out, text.substring(index + 2, close), color, true, italic, strike, false);
                index = close + 2;
                continue;
            }
            if (!code && text.startsWith("__", index)) {
                flush(out, plain, color, bold, italic, strike, code);
                int close = text.indexOf("__", index + 2);
                if (close < 0) {
                    appendInline(out, text.substring(index + 2), color, true, italic, strike, false);
                    return;
                }
                appendInline(out, text.substring(index + 2, close), color, true, italic, strike, false);
                index = close + 2;
                continue;
            }
            if (!code && text.startsWith("~~", index)) {
                flush(out, plain, color, bold, italic, strike, code);
                int close = text.indexOf("~~", index + 2);
                if (close < 0) {
                    appendInline(out, text.substring(index + 2), color, bold, italic, true, false);
                    return;
                }
                appendInline(out, text.substring(index + 2, close), color, bold, italic, true, false);
                index = close + 2;
                continue;
            }
            if (!code && text.charAt(index) == '`') {
                flush(out, plain, color, bold, italic, strike, code);
                int close = text.indexOf('`', index + 1);
                if (close < 0) {
                    appendInline(out, text.substring(index + 1), color, false, false, false, true);
                    return;
                }
                chunk(out, text.substring(index + 1, close), color, false, false, false, true);
                index = close + 1;
                continue;
            }
            if (!code && text.charAt(index) == '[') {
                int mid = text.indexOf("](", index + 1);
                int end = mid < 0 ? -1 : text.indexOf(')', mid + 2);
                if (mid > index && end > mid) {
                    flush(out, plain, color, bold, italic, strike, code);
                    String label = text.substring(index + 1, mid);
                    String url = text.substring(mid + 2, end);
                    chunk(out, label.isBlank() ? url : label, color, bold, italic, strike, false);
                    index = end + 1;
                    continue;
                }
            }
            char mark = text.charAt(index);
            if (!code && (mark == '*' || mark == '_') && emphasis(text, index, mark)) {
                flush(out, plain, color, bold, italic, strike, code);
                int close = closeMark(text, index + 1, mark);
                appendInline(out, text.substring(index + 1, close), color, bold, true, strike, false);
                index = close + 1;
                continue;
            }
            plain.append(text.charAt(index));
            index++;
        }
        flush(out, plain, color, bold, italic, strike, code);
    }

    private static boolean emphasis(String text, int at, char mark) {
        if (at + 1 < text.length() && text.charAt(at + 1) == mark) return false;
        if (at + 1 >= text.length() || Character.isWhitespace(text.charAt(at + 1))) return false;
        if (mark == '_' && at > 0 && Character.isLetterOrDigit(text.charAt(at - 1))) return false;
        int close = closeMark(text, at + 1, mark);
        if (close < 0) return false;
        return mark != '_' || close + 1 >= text.length() || !Character.isLetterOrDigit(text.charAt(close + 1));
    }

    private static int closeMark(String text, int from, char mark) {
        for (int index = from; index < text.length(); index++) {
            if (text.charAt(index) != mark) continue;
            if (index + 1 < text.length() && text.charAt(index + 1) == mark) {
                index++;
                continue;
            }
            return index;
        }
        return -1;
    }

    private static void flush(MutableText out, StringBuilder plain, Formatting color,
                              boolean bold, boolean italic, boolean strike, boolean code) {
        if (plain.isEmpty()) return;
        chunk(out, plain.toString(), color, bold, italic, strike, code);
        plain.setLength(0);
    }

    private static void chunk(MutableText out, String text, Formatting color,
                              boolean bold, boolean italic, boolean strike, boolean code) {
        if (text.isEmpty()) return;
        Style style = Style.EMPTY.withColor(code ? Formatting.GRAY : color)
                .withBold(bold)
                .withItalic(italic)
                .withStrikethrough(strike);
        out.append(Text.literal(text).setStyle(style));
    }
}
