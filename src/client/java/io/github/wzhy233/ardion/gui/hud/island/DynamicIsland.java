package io.github.wzhy233.ardion.gui.hud.island;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.hud.HudPaint;
import io.github.wzhy233.ardion.gui.hud.island.slots.MessageSlot;
import io.github.wzhy233.ardion.gui.hud.island.slots.ModuleToggleSlot;
import io.github.wzhy233.ardion.gui.notification.Notification;
import io.github.wzhy233.ardion.gui.shader.Effects;
import io.github.wzhy233.ardion.irc.IrcLaunchSession;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.impl.render.MusicPlayer;
import io.github.wzhy233.ardion.module.impl.world.BedNuker;
import io.github.wzhy233.ardion.module.impl.world.Scaffold;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.music.MusicPlayerService;
import io.github.wzhy233.ardion.music.MusicTrack;
import io.github.wzhy233.ardion.music.lyric.LyricLine;
import io.github.wzhy233.ardion.notebot.Service;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.setting.ThemeSetting;
import io.github.wzhy233.ardion.util.animation.Animation;
import io.github.wzhy233.ardion.util.animation.Easing;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import net.minecraft.client.MinecraftClient;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class DynamicIsland {
    private static final int IDLE_FONT_SIZE = 18;
    private static final float IDLE_HEIGHT = 20.0F;
    private static final float IDLE_RADIUS = IDLE_HEIGHT / 2.0F;
    private static final float NOTIF_PADDING_Y = 9.0F;
    private static final float NOTIF_ROW_GAP = 4.0F;
    private static final float NOTIF_RADIUS = 18.0F;
    private static final float IDLE_PADDING_X = 12.0F;
    private static final float NOTEBOT_HEIGHT = 52.0F;
    private static final float NOTEBOT_RADIUS = NOTEBOT_HEIGHT / 2.0F;
    private static final float NOTEBOT_PADDING_X = 16.0F;
    private static final float NOTEBOT_PADDING_TOP = 10.0F;
    private static final float NOTEBOT_TITLE_SIZE = 16.0F;
    private static final float NOTEBOT_SUBTITLE_SIZE = 11.0F;
    private static final float NOTEBOT_PROGRESS_HEIGHT = 4.0F;
    private static final float NOTEBOT_MIN_WIDTH = 240.0F;
    private static final float NOTEBOT_MAX_WIDTH_RATIO = 0.62F;
    private static final float NOTEBOT_MAX_WIDTH_CAP = 540.0F;
    private static final float SCAFFOLD_HEIGHT = 32.0F;
    private static final float SCAFFOLD_RADIUS = SCAFFOLD_HEIGHT / 2.0F;
    private static final float SCAFFOLD_PADDING_X = 12.0F;
    private static final float SCAFFOLD_ICON_BOX = 24.0F;
    private static final float SCAFFOLD_ICON_GAP = 10.0F;
    private static final float SCAFFOLD_TEXT_GAP = 10.0F;
    private static final float SCAFFOLD_TEXT_SIZE = 14.0F;
    private static final float SCAFFOLD_BAR_HEIGHT = 4.0F;
    private static final float SCAFFOLD_MIN_BAR_WIDTH = 82.0F;
    private static final float SCAFFOLD_MIN_WIDTH = 178.0F;
    private static final float SCAFFOLD_MAX_WIDTH_RATIO = 0.72F;
    private static final float BED_HEIGHT = 44.0F;
    private static final float BED_RADIUS = BED_HEIGHT / 2.0F;
    private static final float BED_PADDING_X = NOTEBOT_PADDING_X;
    private static final float BED_PADDING_Y = 8.0F;
    private static final float BED_TEXT_SIZE = SCAFFOLD_TEXT_SIZE;
    private static final float BED_BAR_HEIGHT = NOTEBOT_PROGRESS_HEIGHT;
    private static final float BED_MIN_WIDTH = 180.0F;
    private static final float BED_MAX_WIDTH_RATIO = NOTEBOT_MAX_WIDTH_RATIO;
    private static final int SCAFFOLD_FULL_BLOCKS = 256;
    private static final float MUSIC_HEIGHT = SCAFFOLD_HEIGHT;
    private static final float MUSIC_RADIUS = SCAFFOLD_RADIUS;
    private static final float MUSIC_PADDING_X = SCAFFOLD_PADDING_X;
    private static final float MUSIC_COVER_SIZE = 24.0F;
    private static final float MUSIC_COVER_GAP = 9.0F;
    private static final float MUSIC_TEXT_SIZE = 14.0F;
    private static final float MUSIC_MIN_WIDTH = 96.0F;
    private static final float MUSIC_MAX_SCREEN_MARGIN = 8.0F;
    private static final float MUSIC_LYRIC_CLIP_PAD = 4.0F;
    private static final long MUSIC_LINE_TRANSITION_MS = 260L;
    private static final float MUSIC_LINE_TRANSITION_DISTANCE = 12.0F;
    private static final String PURE_MUSIC_PLACEHOLDER = "纯音乐，请欣赏";
    private static final float TOP_Y = 10.0F;
    private static final long ENTER_MS = io.github.wzhy233.ardion.gui.ui.UiManager.ENTER_MS;
    private static final long EXIT_MS = io.github.wzhy233.ardion.gui.ui.UiManager.EXIT_MS;
    private static final long IDLE_TEXT_DELAY_MS = 220L;

    private final Deque<Slot> queue = new ConcurrentLinkedDeque<>();
    private final List<Slot> slots = new ArrayList<>();
    private final TabIsland tabIsland = new TabIsland();
    private final Animation widthAnim = new Animation(Easing.EASE_OUT_BACK, Slot.SHAPE_MS);
    private final Animation heightAnim = new Animation(Easing.EASE_OUT_BACK, Slot.SHAPE_MS);
    private final Animation radiusAnim = new Animation(Easing.EASE_OUT_EXPO, Slot.SHAPE_MS);
    private final Animation scaffoldProgressAnim = new Animation(Easing.EASE_OUT_CUBIC, 180L);
    private long idleEmptySince = -1L;
    private String musicLineKey = "";
    private String musicDisplayedLineText = "";
    private String musicPreviousLineText = "";
    private long musicLineChangedAt = 0L;

    public void post(Notification notification) {
        post(new MessageSlot(notification));
    }

    public void post(Slot slot) {
        if (!HudSetting.dynamicIsland()) return;
        if (!HudSetting.notifications()) return;
        queue.addLast(slot);
    }

    public void showTab(TabIsland.Snapshot snapshot) {
        if (!HudSetting.dynamicIsland() || !HudSetting.islandTab()) {
            tabIsland.hide();
            return;
        }
        tabIsland.show(snapshot);
    }

    public void hideTab() {
        tabIsland.hide();
    }

    public void render(float screenWidth, float screenHeight) {
        if (!HudSetting.dynamicIsland()) {
            resetMusicLineTransition();
            tabIsland.hide();
            queue.clear();
            slots.clear();
            return;
        }
        long now = System.currentTimeMillis();
        tick(now);
        Font idleFont = Fonts.getMedium(IDLE_FONT_SIZE);

        float targetWidth;
        float targetHeight;
        float targetRadius;
        TabIsland.Layout tabLayout = tabIsland.measure(screenWidth, screenHeight);
        ScaffoldStatus scaffoldStatus = getScaffoldStatus();
        BedStatus bedStatus = getBedStatus();
        if (tabLayout != null) {
            targetWidth = tabLayout.width();
            targetHeight = tabLayout.height();
            targetRadius = tabLayout.radius();
        } else if (slots.isEmpty()) {
            if (bedStatus != null) {
                BedLayout layout = measureBedLayout(screenWidth);
                targetWidth = layout.width();
                targetHeight = layout.height();
                targetRadius = layout.radius();
            } else if (scaffoldStatus != null) {
                ScaffoldLayout layout = measureScaffoldLayout(screenWidth, scaffoldStatus);
                targetWidth = layout.width();
                targetHeight = layout.height();
                targetRadius = layout.radius();
            } else if (Service.hasActiveSong()) {
                NotebotLayout layout = measureNotebotLayout(screenWidth);
                targetWidth = layout.width();
                targetHeight = layout.height();
                targetRadius = layout.radius();
            } else if (MusicPlayerService.hasActiveTrack()) {
                updateMusicLineTransition(now);
                MusicLayout layout = measureMusicLayout(screenWidth);
                targetWidth = layout.width();
                targetHeight = layout.height();
                targetRadius = layout.radius();
            } else {
                resetMusicLineTransition();
                targetWidth = measureIdleWidth(idleFont);
                targetHeight = IDLE_HEIGHT;
                targetRadius = IDLE_RADIUS;
            }
        } else {
            float maxWidth = measureIdleWidth(idleFont);
            float sumHeight = NOTIF_PADDING_Y * 2.0F;
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                maxWidth = Math.max(maxWidth, slot.measureWidth());
                sumHeight += slot.getHeight();
                if (i > 0) sumHeight += NOTIF_ROW_GAP;
            }
            targetWidth = maxWidth;
            targetHeight = sumHeight;
            targetRadius = NOTIF_RADIUS;
        }

        float width = (float) widthAnim.animate(targetWidth);
        float height = (float) heightAnim.animate(targetHeight);
        float radius = (float) radiusAnim.animate(targetRadius);
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
            return;
        }
        radius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        float x = Math.max(2.0F, Math.min((screenWidth - width) / 2.0F, screenWidth - width - 2.0F));
        float y = Math.max(2.0F, Math.min(TOP_Y, screenHeight - height - 2.0F));

        if (Skia.getCanvas() == null) return;
        if (HudSetting.blur()) {
            Rect blurRect = Rect.makeXYWH(x, y, width, height);
            Skia.save();
            Skia.clip(x, y, width, height, radius);
            if (HudSetting.blurSksl()) {
                Effects.blurBackdropSKSL(Skia.getCanvas(), Skia.getSurface(), blurRect,
                        HudSetting.blurSigma(), HudSetting.skslShader());
            } else {
                Effects.blurBackdrop(Skia.getCanvas(), blurRect, HudSetting.blurSigma());
            }
            Skia.restore();
        }
        int bgAlpha = HudSetting.backgroundAlpha();
        if (bgAlpha > 0 && HudSetting.shadow()) Skia.drawShadow(x, y, width, height, radius);
        HudPaint.renderHudCard(x, y, width, height, radius, Color.WHITE, bgAlpha);
        Skia.save();
        Skia.clip(x, y, width, height, radius);

        if (tabLayout != null) {
            tabIsland.render(tabLayout, x, y, width, height);
            Skia.restore();
            return;
        }

        if (slots.isEmpty()) {
            if (bedStatus != null) {
                renderBedStatus(bedStatus, x, y, width, height, now);
            } else if (scaffoldStatus != null) {
                renderScaffoldStatus(scaffoldStatus, x, y, width, height);
            } else if (Service.hasActiveSong()) {
                renderNotebotStatus(x, y, width, height, now);
            } else if (MusicPlayerService.hasActiveTrack()) {
                renderMusicStatus(x, y, width, height, now);
            } else {
                float idleAlpha = computeIdleAlpha(now, 0.0F);
                if (idleAlpha > 0.005F) renderIdle(x, y, height, idleFont, idleAlpha);
            }
        } else {
            float maxSlotAlpha = 0.0F;
            for (Slot slot : slots) {
                maxSlotAlpha = Math.max(maxSlotAlpha, computeSlotAlpha(slot, now));
            }
            float idleAlpha = computeIdleAlpha(now, maxSlotAlpha);
            if (idleAlpha > 0.005F) renderIdle(x, y, height, idleFont, idleAlpha);
        }

        float cursor = NOTIF_PADDING_Y;
        for (Slot slot : slots) {
            float animOffset = (float) slot.yOffsetAnim.animate(cursor);
            float alpha = computeSlotAlpha(slot, now);
            if (alpha > 0.005F) slot.render(x, y + animOffset, width, alpha);
            cursor += slot.getHeight() + NOTIF_ROW_GAP;
        }

        Skia.restore();
    }

    private void tick(long now) {
        Slot next;
        while ((next = queue.pollFirst()) != null) {
            if (next instanceof ModuleToggleSlot moduleToggleSlot) {
                mergeModuleToggleSlot(moduleToggleSlot, now);
                continue;
            }
            next.initYOffset(computeTargetOffset(slots.size()));
            slots.add(next);
        }
        Iterator<Slot> iterator = slots.iterator();
        while (iterator.hasNext()) {
            Slot slot = iterator.next();
            if (slot.fadeOutStart == -1L && slot.duration != -1L && now - slot.enteredAt >= slot.duration) {
                slot.markFadeOut(now);
            }
            if (slot.fadeOutStart != -1L && now - slot.fadeOutStart >= EXIT_MS) {
                iterator.remove();
            }
        }
    }

    private BedStatus getBedStatus() {
        BedNuker nuker = ModuleManager.getModule(BedNuker.class);
        if (nuker == null || !nuker.showIslandProgress()) {
            return null;
        }
        return new BedStatus(Math.max(0.0F, Math.min(1.0F, nuker.islandProgress())));
    }

    private BedLayout measureBedLayout(float screenWidth) {
        Font labelFont = Fonts.getMedium(BED_TEXT_SIZE);
        Font percentFont = Fonts.getJbSemibold(BED_TEXT_SIZE);
        float labelWidth = Skia.getTextBounds("Breaking bed", labelFont).getWidth();
        float percentWidth = Skia.getTextBounds("100%", percentFont).getWidth();
        float naturalWidth = BED_PADDING_X * 2.0F + labelWidth + 12.0F + percentWidth;
        float maxWidth = Math.max(BED_MIN_WIDTH, screenWidth * BED_MAX_WIDTH_RATIO);
        float width = Math.min(Math.max(BED_MIN_WIDTH, naturalWidth), maxWidth);
        return new BedLayout(width, BED_HEIGHT, BED_RADIUS);
    }

    private void renderBedStatus(BedStatus status, float x, float y, float width, float height, long now) {
        Font labelFont = Fonts.getMedium(BED_TEXT_SIZE);
        Font percentFont = Fonts.getJbSemibold(BED_TEXT_SIZE);
        String percent = (int) (status.progress() * 100.0F) + "%";
        Rect percentBounds = Skia.getTextBounds(percent, percentFont);
        float textX = x + BED_PADDING_X;
        float percentX = x + width - BED_PADDING_X - percentBounds.getWidth();
        float textY = y + BED_PADDING_Y;
        float barX = textX;
        float barY = y + height - BED_PADDING_Y - BED_BAR_HEIGHT;
        float barWidth = Math.max(0.0F, width - BED_PADDING_X * 2.0F);
        Color accent = new Color(ThemeSetting.getColor((int) (now * 0.01F)), true);

        Paint paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(0.98F);
        Skia.drawText("Breaking bed", textX, textY, labelFont, paint);
        Skia.drawText(percent, percentX, textY, percentFont, paint);

        Skia.drawRoundedRect(barX, barY, barWidth, BED_BAR_HEIGHT, BED_BAR_HEIGHT / 2.0F, new Color(255, 255, 255, 38));
        if (barWidth > 0.0F && status.progress() > 0.0F) {
            Skia.drawRoundedRect(barX, barY, barWidth * status.progress(), BED_BAR_HEIGHT, BED_BAR_HEIGHT / 2.0F,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220));
        }
    }

    private ScaffoldStatus getScaffoldStatus() {
        if (MinecraftClient.getInstance().player == null) {
            return null;
        }
        Scaffold scaffold = ModuleManager.getModule(Scaffold.class);
        if (scaffold == null || !scaffold.isEnabled()) {
            return null;
        }
        return new ScaffoldStatus(Math.max(0, scaffold.getRemainingBlocks()), scaffold.getDisplayBlockColor());
    }

    private void mergeModuleToggleSlot(ModuleToggleSlot incoming, long now) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!(slot instanceof ModuleToggleSlot existing) || !existing.matchesModule(incoming.getModuleName())) {
                continue;
            }

            existing.replaceWith(incoming, now);
            for (int j = i + 1; j < slots.size(); j++) {
                Slot duplicate = slots.get(j);
                if (duplicate instanceof ModuleToggleSlot duplicateToggle
                        && duplicateToggle.matchesModule(incoming.getModuleName())) {
                    slots.remove(j--);
                }
            }
            return;
        }

        incoming.restart(now);
        ((Slot) incoming).initYOffset(computeTargetOffset(slots.size()));
        slots.add(incoming);
    }

    private float computeTargetOffset(int index) {
        float y = NOTIF_PADDING_Y;
        for (int i = 0; i < index && i < slots.size(); i++) {
            y += slots.get(i).getHeight() + NOTIF_ROW_GAP;
        }
        return y;
    }

    private float computeIdleAlpha(long now, float ignoredMaxSlotAlpha) {
        if (slots.isEmpty()) {
            if (idleEmptySince == -1L) idleEmptySince = now;
            long elapsed = now - idleEmptySince;
            if (elapsed < IDLE_TEXT_DELAY_MS) return 0.0F;
            float t = Math.min(1.0F, (elapsed - IDLE_TEXT_DELAY_MS) / (float) ENTER_MS);
            return io.github.wzhy233.ardion.gui.ui.UiTransition.ease(t);
        }
        idleEmptySince = -1L;
        return 0.0F;
    }

    private float computeSlotAlpha(Slot slot, long now) {
        if (slot.fadeOutStart != -1L) {
            float t = Math.min(1.0F, (now - slot.fadeOutStart) / (float) EXIT_MS);
            return 1.0F - io.github.wzhy233.ardion.gui.ui.UiTransition.ease(t);
        }
        float t = Math.min(1.0F, (now - slot.enteredAt) / (float) ENTER_MS);
        return io.github.wzhy233.ardion.gui.ui.UiTransition.ease(t);
    }

    private float measureIdleWidth(Font font) {
        Rect logoBounds = Skia.getTextBounds("Ardion", font);
        String suffix = idleSuffix();
        Rect suffixBounds = Skia.getRawTextBounds(suffix, font);
        float gap = suffix.isEmpty() ? 0.0F : 3.0F;
        return IDLE_PADDING_X + logoBounds.getWidth() + gap + suffixBounds.getWidth() + IDLE_PADDING_X;
    }

    private String idleSuffix() {
        if (Spoofer.spoofsPlayerName() || Spoofer.spoofsServerIp()) {
            return "";
        }
        var session = IrcLaunchSession.get();
        return session == null ? "" : " | " + session.getName();
    }

    private void renderIdle(float x, float y, float height, Font font, float alpha) {
        String logoText = "Ardion";
        String suffix = idleSuffix();
        Rect logoBounds = Skia.getTextBounds(logoText, font);
        Rect suffixBounds = Skia.getRawTextBounds(suffix, font);
        float gap = suffix.isEmpty() ? 0.0F : 3.0F;
        float logoX = x + IDLE_PADDING_X;
        float logoY = y + (height - logoBounds.getHeight()) / 2.0F;
        Color logoColor = new Color(ThemeSetting.getColor((int) (y * 10.0F)), true);
        float logoAlpha = alpha * (logoColor.getAlpha() / 255.0F);
        Paint logoPaint = Skia.scratchPaint();
        logoPaint.setColor(logoColor.getRGB());
        logoPaint.setBlendMode(BlendMode.SRC_OVER);
        logoPaint.setAlphaf(logoAlpha);
        Skia.drawText(logoText, logoX, logoY, font, logoPaint);

        Paint paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(alpha);
        if (!suffix.isEmpty()) {
            Skia.drawRawText(suffix, logoX + logoBounds.getWidth() + gap,
                    y + (height - suffixBounds.getHeight()) / 2.0F, font, paint);
        }
    }

    private NotebotLayout measureNotebotLayout(float screenWidth) {
        Font titleFont = Fonts.getMedium(NOTEBOT_TITLE_SIZE);
        Font subtitleFont = Fonts.getMedium(NOTEBOT_SUBTITLE_SIZE);
        Font timeFont = Fonts.getJbMedium(NOTEBOT_SUBTITLE_SIZE);

        String title = safeNotebotText(Service.getSongName());
        if (title.isBlank()) {
            title = "Notebot";
        }
        boolean initializing = Service.isInitializing() || Service.isTuning();
        String subtitle = initializing ? "Initializing" : safeNotebotText(Service.getSongAuthor());
        String remaining = initializing ? "" : formatRemaining(Service.getRemainingTicks());
        float remainingSlotWidth = measureRemainingWidth(timeFont);

        float maxWidth = Math.min(NOTEBOT_MAX_WIDTH_CAP, screenWidth * NOTEBOT_MAX_WIDTH_RATIO);
        float titleWidth = Skia.getTextBounds(title, titleFont).getWidth();
        float subtitleWidth = Skia.getTextBounds(subtitle, subtitleFont).getWidth();
        float remainingWidth = remaining.isBlank() ? 0.0F : remainingSlotWidth;
        float contentWidth = subtitle.isBlank()
                ? Math.max(titleWidth, remainingWidth)
                : Math.max(titleWidth, subtitleWidth + (remaining.isBlank() ? 0.0F : 12.0F + remainingWidth));
        float width = Math.max(NOTEBOT_MIN_WIDTH, contentWidth + NOTEBOT_PADDING_X * 2.0F);
        width = Math.min(width, maxWidth);
        return new NotebotLayout(width, NOTEBOT_HEIGHT, NOTEBOT_RADIUS);
    }

    private void renderNotebotStatus(float x, float y, float width, float height, long now) {
        Font titleFont = Fonts.getMedium(NOTEBOT_TITLE_SIZE);
        Font subtitleFont = Fonts.getMedium(NOTEBOT_SUBTITLE_SIZE);
        Font timeFont = Fonts.getJbMedium(NOTEBOT_SUBTITLE_SIZE);

        String title = safeNotebotText(Service.getSongName());
        if (title.isBlank()) {
            title = "Notebot";
        }
        boolean initializing = Service.isInitializing() || Service.isTuning();
        String subtitle = initializing ? "Initializing" : safeNotebotText(Service.getSongAuthor());
        String remaining = initializing ? "" : formatRemaining(Service.getRemainingTicks());
        float remainingSlotWidth = measureRemainingWidth(timeFont);

        float innerWidth = Math.max(0.0F, width - NOTEBOT_PADDING_X * 2.0F);
        title = Skia.getLimitTextWithDot(title, titleFont, innerWidth);

        Rect titleBounds = Skia.getTextBounds(title, titleFont);
        float textX = x + NOTEBOT_PADDING_X;
        float titleY = y + NOTEBOT_PADDING_TOP;
        float subtitleY = titleY + titleBounds.getHeight() + 3.0F;
        float remainingX = x + width - NOTEBOT_PADDING_X - (remaining.isBlank() ? 0.0F : remainingSlotWidth);

        Color accent = new Color(ThemeSetting.getColor((int) (now * 0.01F)), true);
        Color muted = new Color(255, 255, 255, 170);

        Paint paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(0.98F);
        Skia.drawText(title, textX, titleY, titleFont, paint);

        paint = Skia.scratchPaint();
        paint.setColor(muted.getRGB());
        paint.setAlphaf(0.95F);
        if (!subtitle.isBlank()) {
            float subtitleWidth = remaining.isBlank()
                    ? Math.max(0.0F, width - NOTEBOT_PADDING_X * 2.0F)
                    : Math.max(0.0F, remainingX - textX - 12.0F);
            subtitle = Skia.getLimitTextWithDot(subtitle, subtitleFont, subtitleWidth);
            Skia.drawText(subtitle, textX, subtitleY, subtitleFont, paint);
        }
        if (!remaining.isBlank()) {
            Skia.drawText(remaining, remainingX, subtitleY, timeFont, paint);
        }

        float barX = textX;
        float barY = y + height - NOTEBOT_PADDING_TOP - NOTEBOT_PROGRESS_HEIGHT;
        float barWidth = Math.max(0.0F, width - NOTEBOT_PADDING_X * 2.0F);
        float progress = Service.getDisplayProgress();
        float fillWidth = barWidth * progress;

        Skia.drawRoundedRect(barX, barY, barWidth, NOTEBOT_PROGRESS_HEIGHT, NOTEBOT_PROGRESS_HEIGHT / 2.0F,
                new Color(255, 255, 255, 38));
        if (fillWidth > 0.0F) {
            Skia.drawRoundedRect(barX, barY, fillWidth, NOTEBOT_PROGRESS_HEIGHT, NOTEBOT_PROGRESS_HEIGHT / 2.0F,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220));
        }
    }

    private ScaffoldLayout measureScaffoldLayout(float screenWidth, ScaffoldStatus status) {
        Font textFont = Fonts.getJbSemibold(SCAFFOLD_TEXT_SIZE);
        String blocksText = formatScaffoldBlocks(status.remainingBlocks());
        float textWidth = Skia.getTextBounds(blocksText, textFont).getWidth();
        float naturalWidth = SCAFFOLD_PADDING_X * 2.0F + SCAFFOLD_ICON_BOX + SCAFFOLD_ICON_GAP
                + SCAFFOLD_MIN_BAR_WIDTH + SCAFFOLD_TEXT_GAP + textWidth;
        float maxWidth = Math.max(SCAFFOLD_MIN_WIDTH, screenWidth * SCAFFOLD_MAX_WIDTH_RATIO);
        float width = Math.min(Math.max(SCAFFOLD_MIN_WIDTH, naturalWidth), maxWidth);
        return new ScaffoldLayout(width, SCAFFOLD_HEIGHT, SCAFFOLD_RADIUS);
    }

    private MusicLayout measureMusicLayout(float screenWidth) {
        MusicTextLayout text = measureMusicText(screenWidth, musicMeasuredDisplayText());
        boolean hasCover = hasMusicCover();
        float coverWidth = hasCover ? MUSIC_COVER_SIZE + MUSIC_COVER_GAP : 0.0F;
        float naturalWidth = MUSIC_PADDING_X * 2.0F + coverWidth + text.width();
        float maxWidth = Math.max(MUSIC_MIN_WIDTH, screenWidth - MUSIC_MAX_SCREEN_MARGIN * 2.0F);
        float width = Math.min(Math.max(MUSIC_MIN_WIDTH, naturalWidth), maxWidth);
        return new MusicLayout(width, MUSIC_HEIGHT, MUSIC_RADIUS);
    }

    private void renderMusicStatus(float x, float y, float width, float height, long now) {
        boolean hasCover = hasMusicCover();
        float textX = x + MUSIC_PADDING_X;
        if (hasCover) {
            float coverX = x + MUSIC_PADDING_X;
            float coverY = y + (height - MUSIC_COVER_SIZE) / 2.0F;
            renderMusicCover(coverX, coverY, MUSIC_COVER_SIZE);
            textX = coverX + MUSIC_COVER_SIZE + MUSIC_COVER_GAP;
        }
        float textW = Math.max(0.0F, x + width - MUSIC_PADDING_X - textX);
        MusicTextLayout text = measureMusicText(textW + MUSIC_PADDING_X * 2.0F, musicMeasuredDisplayText());
        boolean centerText = !hasCover;
        if (!hasCover) {
            textX = x + (width - text.width()) / 2.0F;
            textW = text.width();
        }
        LyricLine line = musicLyricLine();
        renderMusicLyric(line, musicDisplayText(), textX, y, textW, height, text.font(), centerText, now);
    }

    private void renderMusicCover(float x, float y, float size) {
        io.github.humbleui.skija.Image cover = MusicPlayerService.coverImage();
        if (cover == null) return;
        if (cover != null) {
            Skia.save();
            Skia.clip(x, y, size, size, 7.0F);
            Skia.getCanvas().drawImageRect(cover, Rect.makeXYWH(x, y, size, size));
            Skia.restore();
        }
    }

    private void renderMusicLyric(LyricLine line, String fallback, float x, float y, float width, float height,
                                  Font font, boolean centerText, long now) {
        String text = cleanMusicText(line != null ? line.text() : fallback);
        long elapsed = now - musicLineChangedAt;
        boolean transitioning = musicLineChangedAt > 0L
                && elapsed >= 0L
                && elapsed < MUSIC_LINE_TRANSITION_MS
                && !musicPreviousLineText.isBlank()
                && !musicPreviousLineText.equals(text);

        if (!transitioning) {
            drawMusicLyricLine(line, text, x, y, width, height, font, centerText, 1.0F, 0.0F, 0.0F, true);
            return;
        }

        float progress = (float) Easing.EASE_OUT_CUBIC.getFunction()
                .apply(Math.max(0.0D, Math.min(1.0D, elapsed / (double) MUSIC_LINE_TRANSITION_MS))).floatValue();
        Skia.save();
        Skia.clip(x, y, width, height, 0.0F);
        drawMusicLyricLine(null, musicPreviousLineText, x, y, width, height, font, centerText,
                1.0F - progress, -MUSIC_LINE_TRANSITION_DISTANCE * progress, -5.0F * progress, false);
        drawMusicLyricLine(line, text, x, y, width, height, font, centerText,
                progress, MUSIC_LINE_TRANSITION_DISTANCE * (1.0F - progress), 5.0F * (1.0F - progress), true);
        Skia.restore();
    }

    private void drawMusicLyricLine(LyricLine line, String text, float x, float y, float width, float height, Font font,
                                    boolean centerText, float alpha, float offsetY, float rotate, boolean allowWordHighlight) {
        if (alpha <= 0.005F) return;
        text = cleanMusicText(text);
        Rect bounds = Skia.getTextBounds(text, font);
        float textX = centerText ? x + Math.max(0.0F, width - bounds.getWidth()) / 2.0F : x;
        float textY = y + (height - bounds.getHeight()) / 2.0F;

        Skia.save();
        if (Math.abs(rotate) > 0.01F) {
            Skia.rotate(textX, textY + offsetY, Math.max(1.0F, bounds.getWidth()), Math.max(1.0F, bounds.getHeight()), rotate);
        }

        if (allowWordHighlight && hasWordTiming(line)) {
            boolean sweep = MusicPlayer.lyricSweep();
            if (sweep) {
                drawMusicCurrentWordBackplate(line, text, textX, textY + offsetY, bounds.getHeight(), font, alpha);
            }

            Paint muted = Skia.scratchPaint();
            muted.setColor(new Color(255, 255, 255, 118).getRGB());
            muted.setAlphaf(alpha);
            Skia.drawText(text, textX, textY + offsetY, font, muted);

            float fillWidth = musicLyricFillWidth(line, text, font, bounds.getWidth());
            if (fillWidth > 0.0F) {
                Skia.save();
                Skia.clip(textX - MUSIC_LYRIC_CLIP_PAD, textY + offsetY - 2.0F,
                        fillWidth + MUSIC_LYRIC_CLIP_PAD * 2.0F, bounds.getHeight() + 4.0F, 0.0F);
                Paint active = Skia.scratchPaint();
                active.setColor(Color.WHITE.getRGB());
                active.setAlphaf(0.98F * alpha);
                Skia.drawText(text, textX, textY + offsetY, font, active);
                Skia.restore();
            }
            if (sweep) {
                drawMusicCurrentWordAccent(line, text, textX, textY + offsetY, bounds.getHeight(), font, alpha);
            }
        } else {
            Paint active = Skia.scratchPaint();
            active.setColor(Color.WHITE.getRGB());
            active.setAlphaf(0.98F * alpha);
            Skia.drawText(text, textX, textY + offsetY, font, active);
        }
        Skia.restore();
    }

    private void drawMusicCurrentWordAccent(LyricLine line, String visibleText, float x, float y,
                                            float textHeight, Font font, float alpha) {
        WordHighlightMetrics metrics = musicWordMetrics(line, font);
        if (metrics == null) return;

        float progress = Math.max(0.18F, MusicPlayerService.currentLyricWordProgress());
        Color theme = new Color(ThemeSetting.getColor((int) (System.currentTimeMillis() * 0.01F)), true);
        Paint accent = Skia.scratchPaint();
        accent.setColor(theme.getRGB());
        accent.setAlphaf(1.0F * alpha);
        float visibleRemaining = textAdvance(visibleText, font) - metrics.base();
        float clipWidth = Math.min(metrics.width() * progress, visibleRemaining);
        if (clipWidth <= 0.0F) return;

        Skia.save();
        Skia.clip(x + metrics.base() - MUSIC_LYRIC_CLIP_PAD, y - 2.0F,
                clipWidth + MUSIC_LYRIC_CLIP_PAD * 2.0F, textHeight + 4.0F, 0.0F);
        Skia.drawText(visibleText, x, y, font, accent);
        Skia.restore();

        float underlineW = Math.max(6.0F, clipWidth);
        Skia.drawRoundedRect(x + metrics.base(), y + textHeight - 1.0F, underlineW + 1.5F, 3.0F, 1.5F,
                new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), Math.round(235.0F * alpha)));
    }

    private void drawMusicCurrentWordBackplate(LyricLine line, String visibleText, float x, float y,
                                               float textHeight, Font font, float alpha) {
        WordHighlightMetrics metrics = musicWordMetrics(line, font);
        if (metrics == null) return;
        float visibleRemaining = textAdvance(visibleText, font) - metrics.base();
        float width = Math.min(metrics.width(), visibleRemaining);
        if (width <= 0.0F) return;

        float progress = Math.max(0.0F, Math.min(1.0F, MusicPlayerService.currentLyricWordProgress()));
        Color theme = new Color(ThemeSetting.getColor((int) (System.currentTimeMillis() * 0.01F)), true);
        int fillAlpha = Math.round((58.0F + 34.0F * progress) * alpha);
        int lineAlpha = Math.round((92.0F + 52.0F * progress) * alpha);
        Skia.drawRoundedRect(x + metrics.base() - 3.0F, y + 0.5F, width + 6.0F, textHeight,
                Math.max(4.0F, (textHeight - 1.0F) * 0.42F),
                new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), fillAlpha));
        Skia.drawRoundedRect(x + metrics.base(), y + textHeight - 1.0F,
                Math.max(6.0F, width * Math.max(0.18F, progress)) + 1.5F, 3.0F, 1.5F,
                new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), lineAlpha));
    }

    private WordHighlightMetrics musicWordMetrics(LyricLine line, Font font) {
        int index = MusicPlayerService.currentLyricWordIndex();
        if (line == null || index < 0 || index >= line.words().size()) return null;
        StringBuilder done = new StringBuilder();
        for (int i = 0; i < index; i++) done.append(line.words().get(i).text());
        float base = textAdvance(done.toString(), font);
        String current = line.words().get(index).text();
        float width = textAdvance(current, font);
        return width <= 0.0F ? null : new WordHighlightMetrics(base, width);
    }

    private float musicLyricFillWidth(LyricLine line, String visibleText, Font font, float maxWidth) {
        if (!hasWordTiming(line)) return 0.0F;
        int index = MusicPlayerService.currentLyricWordIndex();
        if (index < 0) return 0.0F;
        StringBuilder done = new StringBuilder();
        for (int i = 0; i < index && i < line.words().size(); i++) done.append(line.words().get(i).text());
        float base = textAdvance(done.toString(), font);
        String current = line.words().get(index).text();
        float currentW = textAdvance(current, font) * MusicPlayerService.currentLyricWordProgress();
        return Math.min(maxWidth + MUSIC_LYRIC_CLIP_PAD, Math.min(textAdvance(visibleText, font) + MUSIC_LYRIC_CLIP_PAD, base + currentW));
    }

    private float textAdvance(String text, Font font) {
        return Skia.getTextBounds(text, font).getWidth();
    }

    private boolean hasMusicCover() {
        MusicTrack track = MusicPlayerService.currentTrack();
        io.github.humbleui.skija.Image cover = MusicPlayerService.coverImage();
        if (cover == null && track != null && track.coverUrl != null && !track.coverUrl.isBlank()) {
            MusicPlayerService.ensureCover(track.coverUrl);
        }
        return cover != null;
    }

    private String musicDisplayText() {
        LyricLine line = musicLyricLine();
        if (line != null && line.text() != null && !line.text().isBlank()) return cleanMusicText(line.text());
        MusicTrack track = MusicPlayerService.currentTrack();
        if (track != null && track.title != null && !track.title.isBlank()) return cleanMusicText(track.title);
        return cleanMusicText(MusicPlayerService.status());
    }

    private String musicMeasuredDisplayText() {
        String current = musicDisplayText();
        if (musicLineChangedAt > 0L
                && System.currentTimeMillis() - musicLineChangedAt < MUSIC_LINE_TRANSITION_MS
                && !musicPreviousLineText.isBlank()) {
            Font font = Fonts.getMedium(MUSIC_TEXT_SIZE);
            return Skia.getTextBounds(musicPreviousLineText, font).getWidth() > Skia.getTextBounds(current, font).getWidth()
                    ? musicPreviousLineText : current;
        }
        return current;
    }

    private MusicTextLayout measureMusicText(float availableWidth, String text) {
        float maxTextWidth = Math.max(0.0F, availableWidth - MUSIC_PADDING_X * 2.0F
                - (hasMusicCover() ? MUSIC_COVER_SIZE + MUSIC_COVER_GAP : 0.0F));
        Font font = Fonts.getMedium(MUSIC_TEXT_SIZE);
        float textWidth = Skia.getTextBounds(text, font).getWidth();
        return new MusicTextLayout(Math.min(textWidth, maxTextWidth), font);
    }

    private void updateMusicLineTransition(long now) {
        LyricLine line = musicLyricLine();
        String text = musicDisplayText();
        MusicTrack track = MusicPlayerService.currentTrack();
        long trackId = track == null ? 0L : track.neteaseId;
        String key = trackId + ":" + (line == null ? "fallback" : line.startMs()) + ":" + text;
        if (key.equals(musicLineKey)) return;

        musicPreviousLineText = musicDisplayedLineText;
        musicDisplayedLineText = text;
        musicLineKey = key;
        musicLineChangedAt = musicPreviousLineText.isBlank() ? 0L : now;
    }

    private void resetMusicLineTransition() {
        musicLineKey = "";
        musicDisplayedLineText = "";
        musicPreviousLineText = "";
        musicLineChangedAt = 0L;
    }

    private boolean hasWordTiming(LyricLine line) {
        return line != null && line.words() != null && !line.words().isEmpty();
    }

    private LyricLine musicLyricLine() {
        LyricLine line = MusicPlayerService.currentLyricLine();
        return isPureMusicPlaceholderLyric(line) ? null : line;
    }

    private boolean isPureMusicPlaceholderLyric(LyricLine line) {
        if (line == null) return false;
        List<LyricLine> lyrics = MusicPlayerService.lyrics();
        if (lyrics == null || lyrics.size() != 1) return false;
        String text = line.text();
        if (text == null) return false;
        return PURE_MUSIC_PLACEHOLDER.equals(text.replace('\n', ' ').replace('\r', ' ').strip());
    }

    private String cleanMusicText(String text) {
        if (text != null) text = text.replace('\n', ' ').replace('\r', ' ').stripTrailing();
        if (text == null || text.isBlank()) {
            String statusText = MusicPlayerService.status();
            return statusText == null ? "" : statusText.replace('\n', ' ').replace('\r', ' ').stripTrailing();
        }
        return text;
    }

    private void renderScaffoldStatus(ScaffoldStatus status, float x, float y, float width, float height) {
        Font textFont = Fonts.getJbSemibold(SCAFFOLD_TEXT_SIZE);
        String blocksText = formatScaffoldBlocks(status.remainingBlocks());
        Rect textBounds = Skia.getTextBounds(blocksText, textFont);
        float textX = x + width - SCAFFOLD_PADDING_X - textBounds.getWidth();
        float textY = y + (height - textBounds.getHeight()) / 2.0F;
        float iconCenterX = x + SCAFFOLD_PADDING_X + SCAFFOLD_ICON_BOX / 2.0F;
        float iconCenterY = y + height / 2.0F;
        float barX = x + SCAFFOLD_PADDING_X + SCAFFOLD_ICON_BOX + SCAFFOLD_ICON_GAP;
        float barRight = textX - SCAFFOLD_TEXT_GAP;
        float barWidth = Math.max(0.0F, barRight - barX);
        float barY = y + (height - SCAFFOLD_BAR_HEIGHT) / 2.0F;
        float progress = Math.max(0.0F, Math.min(1.0F, status.remainingBlocks() / (float) SCAFFOLD_FULL_BLOCKS));
        float animatedProgress = (float) scaffoldProgressAnim.animate(progress);
        animatedProgress = Math.max(0.0F, Math.min(1.0F, animatedProgress));
        Color accent = new Color(ThemeSetting.getColor((int) (System.currentTimeMillis() * 0.01F)), true);

        drawScaffoldBlockIcon(iconCenterX, iconCenterY, status.blockColor());
        Skia.drawRoundedRect(barX, barY, barWidth, SCAFFOLD_BAR_HEIGHT, SCAFFOLD_BAR_HEIGHT / 2.0F,
                new Color(255, 255, 255, 38));
        if (barWidth > 0.0F && animatedProgress > 0.0F) {
            Skia.drawRoundedRect(barX, barY, barWidth * animatedProgress, SCAFFOLD_BAR_HEIGHT,
                    SCAFFOLD_BAR_HEIGHT / 2.0F, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220));
        }

        Paint paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(0.96F);
        Skia.drawText(blocksText, textX, textY, textFont, paint);
    }

    private void drawScaffoldBlockIcon(float centerX, float centerY, int blockColor) {
        Skia.drawCircle(centerX, centerY, SCAFFOLD_ICON_BOX / 2.0F, new Color(255, 255, 255, 34));

        float size = 11.5F;
        float x = centerX;
        float y = centerY + 0.5F;
        float left = x - size * 0.55F;
        float right = x + size * 0.55F;
        float top = y - size * 0.68F;
        float mid = y - size * 0.10F;
        float lowerLeft = y + size * 0.28F;
        float bottom = y + size * 0.62F;

        drawPolygon(new float[]{
                x, top,
                right, y - size * 0.36F,
                x, mid,
                left, y - size * 0.36F
        }, shadeBlockColor(blockColor, 1.22F, 238));
        drawPolygon(new float[]{
                left, y - size * 0.36F,
                x, mid,
                x, bottom,
                left, lowerLeft
        }, shadeBlockColor(blockColor, 0.68F, 238));
        drawPolygon(new float[]{
                right, y - size * 0.36F,
                x, mid,
                x, bottom,
                right, lowerLeft
        }, shadeBlockColor(blockColor, 0.92F, 238));

        Paint outline = Skia.scratchPaint();
        outline.setColor(new Color(255, 255, 255, 72).getRGB());
        outline.setStroke(true);
        outline.setStrokeWidth(0.75F);
        try (PathBuilder builder = new PathBuilder();
             Path path = builder.moveTo(x, top)
                     .lineTo(right, y - size * 0.36F)
                     .lineTo(right, lowerLeft)
                     .lineTo(x, bottom)
                     .lineTo(left, lowerLeft)
                     .lineTo(left, y - size * 0.36F)
                     .closePath()
                     .build()) {
            Skia.getCanvas().drawPath(path, outline);
        }
    }

    private void drawPolygon(float[] points, Color color) {
        Paint paint = Skia.scratchPaint();
        paint.setColor(color.getRGB());
        try (PathBuilder builder = new PathBuilder();
             Path path = builder.addPolygon(points, true).build()) {
            Skia.getCanvas().drawPath(path, paint);
        }
    }

    private String formatScaffoldBlocks(int blocks) {
        return blocks + " Blocks";
    }

    private Color shadeBlockColor(int rgb, float factor, int alpha) {
        int red = Math.max(0, Math.min(255, Math.round(((rgb >>> 16) & 0xFF) * factor)));
        int green = Math.max(0, Math.min(255, Math.round(((rgb >>> 8) & 0xFF) * factor)));
        int blue = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * factor)));
        return new Color(red, green, blue, alpha);
    }

    private static String safeNotebotText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String formatRemaining(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private float measureRemainingWidth(Font font) {
        String reference = formatRemaining(Math.max(0, Service.getSongLength()));
        if (reference.isBlank()) {
            reference = "0:00";
        }
        return Math.max(Skia.getTextBounds("0:00", font).getWidth(), Skia.getTextBounds(reference, font).getWidth());
    }

    private record BedLayout(float width, float height, float radius) {
    }

    private record BedStatus(float progress) {
    }

    private record NotebotLayout(float width, float height, float radius) {
    }

    private record ScaffoldLayout(float width, float height, float radius) {
    }

    private record MusicLayout(float width, float height, float radius) {
    }

    private record MusicTextLayout(float width, Font font) {
    }

    private record WordHighlightMetrics(float base, float width) {
    }

    private record ScaffoldStatus(int remainingBlocks, int blockColor) {
    }
}
