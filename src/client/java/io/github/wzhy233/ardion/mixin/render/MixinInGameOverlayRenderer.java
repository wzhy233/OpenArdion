package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.module.impl.render.AntiBlind;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameOverlayRenderer.class)
public abstract class MixinInGameOverlayRenderer {
    @Redirect(method = "renderOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;getInWallBlockState(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/block/BlockState;"))
    private BlockState removeWallOverlay(PlayerEntity player) {
        AntiBlind antiBlind = AntiBlind.get();
        if (antiBlind != null && antiBlind.blocksWallOverlay()) {
            return null;
        }

        return InGameOverlayRendererInvoker.ardion$getInWallBlockState(player);
    }

    @Redirect(method = "renderOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderUnderwaterOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"))
    private void removeLiquidOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        AntiBlind antiBlind = AntiBlind.get();
        if (antiBlind == null || !antiBlind.blocksLiquidsFog()) {
            InGameOverlayRendererInvoker.ardion$renderUnderwaterOverlay(client, matrices, vertexConsumers);
        }
    }

    @Redirect(method = "renderOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V"))
    private void removeFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite) {
        AntiBlind antiBlind = AntiBlind.get();
        if (antiBlind == null || antiBlind.getFireOpacity() > 0.0F) {
            InGameOverlayRendererInvoker.ardion$renderFireOverlay(matrices, vertexConsumers, sprite);
        }
    }
}
