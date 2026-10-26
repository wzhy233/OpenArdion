package io.github.wzhy233.ardion.gui.hud.list;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.config.HudConfig;
import io.github.wzhy233.ardion.gui.shader.Effects;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.render.Skia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ModuleList {
    private final List<ModuleNode> nodes = new ArrayList<>();
    private int lastOrderHash;

    public void clear() {
        nodes.clear();
    }

    public HUD.Bounds render(int screenWidth) {
        if (nodes.isEmpty()) populate();
        sortIfNeeded();
        HudConfig.constrainArrayListToScreen(screenWidth);

        float scale = HudConfig.arrayListScale();
        float anchorX = HudConfig.arrayListX();
        float anchorY = HudConfig.arrayListY();

        float y = 0.0F;
        float nextVisibleY = 0.0F;
        List<ModuleNode> visible = new ArrayList<>(nodes.size());
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (ModuleNode node : nodes) {
            node.update(y, nextVisibleY);
            if (node.opacity() > 0) {
                visible.add(node);
                minX = Math.min(minX, node.getX());
                minY = Math.min(minY, node.getY());
                maxX = Math.max(maxX, node.getX() + node.getWidth());
                maxY = Math.max(maxY, node.getY() + node.getHeight());
                y += node.getHeight() + 2.0F;
                nextVisibleY = node.getY() + node.getHeight() + 2.0F;
            }
        }
        if (HudSetting.blur()) {
            applyMergedBackdropBlur(anchorX, anchorY, scale, visible.stream().filter(node -> node.opacity() == 1).toList());
            for (ModuleNode node : visible) {
                if (node.opacity() == 1) continue;
                try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(Skia.getCanvas(), null, node.opacity())) {
                    applyMergedBackdropBlur(anchorX, anchorY, scale, List.of(node));
                }
            }
        }

        Skia.save();
        Skia.translate(anchorX, anchorY);
        Skia.scale(scale);
        for (ModuleNode node : visible) {
            node.render();
        }
        Skia.restore();

        if (!Float.isFinite(minX)) return HUD.Bounds.empty(anchorX, anchorY);
        return new HUD.Bounds(anchorX + minX * scale, anchorY + minY * scale,
                (maxX - minX) * scale, (maxY - minY) * scale);
    }

    private void applyMergedBackdropBlur(float anchorX, float anchorY, float scale, List<ModuleNode> visible) {
        if (visible.isEmpty()) return;

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        try (PathBuilder builder = new PathBuilder()) {
            for (ModuleNode node : visible) {
                float x = anchorX + node.getX() * scale;
                float y = anchorY + node.getY() * scale;
                float w = node.getWidth() * scale;
                float h = node.getHeight() * scale;
                if (w <= 0.0F || h <= 0.0F || !Float.isFinite(x + y + w + h)) continue;
                builder.addRRect(RRect.makeXYWH(x, y, w, h, ModuleNode.ROW_RADIUS * scale));
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + w);
                maxY = Math.max(maxY, y + h);
            }
            if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return;
            try (Path unionPath = builder.build()) {
                Skia.save();
                Skia.getCanvas().clipPath(unionPath, ClipMode.INTERSECT, true);
                Rect bounds = Rect.makeLTRB(minX, minY, maxX, maxY);
                if (HudSetting.blurSksl()) {
                    Effects.blurBackdropSKSL(Skia.getCanvas(), Skia.getSurface(), bounds,
                            HudSetting.blurSigma(), HudSetting.skslShader());
                } else {
                    Effects.blurBackdrop(Skia.getCanvas(), bounds, HudSetting.blurSigma());
                }
                Skia.restore();
            }
        }
    }

    private void populate() {
        for (Module module : ModuleManager.getModules()) {
            if (module.getCategory() == ModuleCategory.RENDER) continue;
            nodes.add(new ModuleNode(module));
        }
        lastOrderHash = 0;
    }

    private void sortIfNeeded() {
        int hash = 1;
        for (ModuleNode node : nodes) {
            hash = 31 * hash + node.getModule().getName().hashCode();
            hash = 31 * hash + Float.floatToIntBits(node.measureWidth());
        }
        if (hash == lastOrderHash) return;
        nodes.sort(Comparator.comparingDouble(ModuleNode::measureWidth).reversed());
        lastOrderHash = hash;
    }
}
