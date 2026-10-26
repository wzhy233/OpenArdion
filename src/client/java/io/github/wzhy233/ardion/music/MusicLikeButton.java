package io.github.wzhy233.ardion.music;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.wzhy233.ardion.gui.ui.UiManager;

public final class MusicLikeButton {
    private static final long CLICK_NANOS = 330_000_000L;
    private static final int ACCENT = 0xFFFF627D;

    private long trackId = Long.MIN_VALUE;
    private long lastFrameNanos;
    private long clickNanos;
    private float likedAmount;
    private float hoverAmount;
    private boolean clickLiked;

    public void clicked(long songId, boolean liked) {
        if (songId != trackId) return;
        clickLiked = liked;
        clickNanos = System.nanoTime();
    }

    public void draw(Canvas canvas, float cx, float cy, float radius, boolean hovered, boolean liked, long songId) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1F, (now - lastFrameNanos) / 1_000_000_000F);
        lastFrameNanos = now;
        if (trackId != songId) {
            trackId = songId;
            likedAmount = liked ? 1 : 0;
            hoverAmount = 0;
            clickNanos = 0;
        }
        likedAmount = approach(likedAmount, liked ? 1 : 0, dt, 16);
        hoverAmount = approach(hoverAmount, hovered ? 1 : 0, dt, 18);

        float clickProgress = clickNanos == 0 ? 1 : Math.min(1, (now - clickNanos) / (float) CLICK_NANOS);
        float wave = (float) Math.sin(Math.PI * clickProgress) * (1 - clickProgress * 0.35F);
        float scale = 1 + hoverAmount * 0.045F + (clickLiked ? 0.16F : -0.09F) * wave;

        try (Paint paint = new Paint().setAntiAlias(true)) {
            float backdropAlpha = 0.05F * hoverAmount + 0.055F * likedAmount;
            if (backdropAlpha > 0.002F) {
                canvas.drawCircle(cx, cy, radius * 0.95F, paint.setColor(alpha(ACCENT, backdropAlpha)));
            }
            if (clickLiked && clickProgress < 1) {
                float fade = (1 - clickProgress) * (1 - clickProgress);
                float ringRadius = radius * (0.68F + clickProgress * 0.72F);
                canvas.drawCircle(cx, cy, ringRadius, paint.setColor(alpha(ACCENT, fade * 0.32F))
                        .setStroke(true).setStrokeWidth(1.3F));
                paint.setStroke(false);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * (i / 3.0 - 0.5);
                    float distance = radius * (0.86F + clickProgress * 0.57F);
                    canvas.drawCircle(cx + (float) Math.cos(angle) * distance,
                            cy + (float) Math.sin(angle) * distance, 1.1F * fade,
                            paint.setColor(alpha(ACCENT, fade * 0.74F)));
                }
            }

            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(radius * scale / 15F, radius * scale / 15F);
            try (PathBuilder builder = new PathBuilder()) {
                builder.moveTo(0, 10).cubicTo(-17, -0.5F, -8.5F, -13, 0, -5.2F)
                        .cubicTo(8.5F, -13, 17, -0.5F, 0, 10).closePath();
                try (Path heart = builder.detach()) {
                    if (likedAmount > 0.002F) {
                        canvas.drawPath(heart, paint.setStroke(false).setColor(alpha(ACCENT, likedAmount)));
                    }
                    int outline = UiManager.mix(0xD8FFFFFF, ACCENT, likedAmount);
                    canvas.drawPath(heart, paint.setStroke(true).setStrokeWidth(1.55F)
                            .setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND)
                            .setColor(outline));
                }
            }
            canvas.restore();
        }
    }

    private static float approach(float value, float target, float dt, float speed) {
        return value + (target - value) * (1 - (float) Math.exp(-speed * dt));
    }

    private static int alpha(int color, float opacity) {
        return (Math.round(Math.clamp(opacity, 0, 1) * 255) << 24) | (color & 0xFFFFFF);
    }
}
