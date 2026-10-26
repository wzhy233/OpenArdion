package io.github.wzhy233.ardion.mixin.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InGameOverlayRenderer.class)
public interface InGameOverlayRendererInvoker {
    @Invoker("getInWallBlockState")
    static BlockState ardion$getInWallBlockState(PlayerEntity player) {
        throw new AssertionError();
    }

    @Invoker("renderUnderwaterOverlay")
    static void ardion$renderUnderwaterOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        throw new AssertionError();
    }

    @Invoker("renderFireOverlay")
    static void ardion$renderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite) {
        throw new AssertionError();
    }
}
