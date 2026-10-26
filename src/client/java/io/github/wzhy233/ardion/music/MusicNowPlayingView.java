package io.github.wzhy233.ardion.music;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.wzhy233.ardion.music.lyric.LyricLine;
import io.github.wzhy233.ardion.music.lyric.LyricParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MusicNowPlayingView implements AutoCloseable {
    public record Box(float x, float y, float w, float h) {
        public boolean contains(float mx, float my) { return mx >= x && my >= y && mx <= x + w && my <= y + h; }
        public float cx() { return x + w / 2; }
        public float cy() { return y + h / 2; }
        public Rect rect() { return Rect.makeXYWH(x, y, w, h); }
    }
    public record LyricHit(Box bounds, LyricLine line) {}
    public record Layout(Box close, Box previous, Box play, Box next, Box progress, Box volume,
                         Box like, Box lyrics, Box follow, List<LyricHit> hits) {}
    public record State(long trackId, String title, String artist, String album, Image cover, MusicLyrics lyrics,
                        long position, long duration, boolean playing, boolean loading, float volume,
                        boolean canLike, boolean liked, boolean seeking, boolean changingVolume) {}

    private static final Box EMPTY = new Box(0, 0, 0, 0);
    private final Function<Float, Font> regular;
    private final Function<Float, Font> medium;
    private final MusicLikeButton likeButton;
    private final Paint paint = new Paint();
    private final ImageFilter artworkShadow = ImageFilter.makeDropShadowOnly(0, 12, 14, 14, 0x65000000);
    private final MaskFilter lyricBlur = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 0.7F);
    private final MusicMotion scroll = new MusicMotion(0);
    private final MusicMotion coverScale = new MusicMotion(1);
    private final io.github.wzhy233.ardion.gui.ui.UiState ui = new io.github.wzhy233.ardion.gui.ui.UiState();
    private final List<Block> blocks = new ArrayList<>();
    private List<LyricLine> laidOut = List.of();
    private long trackId = Long.MIN_VALUE;
    private float layoutWidth, layoutSize, totalHeight, targetScroll;
    private float manualRemaining, elapsed, dt;
    private Image sourceCover, backdrop, previousBackdrop;
    private float backdropBlend = 1;
    private boolean closed;

    private static final class Block {
        final LyricLine line;
        final float top, height;
        final List<MusicLyricLayout.Segment> rows;
        final MusicMotion focus = new MusicMotion(0);
        final MusicMotion hover = new MusicMotion(0);
        final MusicMotion position;
        Block(LyricLine line, float top, float height, List<MusicLyricLayout.Segment> rows) {
            this.line = line; this.top = top; this.height = height; this.rows = rows;
            this.position = new MusicMotion(top);
        }
    }

    public MusicNowPlayingView(Function<Float, Font> regular, Function<Float, Font> medium, MusicLikeButton likeButton) {
        this.regular = regular;
        this.medium = medium;
        this.likeButton = likeButton;
    }

    public void follow() { manualRemaining = 0; }

    public void scroll(double amount) {
        if (blocks.isEmpty()) return;
        if (manualRemaining <= 0) targetScroll = scroll.value();
        targetScroll = clamp(targetScroll - (float) amount * 58, 0, Math.max(0, totalHeight - 36));
        manualRemaining = 6;
    }

    public Layout render(Canvas canvas, float w, float h, float mx, float my, float seconds, State state) {
        int save = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(0, 0, w, h));
            return renderPage(canvas, w, h, mx, my, seconds, state);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private Layout renderPage(Canvas canvas, float w, float h, float mx, float my, float seconds, State state) {
        dt = clamp(seconds, 0, 0.1F);
        if (state.playing || state.loading) elapsed += dt;
        manualRemaining = Math.max(0, manualRemaining - dt);
        if (trackId != state.trackId) {
            trackId = state.trackId;
            laidOut = List.of();
            blocks.clear();
            scroll.snap(0);
            targetScroll = manualRemaining = totalHeight = 0;
        }
        background(canvas, w, h, state.cover);
        Box close = new Box(w - 56, 14, 36, 36);
        button(canvas, close, "close", "down", mx, my, false);
        text(canvas, "NOW PLAYING", 28, 25, 10, 0xA8FFFFFF, true);

        boolean split = w >= 600 && h >= 400;
        boolean compact = !split;
        float margin = clamp(w * 0.055F, 26, 58);
        float leftW = split ? w * 0.43F : w;
        float blockW = split ? Math.min(310, leftW - margin - 40) : w - 56;
        boolean shortWindow = compact && h < 330;
        float coverSize = split ? Math.max(70, Math.min(blockW, h - 264)) : shortWindow ? 42 : 56;
        if (split) {
            blockW = Math.max(180, coverSize);
            margin = (leftW - blockW) / 2;
        }
        float coverX = split ? (leftW - coverSize) / 2 : 28;
        float coverY = split ? Math.max(52, (h - coverSize - 210) / 2) : shortWindow ? 52 : 58;
        float scale = coverScale.step(state.playing ? 1 : 0.94F, dt, 12);
        artwork(canvas, state.cover, coverX + coverSize * (1 - scale) / 2,
                coverY + coverSize * (1 - scale) / 2, coverSize * scale);
        float titleX = split ? margin : shortWindow ? 86 : 100;
        float titleY = split ? coverY + coverSize + 19 : shortWindow ? 50 : 61;
        float titleW = split ? blockW - (state.canLike ? 34 : 0) : w - 132;
        text(canvas, limited(state.title.isBlank() ? "Choose a song" : state.title, titleW, 18, true),
                titleX, titleY, 18, 0xFFFFFFFF, true);
        text(canvas, limited(state.artist.isBlank() ? "Your music, your moment" : state.artist, titleW, 12, false),
                titleX, titleY + 27, 12, 0xABFFFFFF, false);

        Box progress = new Box(split ? margin : 28, split ? titleY + 61 : h - 78, blockW, 4);
        float playY = split ? progress.y + 48 : h - 39;
        float playX = split ? margin + blockW / 2 : w / 2;
        Box previous = new Box(playX - 78, playY - 22, 44, 44);
        Box play = new Box(playX - 26, playY - 26, 52, 52);
        Box next = new Box(playX + 34, playY - 22, 44, 44);
        Box volume = split ? new Box(margin + 23, playY + 43, blockW - 46, 12)
                : w >= 380 ? new Box(w - 85, playY - 6, 57, 12) : EMPTY;
        Box like = state.canLike ? new Box(split ? margin + blockW - 30 : 22, split ? titleY + 8 : playY - 15, 30, 30) : EMPTY;
        float lyricTop = shortWindow ? 106 : 132;
        Box lyrics = split ? new Box(leftW + 20, 72, w - leftW - Math.max(32, w * 0.05F) - 20, h - 112)
                : new Box(28, lyricTop, w - 56, Math.max(32, progress.y - 18 - lyricTop));
        List<LyricHit> hits = new ArrayList<>();
        lyricColumn(canvas, lyrics, mx, my, state, compact, hits);
        Box follow = manualRemaining > 0 ? new Box(w - 170, 18, 100, 28) : EMPTY;
        if (follow.w > 0) {
            float over = hover("follow", follow.contains(mx, my));
            round(canvas, follow, 14, white(0.08F + over * 0.06F));
            centered(canvas, "Resume lyrics", follow, 9, 0xC8FFFFFF, true);
        }

        float progressHover = hover("progress", state.seeking || new Box(progress.x, progress.y - 9, progress.w, 24).contains(mx, my));
        slider(canvas, progress, state.duration > 0 ? state.position / (float) state.duration : 0, progressHover);
        text(canvas, time(state.position), progress.x, progress.y + 10, 10, 0x8FFFFFFF, false);
        String remaining = "−" + time(Math.max(0, state.duration - state.position));
        text(canvas, remaining, progress.x + progress.w - width(remaining, 10, false), progress.y + 10, 10, 0x8FFFFFFF, false);
        button(canvas, previous, "previous", "previous", mx, my, false);
        button(canvas, play, "play", state.loading ? "loading" : state.playing ? "pause" : "play", mx, my, false);
        button(canvas, next, "next", "next", mx, my, false);
        if (volume.w > 0) {
            float vHover = hover("volume", state.changingVolume || new Box(volume.x - 20, volume.y - 8, volume.w + 40, 28).contains(mx, my));
            speaker(canvas, volume.x - 16, volume.cy(), 0x88FFFFFF);
            slider(canvas, new Box(volume.x, volume.cy() - 1.5F, volume.w, 3), state.volume, vHover);
            speaker(canvas, volume.x + volume.w + 16, volume.cy(), 0xC0FFFFFF);
        }
        if (like.w > 0) {
            likeButton.draw(canvas, like.cx(), like.cy(), like.w / 2,
                    like.contains(mx, my), state.liked, state.trackId);
        }
        return new Layout(close, previous, play, next, progress, volume, like, lyrics, follow, List.copyOf(hits));
    }

    private void lyricColumn(Canvas canvas, Box area, float mx, float my, State state, boolean compact, List<LyricHit> hits) {
        List<LyricLine> lines = state.lyrics.lines();
        boolean shortColumn = area.h < 110;
        float size = shortColumn ? 17 : compact ? 21 : area.w >= 410 ? 29 : 24;
        float leading = size * 1.48F;
        float gutter = compact ? 6 : 16;
        Font font = medium.apply(size);
        boolean rebuilt = laidOut != lines || layoutWidth != area.w || layoutSize != size;
        if (rebuilt) {
            laidOut = lines; layoutWidth = area.w; layoutSize = size;
            blocks.clear(); totalHeight = 0;
            for (LyricLine line : lines) {
                var rows = MusicLyricLayout.wrap(line.text(), area.w - gutter - 16, font::measureTextWidth);
                float height = rows.size() * leading + (shortColumn ? 15 : 32);
                blocks.add(new Block(line, totalHeight, height, rows));
                totalHeight += height;
            }
        }
        if (blocks.isEmpty()) {
            emptyLyrics(canvas, area, state);
            return;
        }
        LyricLine active = LyricParser.currentLine(lines, state.position);
        if (manualRemaining > 0 && area.contains(mx, my)) manualRemaining = Math.max(manualRemaining, 1.5F);
        if (manualRemaining <= 0) {
            targetScroll = 0;
            for (Block block : blocks) if (block.line == active) { targetScroll = block.top; break; }
        }
        if (rebuilt) {
            scroll.snap(targetScroll);
            for (Block block : blocks) block.position.snap(block.top - targetScroll);
        }
        float offset = scroll.step(targetScroll, dt, 20);
        float anchor = area.y + (shortColumn ? 1 : area.h * 0.30F);
        canvas.save();
        canvas.clipRect(Rect.makeXYWH(area.x, area.y, area.w, Math.max(1, area.h)));
        for (Block block : blocks) {
            float distance = Math.max(0, (block.top - targetScroll) / (leading + 32));
            float linePosition;
            if (manualRemaining > 0) {
                linePosition = block.top - offset;
                block.position.snap(linePosition);
            } else {
                linePosition = block.position.step(block.top - targetScroll, dt, 14 - Math.min(4, distance) * 0.7F);
            }
            float top = anchor + linePosition;
            boolean current = block.line == active;
            float focus = block.focus.step(current ? 1 : 0, dt, 14);
            if (top + block.height < area.y || top > area.y + area.h) continue;
            float hitTop = Math.max(area.y, top - 5);
            float hitBottom = Math.min(area.y + area.h, top + block.rows.size() * leading + 4);
            Box hit = new Box(area.x, hitTop, area.w, Math.max(0, hitBottom - hitTop));
            boolean over = hit.contains(mx, my) && hit.h > 0;
            float hover = block.hover.step(over ? 1 : 0, dt, 18);
            float highlight = characterProgress(block.line, state.position);
            canvas.save();
            canvas.translate(area.x + gutter, top);
            for (int row = 0; row < block.rows.size(); row++) {
                var segment = block.rows.get(row);
                float rowY = row * leading;
                float center = top + rowY + size / 2;
                float edge = shortColumn ? 1 : clamp(Math.min((center - area.y) / 42, (area.y + area.h - center) / 52), 0, 1);
                float alpha = (0.25F + 0.16F * focus + 0.30F * hover) * edge;
                float sharp = Math.max(focus, hover);
                if (manualRemaining > 0) sharp = Math.max(sharp, 0.75F);
                if (sharp < 0.999F) lyricGlyphs(canvas, segment.text(), 0, rowY, font, white(alpha * (1 - sharp)), lyricBlur);
                if (sharp > 0.001F) glyphs(canvas, segment.text(), 0, rowY, font, white(alpha * sharp));
                if (current) {
                    float fill = block.line.words().isEmpty() ? area.w : highlightWidth(segment, highlight, font);
                    if (fill > 0) {
                        canvas.save();
                        canvas.clipRect(Rect.makeXYWH(0, rowY - 4, fill, leading + 8));
                        glyphs(canvas, segment.text(), 0, rowY, font, white(edge));
                        canvas.restore();
                    }
                }
            }
            canvas.restore();
            if (hit.h > 0) hits.add(new LyricHit(hit, block.line));
        }
        canvas.restore();
        if (!shortColumn && manualRemaining <= 0 && offset < 1 && active == null && lines.getFirst().startMs() > 1500) {
            dots(canvas, area.x + gutter + 5, anchor - 27, state.playing);
        }
    }

    private void emptyLyrics(Canvas canvas, Box area, State state) {
        MusicLyrics.State status = state.lyrics.state();
        float cy = area.cy() - 35;
        if (area.h < 110) {
            centered(canvas, status == MusicLyrics.State.INSTRUMENTAL ? "Instrumental · Just the music" : stateLabel(status), area, 14, 0xB8FFFFFF, true);
            return;
        }
        if (status == MusicLyrics.State.INSTRUMENTAL) {
            for (int i = 0; i < 7; i++) {
                float wave = (float) Math.sin(elapsed * 1.7F + i * 0.8F);
                float bar = 12 + (1 - Math.abs(i - 3) / 4F) * 18 + (state.playing ? wave * 6 : 0);
                round(canvas, new Box(area.cx() - 34 + i * 11, cy - bar / 2, 4, bar), 2, 0x9EFFFFFF);
            }
        } else if (status == MusicLyrics.State.LOADING) dots(canvas, area.cx() - 12, cy, true);
        else {
            round(canvas, new Box(area.cx() - 21, cy - 17, 42, 30), 7, 0x13FFFFFF);
            round(canvas, new Box(area.cx() - 11, cy - 6, 22, 2), 1, 0x70FFFFFF);
            round(canvas, new Box(area.cx() - 11, cy + 1, 14, 2), 1, 0x70FFFFFF);
        }
        centered(canvas, stateLabel(status), new Box(area.x, cy + 29, area.w, 32), 22, 0xD9FFFFFF, true);
        String hint = switch (status) {
            case INSTRUMENTAL -> "No lyrics. Just the music.";
            case LOADING -> "Finding the words to your music";
            case ERROR -> "Lyrics couldn't be loaded. Keep listening.";
            case IDLE -> "Choose something you love";
            default -> "Enjoy the music while the words are away";
        };
        centered(canvas, limited(hint, area.w - 16, 11, false), new Box(area.x, cy + 67, area.w, 20), 11, 0x70FFFFFF, false);
    }

    private static String stateLabel(MusicLyrics.State state) {
        return switch (state) {
            case INSTRUMENTAL -> "Instrumental";
            case LOADING -> "Loading lyrics";
            case ERROR -> "Lyrics unavailable";
            case IDLE -> "Your next favorite awaits";
            default -> "No lyrics for this song";
        };
    }

    private void background(Canvas canvas, float w, float h, Image cover) {
        if (cover != sourceCover) {
            sourceCover = cover;
            if (previousBackdrop != null) previousBackdrop.close();
            previousBackdrop = backdrop;
            backdrop = null;
            if (cover != null) {
                try (Surface small = Surface.makeRasterN32Premul(96, 96);
                     ImageFilter blur = ImageFilter.makeBlur(14, 14, FilterTileMode.CLAMP);
                     Paint blurred = new Paint().setImageFilter(blur)) {
                    small.getCanvas().drawImageRect(cover, Rect.makeXYWH(-24, -24, 144, 144), blurred);
                    backdrop = small.makeImageSnapshot();
                }
            }
            backdropBlend = previousBackdrop == null ? 1 : 0;
        }
        canvas.drawRect(Rect.makeXYWH(0, 0, w, h), ink(0xFF24232C));
        backdropBlend = Math.min(1, backdropBlend + dt * 1.8F);
        if (previousBackdrop != null) backgroundImage(canvas, previousBackdrop, w, h, 1);
        if (backdrop != null) backgroundImage(canvas, backdrop, w, h, backdropBlend);
        if (backdrop == null && previousBackdrop != null) canvas.drawRect(Rect.makeXYWH(0, 0, w, h), ink((Math.round(backdropBlend * 255) << 24) | 0x24232C));
        if (backdropBlend >= 1 && previousBackdrop != null) { previousBackdrop.close(); previousBackdrop = null; }
        try (Shader shader = Shader.makeLinearGradient(0, 0, w, h,
                new int[]{0x690C101B, 0x830E111D, 0xC5090C14}, new float[]{0, 0.6F, 1})) {
            canvas.drawRect(Rect.makeXYWH(0, 0, w, h), ink(0xFFFFFFFF).setShader(shader));
        }
    }

    private void backgroundImage(Canvas canvas, Image image, float w, float h, float alpha) {
        float side = Math.max(w, h) * 1.35F;
        canvas.save();
        canvas.translate(w / 2 + (float) Math.sin(elapsed * 0.055F) * w * 0.06F, h / 2 + (float) Math.cos(elapsed * 0.07F) * h * 0.05F);
        canvas.rotate((float) Math.sin(elapsed * 0.04F) * 12);
        canvas.drawImageRect(image, Rect.makeXYWH(-side / 2, -side / 2, side, side), ink(white(alpha)));
        canvas.restore();
    }

    private void artwork(Canvas canvas, Image image, float x, float y, float size) {
        canvas.drawRRect(RRect.makeXYWH(x, y, size, size, 10), ink(0xFF000000).setImageFilter(artworkShadow));
        canvas.save();
        canvas.clipRRect(RRect.makeXYWH(x, y, size, size, 9), true);
        if (image != null) canvas.drawImageRect(image, Rect.makeXYWH(x, y, size, size));
        else {
            try (Shader shader = Shader.makeLinearGradient(x, y, x + size, y + size, new int[]{0xFF6A5A71, 0xFF343D53})) {
                canvas.drawRect(Rect.makeXYWH(x, y, size, size), ink(0xFFFFFFFF).setShader(shader));
            }
            float c = size / 2;
            canvas.drawCircle(x + c, y + c, size * 0.24F, ink(0x28FFFFFF));
            canvas.drawCircle(x + c, y + c, size * 0.065F, ink(0x80FFFFFF));
        }
        canvas.restore();
        canvas.drawRRect(RRect.makeXYWH(x + 0.5F, y + 0.5F, size - 1, size - 1, 9), ink(0x20FFFFFF).setStroke(true).setStrokeWidth(1));
    }

    private void button(Canvas canvas, Box bounds, String key, String icon, float mx, float my, boolean selected) {
        float hover = hover(key, bounds.contains(mx, my));
        if (hover > 0.01F) round(canvas, bounds, bounds.w / 2, white(0.09F * hover));
        float size = icon.equals("play") || icon.equals("pause") || icon.equals("loading") ? 30 : icon.equals("down") ? 17 : 23;
        transportIcon(canvas, icon, bounds.cx(), bounds.cy(), size * (1 + hover * 0.07F), selected ? 0xFFFF627D : 0xF2FFFFFF, elapsed);
    }

    public static void transportIcon(Canvas canvas, String icon, float cx, float cy, float size, int color, float phase) {
        try (Paint p = new Paint().setAntiAlias(true).setColor(color); PathBuilder path = new PathBuilder()) {
            canvas.save(); canvas.translate(cx, cy); canvas.scale(size / 24, size / 24);
            switch (icon) {
                case "pause" -> {
                    canvas.drawRRect(RRect.makeXYWH(-8, -10, 5.5F, 20, 1.5F), p);
                    canvas.drawRRect(RRect.makeXYWH(2.5F, -10, 5.5F, 20, 1.5F), p);
                }
                case "play" -> {
                    path.moveTo(-6, -10).lineTo(10, 0).lineTo(-6, 10).closePath(); drawPath(canvas, path, p);
                }
                case "previous", "next" -> {
                    if (icon.equals("previous")) canvas.scale(-1, 1);
                    path.moveTo(-10, -7.5F).lineTo(0, 0).lineTo(-10, 7.5F).closePath();
                    path.moveTo(0, -7.5F).lineTo(10, 0).lineTo(0, 7.5F).closePath();
                    drawPath(canvas, path, p);
                }
                case "down" -> {
                    path.moveTo(-7, -3).lineTo(0, 4).lineTo(7, -3);
                    drawPath(canvas, path, p.setStroke(true).setStrokeWidth(2.4F).setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND));
                }
                case "heart" -> {
                    path.moveTo(0, 8).cubicTo(-18, -2, -6, -14, 0, -5).cubicTo(6, -14, 18, -2, 0, 8).closePath();
                    drawPath(canvas, path, p.setStroke(color != 0xFFFF627D).setStrokeWidth(1.7F));
                }
                case "loading" -> canvas.drawArc(-8, -8, 8, 8, phase * 210, 265, false, p.setStroke(true).setStrokeWidth(2).setStrokeCap(PaintStrokeCap.ROUND));
                default -> { }
            }
            canvas.restore();
        }
    }

    private static void drawPath(Canvas canvas, PathBuilder builder, Paint paint) {
        try (Path path = builder.detach()) { canvas.drawPath(path, paint); }
    }
    private void speaker(Canvas canvas, float x, float y, int color) {
        try (PathBuilder path = new PathBuilder()) {
            path.moveTo(x - 5, y - 2).lineTo(x - 2, y - 2).lineTo(x + 2, y - 5)
                    .lineTo(x + 2, y + 5).lineTo(x - 2, y + 2).lineTo(x - 5, y + 2).closePath();
            drawPath(canvas, path, ink(color));
            canvas.drawArc(x - 1, y - 5, x + 7, y + 5, -60, 120, false, ink(color).setStroke(true).setStrokeWidth(1.2F));
        }
    }

    private void slider(Canvas canvas, Box bounds, float value, float hover) {
        float height = bounds.h + 2 * hover;
        Box track = new Box(bounds.x, bounds.cy() - height / 2, bounds.w, height);
        round(canvas, track, height / 2, 0x35FFFFFF);
        float fill = bounds.w * clamp(value, 0, 1);
        if (fill > 0) round(canvas, new Box(track.x, track.y, fill, height), height / 2, 0xBFFFFFFF);
        if (hover > 0.01F) canvas.drawCircle(track.x + fill, bounds.cy(), 4 * hover, ink(white(hover)));
    }

    private void dots(Canvas canvas, float x, float y, boolean animate) {
        for (int i = 0; i < 3; i++) {
            float pulse = animate ? (float) (0.5 + 0.5 * Math.sin(elapsed * 3 - i * 0.7)) : 0.5F;
            canvas.drawCircle(x + i * 12, y, 2.6F + pulse, ink(white(0.25F + pulse * 0.4F)));
        }
    }

    private float hover(String key, boolean hovered) { return ui.hover(key, hovered); }
    private Paint ink(int color) { return paint.reset().setAntiAlias(true).setColor(color); }
    private void round(Canvas canvas, Box box, float radius, int color) { if (box.w > 0 && box.h > 0) canvas.drawRRect(RRect.makeXYWH(box.x, box.y, box.w, box.h, radius), ink(color)); }
    private void text(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) { glyphs(canvas, text, x, y, (bold ? medium : regular).apply(size), color); }
    private void glyphs(Canvas canvas, String text, float x, float y, Font font, int color) { if (!text.isEmpty()) canvas.drawString(MusicText.safe(text), x, y - font.getMetrics().getAscent(), font, ink(color)); }
    private void lyricGlyphs(Canvas canvas, String text, float x, float y, Font font, int color, MaskFilter blur) {
        if (!text.isEmpty()) canvas.drawString(text, x, y - font.getMetrics().getAscent(), font, ink(color).setMaskFilter(blur));
    }
    private void centered(Canvas canvas, String text, Box box, float size, int color, boolean bold) { text(canvas, text, box.cx() - width(text, size, bold) / 2, box.cy() - size * 0.65F, size, color, bold); }
    private float width(String text, float size, boolean bold) { return MusicText.width(text, (bold ? medium : regular).apply(size)::measureTextWidth); }
    private String limited(String text, float width, float size, boolean bold) { return MusicText.limit(text, Math.max(0, width), (bold ? medium : regular).apply(size)::measureTextWidth); }
    private static int white(float alpha) { return (Math.round(clamp(alpha, 0, 1) * 255) << 24) | 0xFFFFFF; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static String time(long ms) { long seconds = Math.max(0, ms) / 1000; return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60; }
    private static float characterProgress(LyricLine line, long position) {
        int index = LyricParser.currentWordIndex(line, position);
        if (index < 0) return 0;
        float chars = 0;
        for (int i = 0; i < index; i++) chars += line.words().get(i).text().length();
        return chars + line.words().get(index).text().length() * LyricParser.currentWordProgress(line, index, position);
    }
    private static float highlightWidth(MusicLyricLayout.Segment row, float highlight, Font font) {
        float local = clamp(highlight - row.offset(), 0, row.text().length());
        int chars = (int) local;
        if (chars > 0 && chars < row.text().length() && Character.isLowSurrogate(row.text().charAt(chars))) chars--;
        float fill = MusicText.width(row.text().substring(0, chars), font::measureTextWidth);
        if (chars < row.text().length()) {
            int end = row.text().offsetByCodePoints(chars, 1);
            fill += MusicText.width(row.text().substring(chars, end), font::measureTextWidth) * (local - chars) / (end - chars);
        }
        return fill;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (backdrop != null) backdrop.close();
        if (previousBackdrop != null) previousBackdrop.close();
        paint.close();
        artworkShadow.close();
        lyricBlur.close();
    }
}
