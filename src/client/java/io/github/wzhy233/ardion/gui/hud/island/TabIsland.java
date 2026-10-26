package io.github.wzhy233.ardion.gui.hud.island;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.setting.ThemeSetting;
import io.github.wzhy233.ardion.irc.IrcNameOverlay;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TabIsland {
    private static final float PADDING_X = 14.0F;
    private static final float PADDING_Y = 12.0F;
    private static final float MIN_WIDTH = 120.0F;
    private static final float MAX_WIDTH_RATIO = 0.92F;
    private static final float MAX_HEIGHT_RATIO = 0.82F;
    private static final float COLUMN_GAP = 8.0F;
    private static final float MIN_COLUMN_WIDTH = 132.0F;
    private static final float MAX_COLUMN_WIDTH = 216.0F;
    private static final float ROW_HEIGHT = 18.0F;
    private static final float META_HEIGHT = 14.0F;
    private static final float HEADER_GAP = 7.0F;
    private static final float HEADER_LINE_GAP = 3.0F;
    private static final float FOOTER_GAP = 8.0F;
    private static final float FOOTER_LINE_GAP = 2.0F;
    private static final float RADIUS = 18.0F;
    private static final int MAX_COLUMNS = 6;

    private Snapshot snapshot;
    private boolean visible;

    public void show(Snapshot snapshot) {
        this.snapshot = snapshot;
        this.visible = snapshot != null;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible && snapshot != null;
    }

    public Layout measure(float screenWidth, float screenHeight) {
        if (!isVisible()) return null;

        Font titleFont = Fonts.getMedium(14.0F);
        Font metaFont = Fonts.getMedium(11.0F);
        Font rowFont = Fonts.getMedium(12.0F);
        Font monoFont = Fonts.getJbMedium(10.0F);
        Font smallFont = Fonts.getMedium(10.0F);

        int total = snapshot.entries().size();
        int rowsSetting = Math.max(1, snapshot.rowsPerColumn());
        float maxWidth = Math.max(MIN_WIDTH, screenWidth * MAX_WIDTH_RATIO);
        float maxHeight = Math.max(82.0F, screenHeight * MAX_HEIGHT_RATIO);
        String countText = total + " online";
        float textWrapWidth = Math.max(1.0F, maxWidth - PADDING_X * 2.0F);
        List<StyledLine> headerLines = styledLines(snapshot.header(), titleFont, textWrapWidth);
        List<StyledLine> footerLines = styledLines(snapshot.footer(), smallFont, textWrapWidth);
        float headerHeight = linesHeight(headerLines, HEADER_LINE_GAP);
        float footerHeight = linesHeight(footerLines, FOOTER_LINE_GAP);
        float hiddenHeight = 0.0F;
        float fixedHeight = PADDING_Y * 2.0F + META_HEIGHT + HEADER_GAP
                + (headerHeight > 0.0F ? headerHeight + HEADER_GAP : 0.0F)
                + (footerHeight > 0.0F ? footerHeight + FOOTER_GAP : 0.0F);
        int maxRowsByHeight = Math.max(1, (int) Math.floor((maxHeight - fixedHeight - hiddenHeight) / ROW_HEIGHT));
        int rowsPerColumn = Math.max(1, Math.min(rowsSetting, maxRowsByHeight));
        int columns = total == 0 ? 1 : (int) Math.ceil(total / (double) rowsPerColumn);
        int maxColumnsByWidth = Math.max(1, Math.min(MAX_COLUMNS,
                (int) Math.floor((maxWidth - PADDING_X * 2.0F + COLUMN_GAP) / (MIN_COLUMN_WIDTH + COLUMN_GAP))));
        columns = Math.max(1, Math.min(columns, maxColumnsByWidth));
        int rows = total == 0 ? 1 : (int) Math.ceil(total / (double) columns);
        if (rows > maxRowsByHeight) {
            rows = maxRowsByHeight;
        }

        int visibleCount = Math.min(total, rows * columns);
        int hiddenCount = Math.max(0, total - visibleCount);
        if (hiddenCount > 0) {
            hiddenHeight = ROW_HEIGHT;
            fixedHeight += hiddenHeight;
        }

        float scoreWidth = 0.0F;
        float nameWidth = Skia.getTextBounds("Player", rowFont).getWidth();
        for (int i = 0; i < visibleCount; i++) {
            Entry entry = snapshot.entries().get(i);
            nameWidth = Math.max(nameWidth, measureText(entry.name(), rowFont));
            if (!textBlank(entry.score())) {
                scoreWidth = Math.max(scoreWidth, measureText(entry.score(), monoFont));
            }
        }

        float naturalColumnWidth = nameWidth + 58.0F + (scoreWidth > 0.0F ? scoreWidth + 8.0F : 0.0F);
        float columnWidth = Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, naturalColumnWidth));
        float availableColumnsWidth = maxWidth - PADDING_X * 2.0F - Math.max(0, columns - 1) * COLUMN_GAP;
        columnWidth = Math.min(columnWidth, availableColumnsWidth / columns);
        columnWidth = Math.max(84.0F, columnWidth);

        float contentWidth = columns * columnWidth + Math.max(0, columns - 1) * COLUMN_GAP;
        float titleRowWidth = Skia.getTextBounds("Players", titleFont).getWidth()
                + 10.0F
                + Skia.getTextBounds(countText, metaFont).getWidth();
        float headerWidth = Math.max(maxLineWidth(headerLines), maxLineWidth(footerLines));
        float naturalWidth = Math.max(contentWidth, Math.max(titleRowWidth, Math.min(headerWidth, maxWidth - PADDING_X * 2.0F)));
        float width = Math.max(MIN_WIDTH, naturalWidth + PADDING_X * 2.0F);
        width = Math.min(width, maxWidth);
        float height = fixedHeight + rows * ROW_HEIGHT;
        height = Math.min(height, maxHeight);

        return new Layout(width, height, RADIUS, columns, rows, visibleCount, hiddenCount, columnWidth,
                headerHeight, footerHeight, headerLines, footerLines, titleFont, metaFont, rowFont, monoFont, smallFont);
    }

    public void render(Layout layout, float x, float y, float width, float height) {
        if (layout == null || snapshot == null) return;

        float tableWidth = layout.columns() * layout.columnWidth() + Math.max(0, layout.columns() - 1) * COLUMN_GAP;
        float contentX = x + Math.max(PADDING_X, (width - tableWidth) / 2.0F);
        float cursorY = y + PADDING_Y;
        Color accent = new Color(ThemeSetting.getColor((int) (System.currentTimeMillis() * 0.01F)), true);

        Paint paint = Skia.scratchPaint();
        paint.setColor(accent.getRGB());
        paint.setAlphaf(0.98F);
        Skia.drawText("Players", contentX, cursorY, layout.titleFont(), paint);

        String count = snapshot.entries().size() + " online";
        Rect countBounds = Skia.getTextBounds(count, layout.metaFont());
        paint = Skia.scratchPaint();
        paint.setColor(new Color(255, 255, 255, 172).getRGB());
        paint.setAlphaf(0.96F);
        Skia.drawText(count, x + width - PADDING_X - countBounds.getWidth(), cursorY + 2.0F, layout.metaFont(), paint);
        cursorY += META_HEIGHT + HEADER_GAP;

        if (!layout.headerLines().isEmpty() && layout.headerHeight() > 0.0F) {
            cursorY = drawCenteredLines(layout.headerLines(), x, cursorY, width, layout.titleFont(),
                    new Color(255, 255, 255, 222), HEADER_LINE_GAP);
            cursorY += HEADER_GAP;
        }

        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                int index = column * layout.rows() + row;
                if (index >= layout.visibleCount()) continue;
                float columnX = contentX + column * (layout.columnWidth() + COLUMN_GAP);
                Entry entry = snapshot.entries().get(index);
                renderEntry(entry, columnX, cursorY + row * ROW_HEIGHT, layout.columnWidth(), layout.rowFont(), layout.monoFont());
            }
        }
        cursorY += layout.rows() * ROW_HEIGHT;

        if (layout.hiddenCount() > 0) {
            drawCenteredLine("+" + layout.hiddenCount() + " more", x, cursorY + 1.0F, width, layout.smallFont(),
                    new Color(255, 255, 255, 135));
            cursorY += ROW_HEIGHT;
        }

        if (!layout.footerLines().isEmpty() && layout.footerHeight() > 0.0F && cursorY + FOOTER_GAP < y + height) {
            drawCenteredLines(layout.footerLines(), x, cursorY + FOOTER_GAP, width, layout.smallFont(),
                    new Color(255, 255, 255, 160), FOOTER_LINE_GAP);
        }
    }

    private void renderEntry(Entry entry, float x, float y, float width, Font rowFont, Font monoFont) {
        float alpha = entry.spectator() ? 0.48F : 0.94F;

        String latency = entry.latency() < 0 ? "--" : String.valueOf(entry.latency());
        Rect latencyBounds = Skia.getTextBounds(latency, monoFont);
        float latencyX = x + width - latencyBounds.getWidth();
        Paint paint = Skia.scratchPaint();
        paint.setColor(new Color(255, 255, 255, 145).getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(latency, latencyX, y + 4.0F, monoFont, paint);

        float scoreRight = latencyX - 8.0F;
        if (!textBlank(entry.score())) {
            float scoreWidth = measureText(entry.score(), monoFont);
            paint = Skia.scratchPaint();
            Color scoreFallback = entry.heartScore() ? new Color(255, 97, 112) : new Color(255, 221, 92);
            paint.setColor(scoreFallback.getRGB());
            paint.setAlphaf(alpha);
            drawStyledText(entry.score(), scoreRight - scoreWidth, y + 4.0F, monoFont,
                    scoreFallback.getRGB(), alpha, scoreWidth);
            scoreRight -= scoreWidth + 7.0F;
        }

        float nameX = x;
        float nameWidth = Math.max(0.0F, scoreRight - nameX);
        Color fallback = entry.spectator() ? new Color(170, 170, 170) : Color.WHITE;
        drawStyledText(entry.name(), nameX, y + 3.0F, rowFont, fallback.getRGB(), alpha, nameWidth);
    }

    private void drawCenteredLine(String text, float x, float y, float width, Font font, Color color) {
        String limited = Skia.getLimitTextWithDot(text, font, Math.max(0.0F, width - PADDING_X * 2.0F));
        Rect bounds = Skia.getTextBounds(limited, font);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color.getRGB());
        paint.setAlphaf(color.getAlpha() / 255.0F);
        Skia.drawText(limited, x + (width - bounds.getWidth()) / 2.0F, y, font, paint);
    }

    private float drawCenteredLines(List<StyledLine> lines, float x, float y, float width, Font font, Color fallback,
                                    float lineGap) {
        float maxTextWidth = Math.max(0.0F, width - PADDING_X * 2.0F);
        float contentX = x + PADDING_X;
        float cursorY = y;
        for (StyledLine line : lines) {
            float lineWidth = Math.min(maxTextWidth, measureRuns(line.runs(), font));
            float textX = contentX + Math.max(0.0F, (maxTextWidth - lineWidth) / 2.0F);
            drawStyledRuns(line.runs(), textX, cursorY, font, fallback.getRGB(), 1.0F, maxTextWidth,
                    contentX, maxTextWidth);
            cursorY += line.height() + lineGap;
        }
        return cursorY - (lines.isEmpty() ? 0.0F : lineGap);
    }

    private List<StyledLine> styledLines(Text text, Font font, float maxWidth) {
        if (textBlank(text)) return List.of();

        List<StyledLine> lines = new ArrayList<>();
        List<StyledRun> currentRuns = new ArrayList<>();
        float lineHeight = lineHeight(font);
        float[] currentWidth = {0.0F};
        for (StyledRun run : styledRuns(text)) {
            String value = run.text();
            for (int offset = 0; offset < value.length(); ) {
                int codePoint = value.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (codePoint == '\n' || codePoint == '\r') {
                    addLine(lines, currentRuns, currentWidth[0], lineHeight);
                    currentRuns.clear();
                    currentWidth[0] = 0.0F;
                    continue;
                }

                String piece = new String(Character.toChars(codePoint));
                float pieceWidth = glyphWidth(piece, font);
                if (!currentRuns.isEmpty() && currentWidth[0] + pieceWidth > maxWidth) {
                    addLine(lines, currentRuns, currentWidth[0], lineHeight);
                    currentRuns.clear();
                    currentWidth[0] = 0.0F;
                }
                addRun(currentRuns, new StyledRun(piece, run.style()));
                currentWidth[0] += pieceWidth;
            }
        }
        addLine(lines, currentRuns, currentWidth[0], lineHeight);
        return lines;
    }

    private void addLine(List<StyledLine> lines, List<StyledRun> runs, float width, float height) {
        if (runs.isEmpty() || width <= 0.0F) return;
        lines.add(new StyledLine(List.copyOf(runs), width, height));
    }

    private void drawStyledText(Text text, float x, float y, Font font, int fallbackColor, float alpha, float maxWidth) {
        drawStyledRuns(styledRuns(text), x, y, font, fallbackColor, alpha, maxWidth);
    }

    private void drawStyledRuns(List<StyledRun> runs, float x, float y, Font font, int fallbackColor, float alpha, float maxWidth) {
        drawStyledRuns(runs, x, y, font, fallbackColor, alpha, maxWidth, x, maxWidth);
    }

    private void drawStyledRuns(List<StyledRun> runs, float x, float y, Font font, int fallbackColor, float alpha,
                                float maxWidth, float clipX, float clipWidth) {
        if (runs == null || runs.isEmpty() || alpha <= 0.0F || maxWidth <= 0.0F) return;

        Skia.save();
        Skia.getCanvas().clipRect(Rect.makeXYWH(clipX, y, clipWidth, lineHeight(font)), ClipMode.INTERSECT, true);
        float cursorX = x;
        float clipRight = clipX + clipWidth;
        for (StyledRun run : runs) {
            String text = run.text();
            if (text.isEmpty()) continue;
            int leadingEnd = leadingWhitespaceEnd(text);
            int trailingStart = trailingWhitespaceStart(text, leadingEnd);
            float leadingWidth = whitespaceWidth(text, 0, leadingEnd, font);
            float trailingWidth = whitespaceWidth(text, trailingStart, text.length(), font);
            String body = text.substring(leadingEnd, trailingStart);
            float bodyWidth = body.isEmpty() ? 0.0F : Skia.getTextBounds(body, font).getWidth();
            float width = leadingWidth + bodyWidth + trailingWidth;
            Paint paint = Skia.scratchPaint();
            int color = styleColor(run.style(), fallbackColor, alpha);
            applyStylePaint(paint, color);
            if (!body.isEmpty()) {
                Skia.drawText(body, cursorX + leadingWidth, y, font, paint);
                drawStyleDecorations(run.style(), cursorX + leadingWidth, y, bodyWidth, font, color);
            }
            cursorX += width;
            if (cursorX >= clipRight) break;
        }
        Skia.restore();
    }

    private void applyStylePaint(Paint paint, int color) {
        paint.setARGB((color >>> 24) & 0xFF, (color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF);
    }

    private void drawStyleDecorations(Style style, float x, float y, float width, Font font, int color) {
        if (style == null || width <= 0.0F || (!style.isUnderlined() && !style.isStrikethrough())) return;

        Paint paint = Skia.scratchPaint();
        paint.setColor(color);
        paint.setStroke(true);
        paint.setStrokeWidth(1.0F);

        float height = lineHeight(font);
        if (style.isStrikethrough()) {
            float lineY = y + height * 0.58F;
            Skia.getCanvas().drawLine(x, lineY, x + width, lineY, paint);
        }
        if (style.isUnderlined()) {
            float lineY = y + height - 1.5F;
            Skia.getCanvas().drawLine(x, lineY, x + width, lineY, paint);
        }
    }

    private int styleColor(Style style, int fallbackColor, float alpha) {
        TextColor textColor = style == null ? null : style.getColor();
        int rgb = textColor == null ? fallbackColor & 0x00FFFFFF : textColor.getRgb() & 0x00FFFFFF;
        int fallbackAlpha = (fallbackColor >>> 24) & 0xFF;
        int finalAlpha = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * fallbackAlpha);
        return (finalAlpha << 24) | rgb;
    }

    private float measureText(Text text, Font font) {
        return measureRuns(styledRuns(text), font);
    }

    private float measureRuns(List<StyledRun> runs, Font font) {
        float width = 0.0F;
        for (StyledRun run : runs) {
            width += runWidth(run.text(), font);
        }
        return width;
    }

    private float runWidth(String text, Font font) {
        if (text == null || text.isEmpty()) return 0.0F;
        int leadingEnd = leadingWhitespaceEnd(text);
        int trailingStart = trailingWhitespaceStart(text, leadingEnd);
        float width = whitespaceWidth(text, 0, leadingEnd, font);
        if (leadingEnd < trailingStart) {
            width += Skia.getTextBounds(text.substring(leadingEnd, trailingStart), font).getWidth();
        }
        return width + whitespaceWidth(text, trailingStart, text.length(), font);
    }

    private float glyphWidth(String text, Font font) {
        if (text == null || text.isEmpty()) return 0.0F;
        float width = Skia.getTextBounds(text, font).getWidth();
        if (width <= 0.0F && text.isBlank()) {
            width = Math.max(3.0F, Skia.getTextBounds("n", font).getWidth() * 0.35F);
        }
        return width;
    }

    private int leadingWhitespaceEnd(String text) {
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            if (!isSpacingCodePoint(codePoint)) break;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    private int trailingWhitespaceStart(String text, int minOffset) {
        int offset = text.length();
        while (offset > minOffset) {
            int codePoint = text.codePointBefore(offset);
            if (!isSpacingCodePoint(codePoint)) break;
            offset -= Character.charCount(codePoint);
        }
        return offset;
    }

    private float whitespaceWidth(String text, int start, int end, Font font) {
        if (start >= end) return 0.0F;
        float width = 0.0F;
        for (int offset = start; offset < end; ) {
            int codePoint = text.codePointAt(offset);
            String piece = new String(Character.toChars(codePoint));
            float pieceWidth = Skia.getTextBounds(piece, font).getWidth();
            width += pieceWidth > 0.0F ? pieceWidth : spaceWidth(font);
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private boolean isSpacingCodePoint(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private float spaceWidth(Font font) {
        return Math.max(3.0F, Skia.getTextBounds("n", font).getWidth() * 0.35F);
    }

    private List<StyledRun> styledRuns(Text text) {
        if (text == null) return List.of();
        text = IrcNameOverlay.rewrite(text);

        List<StyledRun> runs = new ArrayList<>();
        text.visit((style, string) -> {
            if (string != null && !string.isEmpty()) {
                addRun(runs, new StyledRun(sanitizeStyledFragment(string), style == null ? Style.EMPTY : style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private void addRun(List<StyledRun> runs, StyledRun run) {
        if (run.text().isEmpty()) return;
        if (!runs.isEmpty()) {
            StyledRun last = runs.get(runs.size() - 1);
            if (last.style().equals(run.style())) {
                runs.set(runs.size() - 1, new StyledRun(last.text() + run.text(), last.style()));
                return;
            }
        }
        runs.add(run);
    }

    private String sanitizeStyledFragment(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00A7') {
                if (i + 1 < text.length()) i++;
                continue;
            }
            builder.append(Character.isSurrogate(ch) ? '?' : ch);
        }
        return builder.toString();
    }

    private float linesHeight(List<StyledLine> lines, float lineGap) {
        if (lines.isEmpty()) return 0.0F;
        float height = 0.0F;
        for (int i = 0; i < lines.size(); i++) {
            height += lines.get(i).height();
            if (i + 1 < lines.size()) height += lineGap;
        }
        return height;
    }

    private float maxLineWidth(List<StyledLine> lines) {
        float width = 0.0F;
        for (StyledLine line : lines) {
            width = Math.max(width, line.width());
        }
        return width;
    }

    private float lineHeight(Font font) {
        FontMetrics metrics = font.getMetrics();
        return Math.max(1.0F, metrics.getDescent() - metrics.getAscent());
    }

    private boolean textBlank(Text text) {
        return text == null || Skia.sanitizeText(text.getString()).isBlank();
    }

    public static record Snapshot(List<Entry> entries, Text header, Text footer, int rowsPerColumn) {
        public Snapshot {
            entries = List.copyOf(entries == null ? List.of() : entries);
            header = header == null ? Text.empty() : header;
            footer = footer == null ? Text.empty() : footer;
            rowsPerColumn = Math.max(1, rowsPerColumn);
        }
    }

    public static record Entry(Text name, int latency, boolean spectator, Text score, boolean heartScore, int nameColor) {
        public Entry {
            name = name == null || Skia.sanitizeText(name.getString()).isBlank() ? Text.literal("Player") : name;
            score = score == null ? Text.empty() : score;
            nameColor = nameColor == 0 ? 0xFFFFFF : nameColor;
        }
    }

    public static record Layout(float width, float height, float radius, int columns, int rows, int visibleCount,
                         int hiddenCount, float columnWidth, float headerHeight, float footerHeight,
                         List<StyledLine> headerLines, List<StyledLine> footerLines,
                         Font titleFont, Font metaFont, Font rowFont, Font monoFont, Font smallFont) {
    }

    public record StyledLine(List<StyledRun> runs, float width, float height) {
    }

    private record StyledRun(String text, Style style) {
    }
}
