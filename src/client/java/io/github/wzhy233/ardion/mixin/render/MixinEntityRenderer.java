package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.hypixel.HypixelManager;
import io.github.wzhy233.ardion.module.impl.misc.HypixelUtils;
import io.github.wzhy233.ardion.util.server.Server;
import io.github.wzhy233.ardion.util.server.ServerUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void onRenderLevelHead(S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState) || !HypixelUtils.isLevelHeadEnabled() || !ServerUtil.isJoined(Server.HYPIXEL)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || state.displayName == null || state.nameLabelPos == null) {
            return;
        }

        Entity entity = client.world.getEntityById(playerState.id);
        if (entity == null || !state.displayName.getString().contains(entity.getName().getString())) {
            return;
        }

        String level = HypixelManager.getByUuid(entity.getUuid().toString()).networkLevel();
        if ("-1".equals(level)) {
            return;
        }

        Text levelText = Text.empty()
                .append(Text.literal("Level: ").formatted(Formatting.AQUA))
                .append(Text.literal(level).formatted(Formatting.YELLOW));
        queue.submitLabel(matrices, state.nameLabelPos, -10, levelText, !state.sneaking, state.light, state.squaredDistanceToCamera, cameraState);
    }
}
