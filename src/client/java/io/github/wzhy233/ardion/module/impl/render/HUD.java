package io.github.wzhy233.ardion.module.impl.render;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.config.HudConfig;
import io.github.wzhy233.ardion.event.impl.render.Render2DEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.gui.hud.HudPaint;
import io.github.wzhy233.ardion.gui.hud.island.DynamicIsland;
import io.github.wzhy233.ardion.gui.hud.island.Slot;
import io.github.wzhy233.ardion.gui.hud.island.TabIsland;
import io.github.wzhy233.ardion.gui.hud.list.ModuleList;
import io.github.wzhy233.ardion.gui.notification.Notification;
import io.github.wzhy233.ardion.gui.shader.Effects;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.combat.KillAura;
import io.github.wzhy233.ardion.module.impl.combat.TriggerBot;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.player.PlayerUtil;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.util.time.SmoothAnimationTimer;
import io.github.wzhy233.ardion.util.time.TimeUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class HUD extends Module {
    private final io.github.wzhy233.ardion.gui.ui.UiState ui = new io.github.wzhy233.ardion.gui.ui.UiState();
    private final io.github.wzhy233.ardion.gui.ui.UiTransition targetVisibility = new io.github.wzhy233.ardion.gui.ui.UiTransition(0);
    private static final long TARGET_HUD_KEEP_VISIBLE_MS = 3000L;
    private static final long TARGET_HUD_HEALTH_CHANGE_KEEP_MS = 900L;
    private static final float TARGET_HUD_HEALTH_ANIMATION_SPEED = 0.35F;
    private static final DecimalFormat TARGET_HUD_HEALTH_FORMAT = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat TARGET_HUD_DIFF_FORMAT = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private static HUD instance;
    private final ModuleList moduleList = new ModuleList();
    private final DynamicIsland island = new DynamicIsland();
    private String infoLine1;
    private String infoLine2Base;
    private final SmoothAnimationTimer targetHudHealthAnimation = new SmoothAnimationTimer(0.0F);
    private float targetHudAlpha = 0.0F;
    private float targetHudAlphaTarget = 0.0F;
    private LivingEntity targetHudLastTarget;
    private float targetHudLastMaxHealth;
    private float targetHudLastTrackedHealth;
    private float targetHudLastHealthChange;
    private long targetHudLastHealthChangeTime;
    private long targetHudLastSeenTime;
    private String targetHudCachedHeadTexturePath;
    private NativeImage targetHudCachedHeadSource;
    private Image targetHudCachedHeadImage;

    public HUD() {
        super("hud", ModuleCategory.RENDER);
        instance = this;
        targetHudHealthAnimation.speed = TARGET_HUD_HEALTH_ANIMATION_SPEED;
    }

    public static void postNotification(Notification notification) {
        if (instance != null) instance.island.post(notification);
    }

    public static void postIslandSlot(Slot slot) {
        if (instance != null) instance.island.post(slot);
    }

    public static void showTabIsland(TabIsland.Snapshot snapshot) {
        if (instance != null) instance.island.showTab(snapshot);
    }

    public static void hideTabIsland() {
        if (instance != null) instance.island.hideTab();
    }

    public void onRender(Render2DEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            ui.reset();
            clearTargetHeadCache();
            return;
        }
        if (client.currentScreen instanceof io.github.wzhy233.ardion.gui.screen.HudEditorScreen) return;
        float opacity = ui.visible("root", isEnabled() && !shouldSkipHudRender(client));
        if (opacity == 0) {
            if (!isEnabled()) clearTargetHeadCache();
            return;
        }
        Skia.query(canvas -> {
            try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, null, opacity)) {
            island.render(event.getScaledWidth(), event.getScaledHeight());
            renderXyz();
            renderArrayList(event.getScaledWidth());
            renderInfo(event.getScaledWidth(), event.getScaledHeight());
            renderTargetHud(event.getDrawContext(), event.getScaledWidth(), event.getScaledHeight());
            }
        });
    }


    private boolean shouldSkipHudRender(MinecraftClient client) {
        if (client.options.hudHidden) return true;
        if (client.getDebugHud().shouldShowDebugHud()
                || client.getDebugHud().shouldShowRenderingChart()
                || client.getDebugHud().shouldShowPacketSizeAndPingCharts()
                || client.getDebugHud().shouldRenderTickCharts()) {
            return true;
        }
        return client.currentScreen != null && !HudSetting.renderInGui();
    }

    public Bounds renderXyz() {
        return fade("xyz", HudConfig.isXyzVisible(), this::drawXyz);
    }

    private Bounds drawXyz() {
        if (!PlayerUtil.isInGame()) return Bounds.empty(xyzX(), xyzY());
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return Bounds.empty(xyzX(), xyzY());

        String pos = "X: " + MathHelper.floor(player.getX())
                + " Y: " + MathHelper.floor(player.getY())
                + " Z: " + MathHelper.floor(player.getZ());
        return drawScaledText(pos, xyzX(), xyzY(), ColorHelper.getWhite(1), xyzScale());
    }

    public Bounds renderArrayList(int screenWidth) {
        return fade("array-list", HudConfig.isArrayListVisible() && HudSetting.moduleList(), () -> moduleList.render(screenWidth));
    }

    public Bounds renderInfo(int width, int height) {
        return fade("info", HudConfig.isInfoVisible(), () -> drawInfo(width, height));
    }

    private Bounds fade(String key, boolean visible, java.util.function.Supplier<Bounds> drawing) {
        float opacity = ui.visible(key, visible);
        if (opacity == 0) return Bounds.empty(0, 0);
        try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(Skia.getCanvas(), null, opacity)) {
            return drawing.get();
        }
    }

    private Bounds drawInfo(int width, int height) {
        ensureInfoText();

        String line2 = infoLine2Base + TimeUtil.clock();
        Font font = Fonts.getMedium(14.0F);
        Rect line1Bounds = Skia.getTextBounds(infoLine1, font);
        Rect line2Bounds = Skia.getTextBounds(line2, font);
        float scale = infoScale();
        float fontHeight = Math.max(line1Bounds.getHeight(), line2Bounds.getHeight());
        float lineHeight = (fontHeight + 2.0F) * scale;
        float maxLineWidth = Math.max(line1Bounds.getWidth(), line2Bounds.getWidth()) * scale;
        float x = width - infoX() - maxLineWidth;
        float y = height - infoY() - fontHeight * scale - lineHeight;

        drawScaledText(infoLine1, x + maxLineWidth - line1Bounds.getWidth() * scale, y, ColorHelper.getWhite(1), scale);
        drawScaledText(line2, x + maxLineWidth - line2Bounds.getWidth() * scale, y + lineHeight, ColorHelper.getWhite(1), scale);
        return new Bounds(x, y, maxLineWidth, fontHeight * scale + lineHeight);
    }

    public Bounds renderTargetHudPreview(DrawContext context, LivingEntity target, int screenWidth, int screenHeight) {
        if (target == null) {
            return Bounds.empty(targetHudX(), targetHudY());
        }
        Bounds bounds = measureTargetHudPreview(target, screenWidth, screenHeight);
        fade("target-preview", isTargetHudVisible(), () -> drawTargetHudPreview(target, screenWidth, screenHeight));
        return bounds;
    }

    public Bounds renderTargetHud(DrawContext context, int screenWidth, int screenHeight) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null || mc.world == null) {
            setTargetHudAlphaTarget(0.0F);
            updateTargetHudAlpha();
            return Bounds.empty(targetHudX(), targetHudY());
        }

        LivingEntity sourceTarget = null;
        KillAura killAura = ModuleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            sourceTarget = killAura.getTarget();
        }
        TriggerBot triggerBot = ModuleManager.getModule(TriggerBot.class);
        if (sourceTarget == null && triggerBot != null && triggerBot.isEnabled() && triggerBot.getCurrentTarget() instanceof LivingEntity living) {
            sourceTarget = living;
        }
        if (sourceTarget != null) {
            updateTargetHudSnapshot(sourceTarget);
        }

        boolean keepVisible = HudSetting.targetHud() && targetHudLastTarget != null
                && System.currentTimeMillis() - targetHudLastSeenTime <= TARGET_HUD_KEEP_VISIBLE_MS;
        setTargetHudAlphaTarget(keepVisible ? 1.0F : 0.0F);
        updateTargetHudAlpha();
        if (targetHudAlpha < 0.01F && targetHudAlphaTarget == 0.0F) {
            return Bounds.empty(targetHudX(), targetHudY());
        }

        LivingEntity target = targetHudLastTarget;
        if (target == null) {
            return Bounds.empty(targetHudX(), targetHudY());
        }
        return drawTargetHudLive(target, screenWidth, screenHeight);
    }

    public boolean isTargetHudVisible() {
        return HudSetting.targetHud();
    }

    public void setTargetHudVisible(boolean visible) {
        HudSetting.setTargetHud(visible);
    }

    public void setTargetHud(float xFromCenter, float yFromCenter, float scale) {
        HudConfig.setTargetHud(xFromCenter, yFromCenter, scale);
    }

    public void resetTargetHud() {
        HudConfig.resetTargetHud();
    }

    public float targetHudX() {
        return HudConfig.targetHudX();
    }

    public float targetHudY() {
        return HudConfig.targetHudY();
    }

    public float targetHudScale() {
        return HudConfig.targetHudScale();
    }

    private Bounds measureTargetHudPreview(LivingEntity target, int screenWidth, int screenHeight) {
        String targetName = safeText(target.getName().getString());
        if (targetName.isBlank()) targetName = "Target";
        float realHealth = getTargetHudDisplayHealth(target);
        String healthText = formatTargetHudHealthText(realHealth);
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        float viewerHealth = getTargetHudDisplayHealth(viewer);
        String statusText = safeText(viewerHealth == realHealth
                ? "D" : (realHealth < viewerHealth ? "W" : "L"));
        return measureTargetHud(targetName, healthText, "", statusText, target, screenWidth, screenHeight);
    }

    private Bounds drawTargetHudPreview(LivingEntity target, int screenWidth, int screenHeight) {
        String targetName = safeText(target.getName().getString());
        if (targetName.isBlank()) targetName = "Target";
        float realHealth = getTargetHudDisplayHealth(target);
        float maxHealth = Math.max(getTargetHudDisplayMaxHealth(target), 0.5F);
        String healthText = formatTargetHudHealthText(realHealth);
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        float viewerHealth = getTargetHudDisplayHealth(viewer);
        String statusText = safeText(viewerHealth == realHealth
                ? "D" : (realHealth < viewerHealth ? "W" : "L"));
        String healthChangeText = "";
        String initial = initialOf(targetName);
        return drawTargetHudSkia(targetName, healthText, healthChangeText, statusText, initial,
                screenWidth / 2.0F + targetHudX(), screenHeight / 2.0F + targetHudY(),
                realHealth / Math.max(maxHealth, 0.5F), targetHudScale(), target, 1.0F);
    }

    private Bounds measureTargetHud(String targetName, String healthText, String healthChangeText, String statusText,
                                    LivingEntity target, int screenWidth, int screenHeight) {
        Font nameFont = Fonts.getMedium(14.0F);
        Font smallFont = Fonts.getMedium(11.0F);
        float nameWidth = Skia.getTextBounds(targetName, nameFont).getWidth();
        float healthWidth = Skia.getTextBounds(healthText, smallFont).getWidth();
        float statusWidth = Skia.getTextBounds(statusText, smallFont).getWidth();
        float diffWidth = Skia.getTextBounds(healthChangeText, smallFont).getWidth();
        boolean showHead = HudConfig.targetHudHead() && target instanceof PlayerEntity;
        float headOffset = showHead ? 36.0F : 0.0F;
        float contentWidth = Math.max(nameWidth + (HudConfig.targetHudIndicator() ? 4.0F + statusWidth : 0.0F),
                healthWidth + (HudConfig.targetHudIndicator() ? 4.0F + diffWidth : 0.0F));
        float hudWidth = Math.max(headOffset + 84.0F, headOffset + 10.0F + contentWidth);
        float hudHeight = 38.0F;
        float scale = targetHudScale();
        float centerX = screenWidth / 2.0F + targetHudX();
        float centerY = screenHeight / 2.0F + targetHudY();
        return new Bounds(centerX - hudWidth * scale / 2.0F, centerY - hudHeight * scale / 2.0F,
                hudWidth * scale, hudHeight * scale);
    }

    private Bounds drawTargetHudLive(LivingEntity target, int screenWidth, int screenHeight) {
        float targetHealth = getTargetHudDisplayHealth(target);

        targetHudHealthAnimation.target = targetHealth;
        targetHudHealthAnimation.update(true);
        float animatedHealth = targetHudHealthAnimation.value;
        float healthRatio = Math.max(0.0F, Math.min(1.0F, animatedHealth / Math.max(targetHudLastMaxHealth, 0.5F)));

        String targetName = safeText(target.getName().getString());
        if (targetName.isBlank()) targetName = "Target";
        String healthText = formatTargetHudHealthText(animatedHealth);
        String healthChangeText = System.currentTimeMillis() - targetHudLastHealthChangeTime <= TARGET_HUD_HEALTH_CHANGE_KEEP_MS && Math.abs(targetHudLastHealthChange) >= 0.05F
                ? safeText(TARGET_HUD_DIFF_FORMAT.format(targetHudLastHealthChange))
                : "";
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        float playerHealth = getTargetHudDisplayHealth(viewer);
        String statusText = safeText(targetHealth == playerHealth ? "D" : (targetHealth < playerHealth ? "W" : "L"));
        String initial = initialOf(targetName);

        return drawTargetHudSkia(targetName, healthText, healthChangeText, statusText, initial,
                screenWidth / 2.0F + targetHudX(), screenHeight / 2.0F + targetHudY(),
                healthRatio, targetHudScale(), target, targetHudAlpha);
    }

    private Bounds drawTargetHudSkia(String targetName, String healthText, String healthChangeText, String statusText,
                                     String initial, float drawX, float drawY, float healthRatio, float scaleValue,
                                     LivingEntity target, float alpha) {
        Font nameFont = Fonts.getMedium(14.0F);
        Font smallFont = Fonts.getMedium(11.0F);
        float nameWidth = Skia.getTextBounds(targetName, nameFont).getWidth();
        float healthWidth = Skia.getTextBounds(healthText, smallFont).getWidth();
        float statusWidth = Skia.getTextBounds(statusText, smallFont).getWidth();
        float diffWidth = Skia.getTextBounds(healthChangeText, smallFont).getWidth();

        boolean showHead = HudConfig.targetHudHead() && target instanceof PlayerEntity;
        float faceSize = showHead ? 28.0F : 0.0F;
        float headOffset = showHead ? faceSize + 8.0F : 0.0F;
        float contentWidth = Math.max(nameWidth + (HudConfig.targetHudIndicator() ? 4.0F + statusWidth : 0.0F),
                healthWidth + (HudConfig.targetHudIndicator() ? 4.0F + diffWidth : 0.0F));
        float hudWidth = Math.max(headOffset + 84.0F, headOffset + 10.0F + contentWidth);
        float hudHeight = 38.0F;

        Color targetColor = getTargetColor(target);
        Color barColor = getHealthColor(healthRatio);
        Color statusColor = getStatusColor(getTargetHudDisplayHealth(target), getTargetHudDisplayHealth(MinecraftClient.getInstance().player));
        Color healthChangeColor = getChangeColor(targetHudLastHealthChange);
        int bgAlpha = Math.round(HudSetting.backgroundAlpha() * alpha);
        boolean indicator = HudConfig.targetHudIndicator();
        boolean outline = HudConfig.targetHudOutline();
        boolean shadow = HudConfig.targetHudShadow();
        float radius = 7.0F;

        drawX -= hudWidth * scaleValue / 2.0F;
        drawY -= hudHeight * scaleValue / 2.0F;

        if (HudSetting.blur() && alpha > 0.01F) {
            float blurWidth = hudWidth * scaleValue;
            float blurHeight = hudHeight * scaleValue;
            float blurRadius = radius * scaleValue;
            Rect blurBounds = Rect.makeXYWH(drawX, drawY, blurWidth, blurHeight);
            Skia.save();
            Skia.clip(drawX, drawY, blurWidth, blurHeight, blurRadius);
            if (HudSetting.blurSksl()) {
                Effects.blurBackdropSKSL(Skia.getCanvas(), Skia.getSurface(), blurBounds,
                        HudSetting.blurSigma(), HudSetting.skslShader());
            } else {
                Effects.blurBackdrop(Skia.getCanvas(), blurBounds, HudSetting.blurSigma());
            }
            Skia.restore();
        }

        Skia.save();
        Skia.translate(drawX, drawY);
        Skia.scale(scaleValue);
        if (bgAlpha > 0 && shadow) Skia.drawShadow(0.0F, 0.0F, hudWidth, hudHeight, radius);
        HudPaint.renderHudCard(0.0F, 0.0F, hudWidth, hudHeight, radius, targetColor, bgAlpha);
        if (outline) {
            Skia.drawOutline(0.5F, 0.5F, hudWidth - 1.0F, hudHeight - 1.0F, radius - 1.0F, 1.0F,
                    alphaColor(targetColor, Math.round(220.0F * alpha)));
        }

        float barStart = headOffset + 4.0F;
        float barEnd = hudWidth - 4.0F;
        float filledBarEnd = barStart + (barEnd - barStart) * healthRatio;
        Skia.drawRoundedRect(barStart, 27.0F, barEnd - barStart, 5.0F, 2.5F,
                alphaColor(darker(barColor, 0.45F), Math.round(170.0F * alpha)));
        Skia.drawRoundedRect(barStart, 27.0F, Math.max(0.0F, filledBarEnd - barStart), 5.0F, 2.5F,
                alphaColor(barColor, Math.round(245.0F * alpha)));

        if (showHead) drawTargetHead(target, initial, 4.0F, 4.0F, faceSize, alpha);

        drawSkiaText(targetName, headOffset + 4.0F, 5.0F, nameFont, Color.WHITE, alpha);
        drawSkiaText(healthText, headOffset + 4.0F, 17.0F, smallFont, Color.WHITE, alpha);

        if (indicator) {
            Rect statusBounds = Skia.getTextBounds(statusText, smallFont);
            drawSkiaText(statusText, hudWidth - 4.0F - statusBounds.getWidth(), 6.0F, smallFont, statusColor, alpha);
            if (!healthChangeText.isBlank()) {
                Rect diffBounds = Skia.getTextBounds(healthChangeText, smallFont);
                drawSkiaText(healthChangeText, hudWidth - 4.0F - diffBounds.getWidth(), 17.0F,
                        smallFont, healthChangeColor, alpha);
            }
        }

        Skia.restore();
        return new Bounds(drawX, drawY, hudWidth * scaleValue, hudHeight * scaleValue);
    }

    private void updateTargetHudAlpha() {
        targetHudAlpha = targetVisibility.to(targetHudAlphaTarget, System.nanoTime(),
                (targetHudAlphaTarget > 0 ? io.github.wzhy233.ardion.gui.ui.UiManager.ENTER_MS
                        : io.github.wzhy233.ardion.gui.ui.UiManager.EXIT_MS) * 1_000_000L);
    }

    private void setTargetHudAlphaTarget(float alpha) {
        targetHudAlphaTarget = Math.clamp(alpha, 0, 1);
    }
    private void updateTargetHudSnapshot(LivingEntity target) {
        float targetHealth = getTargetHudDisplayHealth(target);
        float targetMaxHealth = Math.max(getTargetHudDisplayMaxHealth(target), 0.5F);
        if (target != targetHudLastTarget) {
            targetHudHealthAnimation.value = targetHealth;
            targetHudHealthAnimation.target = targetHealth;
            targetHudLastMaxHealth = targetMaxHealth;
            targetHudLastTrackedHealth = targetHealth;
            targetHudLastHealthChange = 0.0F;
            targetHudLastHealthChangeTime = 0L;
        } else {
            targetHudLastMaxHealth = targetMaxHealth;
            float healthChange = targetHealth - targetHudLastTrackedHealth;
            if (Math.abs(healthChange) >= 0.05F) {
                targetHudLastHealthChange = healthChange;
                targetHudLastHealthChangeTime = System.currentTimeMillis();
                targetHudLastTrackedHealth = targetHealth;
            }
        }
        targetHudLastTarget = target;
        targetHudLastSeenTime = System.currentTimeMillis();
    }

    private float getTargetHudDisplayHealth(LivingEntity entity) {
        if (entity == null) return 0.0F;
        if (entity instanceof PlayerEntity player) return PlayerUtil.getDisplayHealth(player);
        return entity.getHealth();
    }

    private float getTargetHudDisplayMaxHealth(LivingEntity entity) {
        if (entity == null) return 0.5F;
        if (entity instanceof PlayerEntity player) return PlayerUtil.getDisplayMaxHealth(player);
        return Math.max(entity.getMaxHealth(), 0.5F);
    }

    private void drawTargetHead(LivingEntity target, String initial, float x, float y, float size, float alpha) {
        if (target instanceof AbstractClientPlayerEntity player && drawSkiaPlayerHead(player, x, y, size, alpha)) {
            return;
        }

        drawFallbackHead(initial, x, y, size, alpha);
    }

    private boolean drawSkiaPlayerHead(AbstractClientPlayerEntity player, float x, float y, float size, float alpha) {
        String texturePath = player.getSkin().body().texturePath().toString();
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(player.getSkin().body().texturePath());
        if (!(texture instanceof NativeImageBackedTexture nativeTexture)) {
            return false;
        }

        NativeImage image = nativeTexture.getImage();
        if (image == null) {
            return false;
        }

        Image skinImage = this.cachedTargetHeadImage(texturePath, image);
        if (skinImage == null) {
            return false;
        }

        Rect faceSrc = Rect.makeXYWH(PlayerSkinDrawer.FACE_X, PlayerSkinDrawer.FACE_Y, PlayerSkinDrawer.FACE_WIDTH, PlayerSkinDrawer.FACE_HEIGHT);
        Rect overlaySrc = Rect.makeXYWH(PlayerSkinDrawer.FACE_OVERLAY_X, PlayerSkinDrawer.FACE_OVERLAY_Y, PlayerSkinDrawer.FACE_WIDTH, PlayerSkinDrawer.FACE_HEIGHT);
        Rect faceDst = Rect.makeXYWH(x, y, size, size);

        Paint paint = Skia.scratchPaint();
        paint.setAlphaf(alpha);
        Skia.save();
        try {
            Skia.clip(x, y, size, size, 5.0F);
            Skia.getCanvas().drawImageRect(skinImage, faceSrc, faceDst, paint);
            Skia.getCanvas().drawImageRect(skinImage, overlaySrc, faceDst, paint);
        } finally {
            Skia.restore();
        }
        return true;
    }

    private Image toSkiaImage(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        int[] pixels = image.copyPixelsArgb();
        byte[] bytes = new byte[pixels.length * 4];
        for (int i = 0, j = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            bytes[j++] = (byte) argb;
            bytes[j++] = (byte) (argb >> 8);
            bytes[j++] = (byte) (argb >> 16);
            bytes[j++] = (byte) (argb >> 24);
        }
        return Image.makeRasterFromBytes(ImageInfo.makeN32Premul(width, height), bytes, width * 4L);
    }

    private Image cachedTargetHeadImage(String texturePath, NativeImage sourceImage) {
        if (texturePath.equals(this.targetHudCachedHeadTexturePath)
                && this.targetHudCachedHeadSource == sourceImage
                && this.targetHudCachedHeadImage != null) {
            return this.targetHudCachedHeadImage;
        }

        this.clearTargetHeadCache();
        Image converted = this.toSkiaImage(sourceImage);
        if (converted == null) {
            return null;
        }

        this.targetHudCachedHeadTexturePath = texturePath;
        this.targetHudCachedHeadSource = sourceImage;
        this.targetHudCachedHeadImage = converted;
        return converted;
    }

    private void clearTargetHeadCache() {
        this.targetHudCachedHeadTexturePath = null;
        this.targetHudCachedHeadSource = null;
        if (this.targetHudCachedHeadImage != null) {
            this.targetHudCachedHeadImage.close();
            this.targetHudCachedHeadImage = null;
        }
    }

    private void drawFallbackHead(String initial, float x, float y, float size, float alpha) {
        Skia.drawRoundedRect(x, y, size, size, 5.0F, new Color(255, 255, 255, Math.round(34.0F * alpha)));
        Skia.drawOutline(x + 0.5F, y + 0.5F, size - 1.0F, size - 1.0F, 4.0F, 1.0F,
                new Color(255, 255, 255, Math.round(60.0F * alpha)));
        Font font = Fonts.getMedium(16.0F);
        Rect bounds = Skia.getTextBounds(initial, font);
        Paint paint = Skia.scratchPaint();
        paint.setColor(Color.WHITE.getRGB());
        paint.setAlphaf(alpha);
        Skia.drawText(initial, x + (size - bounds.getWidth()) / 2.0F,
                y + (size - bounds.getHeight()) / 2.0F, font, paint);
    }

    private void drawSkiaText(String text, float x, float y, Font font, Color color, float alpha) {
        Paint paint = Skia.scratchPaint();
        paint.setColor(color.getRGB());
        paint.setAlphaf(alpha * (color.getAlpha() / 255.0F));
        Skia.drawText(text, x, y, font, paint);
    }

    private Color getTargetColor(LivingEntity entity) {
        return Color.WHITE;
    }

    private Color getHealthColor(float ratio) {
        ratio = Math.max(0.0F, Math.min(1.0F, ratio));
        if (ratio > 0.5F) {
            return getGradient(new Color(255, 204, 0), new Color(70, 220, 70), (ratio - 0.5F) / 0.5F);
        }
        return getGradient(new Color(255, 70, 70), new Color(255, 204, 0), ratio / 0.5F);
    }

    private Color getStatusColor(float targetHealth, float playerHealth) {
        if (targetHealth < playerHealth) return new Color(70, 220, 70);
        if (targetHealth > playerHealth) return new Color(255, 70, 70);
        return Color.WHITE;
    }

    private Color getChangeColor(float healthChange) {
        if (healthChange > 0.0F) return new Color(70, 220, 70);
        if (healthChange < 0.0F) return new Color(255, 70, 70);
        return Color.WHITE;
    }

    private Color darker(Color color, float factor) {
        factor = Math.max(0.0F, Math.min(1.0F, factor));
        return new Color(
                Math.max((int) (color.getRed() * (1.0F - factor)), 0),
                Math.max((int) (color.getGreen() * (1.0F - factor)), 0),
                Math.max((int) (color.getBlue() * (1.0F - factor)), 0),
                color.getAlpha()
        );
    }

    private Color getGradient(Color start, Color end, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * t);
        int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * t);
        int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * t);
        return new Color(r, g, b);
    }

    private Color alphaColor(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private String formatTargetHudHealthText(float health) {
        return safeText(TARGET_HUD_HEALTH_FORMAT.format(health));
    }

    private static String safeText(String value) {
        return Skia.sanitizeText(value);
    }

    private static String initialOf(String text) {
        if (text == null || text.isEmpty()) return "?";
        char first = text.charAt(0);
        return Character.isSurrogate(first) ? "?" : String.valueOf(Character.toUpperCase(first));
    }

    private float easeOutCubic(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    public void setXyz(float x, float y, float scale) {
        HudConfig.setXyz(x, y, scale);
    }

    public void setArrayList(float x, float y, float scale) {
        HudConfig.setArrayList(x, y, scale);
    }

    public void setArrayList(float x, float y, float scale, boolean leftAligned) {
        HudConfig.setArrayList(x, y, scale, leftAligned);
    }

    public void setInfo(float xFromRight, float yFromBottom, float scale) {
        HudConfig.setInfo(xFromRight, yFromBottom, scale);
    }

    public void resetXyz() {
        HudConfig.resetXyz();
    }

    public void resetArrayList() {
        HudConfig.resetArrayList();
    }

    public void resetInfo() {
        HudConfig.resetInfo();
    }

    public boolean isXyzVisible() {
        return HudConfig.isXyzVisible();
    }

    public boolean isArrayListVisible() {
        return HudConfig.isArrayListVisible();
    }

    public boolean isInfoVisible() {
        return HudConfig.isInfoVisible();
    }

    public void setXyzVisible(boolean visible) {
        HudConfig.setXyzVisible(visible);
    }

    public void setArrayListVisible(boolean visible) {
        HudConfig.setArrayListVisible(visible);
    }

    public void setInfoVisible(boolean visible) {
        HudConfig.setInfoVisible(visible);
    }

    public float xyzX() {
        return HudConfig.xyzX();
    }

    public float xyzY() {
        return HudConfig.xyzY();
    }

    public float xyzScale() {
        return HudConfig.xyzScale();
    }

    public float arrayX() {
        return HudConfig.arrayListX();
    }

    public float arrayY() {
        return HudConfig.arrayListY();
    }

    public float arrayScale() {
        return HudConfig.arrayListScale();
    }

    public float infoX() {
        return HudConfig.infoX();
    }

    public float infoY() {
        return HudConfig.infoY();
    }

    public float infoScale() {
        return HudConfig.infoScale();
    }

    private void ensureInfoText() {
        if (infoLine1 != null) return;
        String game = SharedConstants.getGameVersion().name();
        String fabric = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        infoLine1 = "Minecraft " + game + ", Fabric " + fabric;
        infoLine2Base = "OpenArdion, ";
    }

    private static Bounds drawScaledText(String text, float x, float y, int color, float scale) {
        Font font = Fonts.getMedium(14.0F);
        Rect bounds = Skia.getTextBounds(text, font);
        Skia.save();
        Skia.getCanvas().translate(x, y);
        Skia.getCanvas().scale(scale, scale);
        Paint shadow = Skia.scratchPaint();
        shadow.setColor(new Color(0, 0, 0, 120).getRGB());
        Skia.drawText(text, 1.0F, 1.0F, font, shadow);
        Paint paint = Skia.scratchPaint();
        paint.setColor(color);
        Skia.drawText(text, 0.0F, 0.0F, font, paint);
        Skia.restore();
        return new Bounds(x, y, bounds.getWidth() * scale, bounds.getHeight() * scale);
    }

    public record Bounds(float x, float y, float width, float height) {
        public static Bounds empty(float x, float y) {
            return new Bounds(x, y, 1.0F, 1.0F);
        }

        public static Bounds text(float x, float y, int width, int height, float scale) {
            return new Bounds(x, y, width * scale, height * scale);
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
