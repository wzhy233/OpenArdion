package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.event.impl.render.Render2DEvent;
import io.github.wzhy233.ardion.event.impl.render.Render3DEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.misc.AntiBot;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.TargetSetting;
import io.github.wzhy233.ardion.util.player.PlayerUtil;
import io.github.wzhy233.ardion.util.render.Render3DUtil;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ColorValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ESP extends Module {
    private static final Color TEAMMATE_COLOR = new Color(75, 145, 255);
    private static final Color ENEMY_COLOR = new Color(255, 80, 80);
    private static final Color BOT_COLOR = new Color(255, 70, 255);
    private static final Color OUTLINE_BLACK = new Color(0, 0, 0, 190);

    private final ModeValue mode = this.registerValue(new ModeValue("mode", this, "3d",
            "none", "2d", "3d", "fake2d"));
    private final ModeValue colorMode = this.registerValue(new ModeValue("color", this, "default",
            "default", "custom"));
    private final ModeValue healthBar = this.registerValue(new ModeValue("health-bar", this, "none",
            "none", "2d", "raven"));
    private final ColorValue customColor = this.registerValue(new ColorValue("custom-color", this,
            new Color(90, 190, 255).getRGB(), () -> this.colorMode.is("custom")));
    private final BooleanValue players = this.registerValue(new BooleanValue("players", this, true));
    private final BooleanValue teammates = this.registerValue(new BooleanValue("teammates", this, false,
            TargetSetting::teamCheck));
    private final BooleanValue self = this.registerValue(new BooleanValue("self", this, false));
    private final BooleanValue bots = this.registerValue(new BooleanValue("bots", this, false));
    private final BooleanValue invisibles = this.registerValue(new BooleanValue("invisibles", this, true));
    private final BooleanValue filled = this.registerValue(new BooleanValue("filled", this, false,
            () -> this.mode.is("3d")));
    private final BooleanValue outline = this.registerValue(new BooleanValue("box-outline", this, true,
            () -> this.mode.is("3d")));
    private final NumberValue alpha = this.registerValue(new NumberValue("alpha", this,
            0.18D, 0.02D, 1.0D));
    private List<PlayerEntity> cachedPlayers = List.of();
    private ClientWorld cachedPlayersWorld;
    private int cachedPlayersAge = Integer.MIN_VALUE;
    private int cachedPlayersTickDeltaBits = Integer.MIN_VALUE;

    public ESP() {
        super("esp", ModuleCategory.RENDER);
    }

    @Listener
    public void onRender2D(Render2DEvent event) {
        if ((!this.mode.isAny("2d", "fake2d") && !this.healthBar.is("2d"))
                || MinecraftClient.getInstance().options.hudHidden) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        List<PlayerEntity> renderedPlayers = this.collectPlayers(client, event.getTickDelta());
        if (renderedPlayers.isEmpty()) {
            return;
        }

        Skia.query(canvas -> {
            for (PlayerEntity player : renderedPlayers) {
                ScreenBox box = this.projectBox(client, this.interpolatedBox(player, event.getTickDelta()),
                        event.getScaledWidth(), event.getScaledHeight());
                if (box == null) {
                    continue;
                }

                Color color = this.getEntityColor(client, player, 255);
                if (this.mode.is("2d")) {
                    this.draw2DBox(box, color);
                } else if (this.mode.is("fake2d")) {
                    this.draw2DCorners(box, color);
                }

                if (this.healthBar.is("2d")) {
                    this.draw2DHealthBar(box, this.healthRatio(player));
                }
            }
        });
    }

    @Listener
    public void onRender3D(Render3DEvent event) {
        if (!this.mode.is("3d") && !this.healthBar.is("raven")) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getCameraEntity() == null) {
            return;
        }

        List<PlayerEntity> renderedPlayers = this.collectPlayers(client, event.getTickDelta());
        if (renderedPlayers.isEmpty()) {
            return;
        }

        var matrices = event.getMatrices();
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        boolean shouldFlushOutline = false;

        matrices.push();
        try {
            for (PlayerEntity player : renderedPlayers) {
                Box shiftedBox = this.interpolatedBox(player, event.getTickDelta())
                        .offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Color color = this.getEntityColor(client, player, 255);

                if (this.mode.is("3d")) {
                    Render3DUtil.drawBox(matrices, event.getConsumers(), shiftedBox,
                            color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F,
                            this.alpha.getValue().floatValue(), this.filled.getValue(), this.outline.getValue());
                    shouldFlushOutline |= this.outline.getValue();
                }

                if (this.healthBar.is("raven")) {
                    this.draw3DHealthBar(matrices, event, shiftedBox, this.healthRatio(player));
                }
            }
        } finally {
            matrices.pop();
        }

        if (shouldFlushOutline) {
            Render3DUtil.flushOutline();
        }
    }

    private List<PlayerEntity> collectPlayers(MinecraftClient client, float tickDelta) {
        List<PlayerEntity> result = new ArrayList<>();
        if (client.world == null || client.player == null) {
            return List.of();
        }

        int age = client.player.age;
        int tickDeltaBits = Float.floatToIntBits(tickDelta);
        if (client.world == this.cachedPlayersWorld
                && this.cachedPlayersAge == age
                && this.cachedPlayersTickDeltaBits == tickDeltaBits) {
            return this.cachedPlayers;
        }

        for (PlayerEntity player : client.world.getPlayers()) {
            if (this.shouldRenderPlayer(client, player)) {
                result.add(player);
            }
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        result.sort(Comparator.comparingDouble((PlayerEntity player) -> player.squaredDistanceTo(cameraPos)).reversed());
        this.cachedPlayers = List.copyOf(result);
        this.cachedPlayersWorld = client.world;
        this.cachedPlayersAge = age;
        this.cachedPlayersTickDeltaBits = tickDeltaBits;
        return this.cachedPlayers;
    }

    private boolean shouldRenderPlayer(MinecraftClient client, PlayerEntity player) {
        if (client == null || player == null || client.player == null) {
            return false;
        }
        if (player.isRemoved() || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (player.isInvisible() && !this.invisibles.getValue()) {
            return false;
        }
        if (client.player.squaredDistanceTo(player) > 512.0D * 512.0D) {
            return false;
        }

        boolean selfPlayer = player == client.player || player == client.getCameraEntity();
        if (selfPlayer) {
            return this.self.getValue() && !client.options.getPerspective().isFirstPerson();
        }

        if (this.isBot(player)) {
            return this.bots.getValue();
        }
        if (TargetSetting.teamCheck() && PlayerUtil.isTeammate(client.player, player)) {
            return this.teammates.getValue();
        }
        return this.players.getValue();
    }

    private Color getEntityColor(MinecraftClient client, PlayerEntity player, int alpha) {
        if (this.isBot(player)) {
            return withAlpha(BOT_COLOR, alpha);
        }
        if (client.player != null && TargetSetting.teamCheck() && PlayerUtil.isTeammate(client.player, player)) {
            return withAlpha(TEAMMATE_COLOR, alpha);
        }

        return switch (this.colorMode.getValue().toLowerCase()) {
            case "custom" -> withAlpha(this.customColor.getValue(), alpha);
            default -> PlayerUtil.getTeamColor(player, ENEMY_COLOR, alpha);
        };
    }

    private Box interpolatedBox(PlayerEntity player, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, player.lastX, player.getX());
        double y = MathHelper.lerp(tickDelta, player.lastY, player.getY());
        double z = MathHelper.lerp(tickDelta, player.lastZ, player.getZ());
        return player.getBoundingBox()
                .offset(x - player.getX(), y - player.getY(), z - player.getZ())
                .expand(0.04D, 0.08D, 0.04D);
    }

    private ScreenBox projectBox(MinecraftClient client, Box box, int screenWidth, int screenHeight) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int visibleCorners = 0;

        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    Vec3d world = new Vec3d(x == 0 ? box.minX : box.maxX,
                            y == 0 ? box.minY : box.maxY,
                            z == 0 ? box.minZ : box.maxZ);
                    Vec3d projected = client.gameRenderer.project(world);
                    if (!isFinite(projected.x) || !isFinite(projected.y) || !isFinite(projected.z)
                            || projected.z < -1.0D || projected.z > 1.0D) {
                        continue;
                    }

                    double screenX = (projected.x + 1.0D) * 0.5D * screenWidth;
                    double screenY = (1.0D - projected.y) * 0.5D * screenHeight;
                    minX = Math.min(minX, screenX);
                    minY = Math.min(minY, screenY);
                    maxX = Math.max(maxX, screenX);
                    maxY = Math.max(maxY, screenY);
                    visibleCorners++;
                }
            }
        }

        if (visibleCorners == 0 || maxX < 0.0D || maxY < 0.0D || minX > screenWidth || minY > screenHeight) {
            return null;
        }

        float x = (float) MathHelper.clamp(minX, -screenWidth, screenWidth * 2.0D);
        float y = (float) MathHelper.clamp(minY, -screenHeight, screenHeight * 2.0D);
        float width = (float) MathHelper.clamp(maxX - minX, 1.0D, screenWidth * 2.0D);
        float height = (float) MathHelper.clamp(maxY - minY, 1.0D, screenHeight * 2.0D);
        if (width < 2.0F || height < 2.0F) {
            return null;
        }
        return new ScreenBox(x, y, width, height);
    }

    private void draw2DBox(ScreenBox box, Color color) {
        this.draw2DOutline(box, OUTLINE_BLACK, 3.0F);
        this.draw2DOutline(box, color, 1.4F);
    }

    private void draw2DOutline(ScreenBox box, Color color, float width) {
        float x = box.x();
        float y = box.y();
        float endX = x + box.width();
        float endY = y + box.height();
        Skia.drawLine(x, y, endX, y, width, color);
        Skia.drawLine(endX, y, endX, endY, width, color);
        Skia.drawLine(endX, endY, x, endY, width, color);
        Skia.drawLine(x, endY, x, y, width, color);
    }

    private void draw2DCorners(ScreenBox box, Color color) {
        this.draw2DCorners(box, OUTLINE_BLACK, 3.0F);
        this.draw2DCorners(box, color, 1.4F);
    }

    private void draw2DCorners(ScreenBox box, Color color, float width) {
        float x = box.x();
        float y = box.y();
        float endX = x + box.width();
        float endY = y + box.height();
        float length = Math.max(5.0F, Math.min(Math.min(box.width(), box.height()) * 0.28F, 18.0F));

        Skia.drawLine(x, y, x + length, y, width, color);
        Skia.drawLine(x, y, x, y + length, width, color);
        Skia.drawLine(endX, y, endX - length, y, width, color);
        Skia.drawLine(endX, y, endX, y + length, width, color);
        Skia.drawLine(endX, endY, endX - length, endY, width, color);
        Skia.drawLine(endX, endY, endX, endY - length, width, color);
        Skia.drawLine(x, endY, x + length, endY, width, color);
        Skia.drawLine(x, endY, x, endY - length, width, color);
    }

    private void draw2DHealthBar(ScreenBox box, float ratio) {
        float x = box.x() - 5.0F;
        float y = box.y();
        float endY = y + box.height();
        float healthY = endY - box.height() * ratio;
        Color health = this.healthColor(ratio);
        Skia.drawLine(x, y, x, endY, 3.2F, OUTLINE_BLACK);
        Skia.drawLine(x, endY, x, healthY, 1.7F, health);
    }

    private void draw3DHealthBar(net.minecraft.client.util.math.MatrixStack matrices, Render3DEvent event,
                                 Box box, float ratio) {
        double x = box.maxX + 0.08D;
        double width = 0.055D;
        double z = box.minZ;
        double height = box.getLengthY();
        Box background = new Box(x, box.minY, z, x + width, box.maxY, z + width);
        Box filled = new Box(x, box.minY, z, x + width, box.minY + height * ratio, z + width);
        Color health = this.healthColor(ratio);

        Render3DUtil.drawBox(matrices, event.getConsumers(), background,
                0.0F, 0.0F, 0.0F, 0.45F, true, false);
        Render3DUtil.drawBox(matrices, event.getConsumers(), filled,
                health.getRed() / 255.0F, health.getGreen() / 255.0F, health.getBlue() / 255.0F,
                0.85F, true, false);
    }

    private float healthRatio(PlayerEntity player) {
        return PlayerUtil.getHealthRatio(player);
    }

    private Color healthColor(float ratio) {
        ratio = MathHelper.clamp(ratio, 0.0F, 1.0F);
        if (ratio > 0.5F) {
            return gradient(new Color(255, 205, 65), new Color(70, 225, 90), (ratio - 0.5F) / 0.5F);
        }
        return gradient(new Color(255, 70, 70), new Color(255, 205, 65), ratio / 0.5F);
    }

    private static Color gradient(Color start, Color end, float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return new Color(
                Math.round(start.getRed() + (end.getRed() - start.getRed()) * t),
                Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * t),
                Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * t)
        );
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), MathHelper.clamp(alpha, 0, 255));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private boolean isBot(PlayerEntity player) {
        AntiBot antiBot = ModuleManager.getModule(AntiBot.class);
        return antiBot != null && antiBot.isEnabled() && antiBot.isBot(player);
    }

    private record ScreenBox(float x, float y, float width, float height) {
    }
}
