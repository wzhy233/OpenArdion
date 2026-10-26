package io.github.wzhy233.ardion.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.wzhy233.ardion.mixin.render.RenderLayerAccessor;
import io.github.wzhy233.ardion.mixin.render.RenderPipelinesAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public final class Render3DUtil {
    private static final Identifier OUTLINE_TEXTURE = Identifier.ofVanilla("textures/misc/forcefield.png");
    private static final RenderPipeline FILL_NO_DEPTH_PIPELINE = RenderPipelinesAccessor.invokeRegister(RenderPipeline.builder()
            .withLocation(Identifier.of("ardion", "pipeline/fill_no_depth"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .build());
    private static final RenderLayer FILL_NO_DEPTH_LAYER = RenderLayerAccessor.invokeOf("ardion_fill_no_depth", RenderSetup.builder(FILL_NO_DEPTH_PIPELINE)
            .translucent()
            .build());
    private static final RenderPipeline LINE_NO_DEPTH_PIPELINE = RenderPipelinesAccessor.invokeRegister(RenderPipeline.builder()
            .withLocation(Identifier.of("ardion", "pipeline/line_no_depth"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.LINES)
            .build());
    private static final RenderLayer LINE_NO_DEPTH_LAYER = RenderLayerAccessor.invokeOf("ardion_line_no_depth", RenderSetup.builder(LINE_NO_DEPTH_PIPELINE)
            .translucent()
            .build());

    private Render3DUtil() {
    }

    public static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers, Box box, float red, float green, float blue, float alpha, boolean filled, boolean outline) {
        if (filled) {
            VertexConsumer buffer = consumers.getBuffer(FILL_NO_DEPTH_LAYER);
            drawFilledBox(matrices, buffer, box, red, green, blue, alpha);
        }

        if (outline) {
            OutlineVertexConsumerProvider outlineConsumers = MinecraftClient.getInstance().getBufferBuilders().getOutlineVertexConsumers();
            outlineConsumers.setColor(packColor(red, green, blue, 1.0F));
            VertexConsumer buffer = outlineConsumers.getBuffer(RenderLayers.outlineNoCull(OUTLINE_TEXTURE));
            drawOutlinedEdgesTextured(matrices, buffer, box, red, green, blue, 1.0F);
        }
    }

    public static void flushOutline() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getBufferBuilders().getOutlineVertexConsumers().draw();
        client.worldRenderer.drawEntityOutlinesFramebuffer();
    }

    public static void drawLine(MatrixStack matrices, VertexConsumerProvider consumers,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float red, float green, float blue, float alpha) {
        VertexConsumer buffer = consumers.getBuffer(LINE_NO_DEPTH_LAYER);
        int color = packColor(red, green, blue, alpha);
        vertex(buffer, matrices, (float) x1, (float) y1, (float) z1, color);
        vertex(buffer, matrices, (float) x2, (float) y2, (float) z2, color);
    }

    private static void drawFilledBox(MatrixStack matrices, VertexConsumer buffer, Box box, float red, float green, float blue, float alpha) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        int color = packColor(red, green, blue, alpha);

        vertex(buffer, matrices, minX, minY, minZ, color);
        vertex(buffer, matrices, maxX, minY, minZ, color);
        vertex(buffer, matrices, maxX, minY, maxZ, color);
        vertex(buffer, matrices, minX, minY, maxZ, color);

        vertex(buffer, matrices, minX, maxY, minZ, color);
        vertex(buffer, matrices, minX, maxY, maxZ, color);
        vertex(buffer, matrices, maxX, maxY, maxZ, color);
        vertex(buffer, matrices, maxX, maxY, minZ, color);

        vertex(buffer, matrices, minX, minY, minZ, color);
        vertex(buffer, matrices, minX, maxY, minZ, color);
        vertex(buffer, matrices, maxX, maxY, minZ, color);
        vertex(buffer, matrices, maxX, minY, minZ, color);

        vertex(buffer, matrices, maxX, minY, minZ, color);
        vertex(buffer, matrices, maxX, maxY, minZ, color);
        vertex(buffer, matrices, maxX, maxY, maxZ, color);
        vertex(buffer, matrices, maxX, minY, maxZ, color);

        vertex(buffer, matrices, minX, minY, maxZ, color);
        vertex(buffer, matrices, maxX, minY, maxZ, color);
        vertex(buffer, matrices, maxX, maxY, maxZ, color);
        vertex(buffer, matrices, minX, maxY, maxZ, color);

        vertex(buffer, matrices, minX, minY, minZ, color);
        vertex(buffer, matrices, minX, minY, maxZ, color);
        vertex(buffer, matrices, minX, maxY, maxZ, color);
        vertex(buffer, matrices, minX, maxY, minZ, color);
    }

    private static void drawOutlinedEdgesTextured(MatrixStack matrices, VertexConsumer buffer, Box box, float red, float green, float blue, float alpha) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        float thickness = 0.02F;

        edgeBox(matrices, buffer, minX, minY, minZ, maxX, minY + thickness, minZ + thickness, red, green, blue, alpha);
        edgeBox(matrices, buffer, minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ, red, green, blue, alpha);
        edgeBox(matrices, buffer, minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness, red, green, blue, alpha);
        edgeBox(matrices, buffer, minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ, red, green, blue, alpha);

        edgeBox(matrices, buffer, minX, minY, minZ, minX + thickness, maxY, minZ + thickness, red, green, blue, alpha);
        edgeBox(matrices, buffer, maxX - thickness, minY, minZ, maxX, maxY, minZ + thickness, red, green, blue, alpha);
        edgeBox(matrices, buffer, minX, minY, maxZ - thickness, minX + thickness, maxY, maxZ, red, green, blue, alpha);
        edgeBox(matrices, buffer, maxX - thickness, minY, maxZ - thickness, maxX, maxY, maxZ, red, green, blue, alpha);

        edgeBox(matrices, buffer, minX, minY, minZ, minX + thickness, minY + thickness, maxZ, red, green, blue, alpha);
        edgeBox(matrices, buffer, maxX - thickness, minY, minZ, maxX, minY + thickness, maxZ, red, green, blue, alpha);
        edgeBox(matrices, buffer, minX, maxY - thickness, minZ, minX + thickness, maxY, maxZ, red, green, blue, alpha);
        edgeBox(matrices, buffer, maxX - thickness, maxY - thickness, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void edgeBox(MatrixStack matrices, VertexConsumer buffer,
                                float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ,
                                float red, float green, float blue, float alpha) {
        int color = packColor(red, green, blue, alpha);

        texturedQuad(buffer, matrices, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color);
        texturedQuad(buffer, matrices, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        texturedQuad(buffer, matrices, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
        texturedQuad(buffer, matrices, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);
        texturedQuad(buffer, matrices, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        texturedQuad(buffer, matrices, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, float x, float y, float z, int color) {
        buffer.vertex(matrices.peek(), x, y, z).color(color);
    }

    private static void texturedVertex(VertexConsumer buffer, MatrixStack matrices, float x, float y, float z, float u, float v, int color) {
        buffer.vertex(matrices.peek(), x, y, z).color(color).texture(u, v);
    }

    private static void texturedQuad(VertexConsumer buffer, MatrixStack matrices,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float x3, float y3, float z3,
                                     float x4, float y4, float z4,
                                     int color) {
        texturedVertex(buffer, matrices, x1, y1, z1, 0.0F, 0.0F, color);
        texturedVertex(buffer, matrices, x2, y2, z2, 1.0F, 0.0F, color);
        texturedVertex(buffer, matrices, x3, y3, z3, 1.0F, 1.0F, color);
        texturedVertex(buffer, matrices, x4, y4, z4, 0.0F, 1.0F, color);
    }

    public static int packColor(float red, float green, float blue, float alpha) {
        int a = MathHelper.clamp((int) (alpha * 255.0F), 0, 255);
        int r = MathHelper.clamp((int) (red * 255.0F), 0, 255);
        int g = MathHelper.clamp((int) (green * 255.0F), 0, 255);
        int b = MathHelper.clamp((int) (blue * 255.0F), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
