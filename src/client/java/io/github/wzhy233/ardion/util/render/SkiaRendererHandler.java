package io.github.wzhy233.ardion.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class SkiaRendererHandler extends AbstractSkiaRenderer {
    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private int boundFboId = -1;
    private int targetFboId = -1;
    private int width;
    private int height;

    void setTargetFramebuffer(int framebuffer) {
        targetFboId = framebuffer;
    }

    @Override
    public void init(int width, int height) {
        RenderSystem.assertOnRenderThread();
        if (!NativeSkiaLoader.ensureLoaded()) {
            throw new IllegalStateException(NativeSkiaLoader.failureReason());
        }
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        context = DirectContext.makeGL();
        rebuildRenderTarget();
    }

    private void rebuildRenderTarget() {
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        boundFboId = targetFboId >= 0 ? targetFboId : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        renderTarget = BackendRenderTarget.makeGL(
                width,
                height,
                0,
                8,
                boundFboId,
                FramebufferFormat.GR_GL_RGBA8
        );
    }

    @Override
    public void preRender(Surface surface) {
        if (surface == null || context == null) return;
        context.resetGLAll();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, boundFboId);
        GL11.glViewport(0, 0, width, height);
    }

    @Override
    public void postRender(Surface surface) {
        if (surface == null || context == null) return;
        context.flushAndSubmit(surface);
        context.resetGLAll();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, boundFboId);
    }

    public boolean render(DrawFrame frame) {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        int currentFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight());

        if (context == null) {
            targetFboId = currentFbo;
            SkiaRenderManager.get().init(framebufferWidth, framebufferHeight);
        } else if (framebufferWidth != width || framebufferHeight != height || currentFbo != boundFboId) {
            width = framebufferWidth;
            height = framebufferHeight;
            targetFboId = currentFbo;
            SkiaRenderManager.get().resize(width, height);
        }

        return SkiaRenderManager.get().render(frame);
    }

    @Override
    public Surface create(int width, int height) {
        if (renderTarget == null) rebuildRenderTarget();
        return Surface.wrapBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorSpace.getSRGB()
        );
    }

    @Override
    public void destroy() {
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (context != null) {
            context.flushAndSubmit(true);
            context.close();
            context = null;
        }
        boundFboId = -1;
    }
}
