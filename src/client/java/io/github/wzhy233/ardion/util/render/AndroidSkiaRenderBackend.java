package io.github.wzhy233.ardion.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.FramebufferFormat;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

final class AndroidSkiaRenderBackend {
    private DirectContext directContext;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private ByteBuffer pixelBuffer;
    private int uploadTexture;
    private int uploadFramebuffer;
    private int colorTexture;
    private int width;
    private int height;
    private boolean sharedCurrent;

    Canvas getCanvas() {
        return surface == null ? null : surface.getCanvas();
    }

    Surface getSurface() {
        return surface;
    }

    boolean render(DrawContext drawContext, int scaledWidth, int scaledHeight, float scaleX, float scaleY,
                   Consumer<Canvas> drawingLogic) {
        MinecraftClient client = MinecraftClient.getInstance();
        int targetWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int targetHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        ensureTarget(targetWidth, targetHeight);
        if (surface == null || directContext == null || colorTexture == 0 || pixelBuffer == null || uploadFramebuffer == 0) {
            return false;
        }

        int minecraftFramebuffer = currentFramebufferId();
        if (minecraftFramebuffer == 0) return false;

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, minecraftFramebuffer);
        ((Buffer) pixelBuffer).position(0);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);

        if (!AndroidGlBridge.androidGlMakeSharedCurrent()) return false;
        sharedCurrent = true;
        try {
            ((Buffer) pixelBuffer).position(0);
            if (!AndroidGlBridge.androidGlUploadColor(pixelBuffer, width, height)) return false;

            directContext.resetGLAll();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, AndroidGlBridge.androidGlGetOffscreenFbo());
            GL11.glViewport(0, 0, width, height);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            Canvas canvas = surface.getCanvas();
            canvas.save();
            canvas.scale(scaleX, scaleY);
            drawingLogic.accept(canvas);
            canvas.restore();
            directContext.flushAndSubmit(surface);
            directContext.resetGLAll();

            ((Buffer) pixelBuffer).position(0);
            if (!AndroidGlBridge.androidGlReadColor(pixelBuffer, width, height)) return false;
        } finally {
            AndroidGlBridge.androidGlRestoreCurrent();
            sharedCurrent = false;
        }

        blitBack(minecraftFramebuffer);
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer != null) framebuffer.blitToScreen();
        return true;
    }

    void releaseIfIdle() {
        closeTarget();
    }

    void destroy() {
        if (sharedCurrent) AndroidGlBridge.androidGlRestoreCurrent();
        closeTarget();
        if (AndroidGlBridge.isLoaded()) {
            AndroidGlBridge.androidGlDestroyShared();
        }
    }

    private void ensureTarget(int targetWidth, int targetHeight) {
        if (surface != null && width == targetWidth && height == targetHeight) return;
        closeTarget();
        AndroidGlBridge.ensureLoaded();
        if (!AndroidGlBridge.androidGlInitShared()) {
            throw new IllegalStateException("Android EGL shared context init failed");
        }
        width = targetWidth;
        height = targetHeight;
        colorTexture = AndroidGlBridge.androidGlCreateOffscreen(width, height);
        if (colorTexture == 0) {
            throw new IllegalStateException("Android Skia offscreen FBO creation failed");
        }

        if (!AndroidGlBridge.androidGlMakeSharedCurrent()) {
            throw new IllegalStateException("Android EGL shared context switch failed");
        }
        sharedCurrent = true;
        try {
            directContext = DirectContext.makeGL();
            renderTarget = BackendRenderTarget.makeGL(
                    width,
                    height,
                    0,
                    8,
                    AndroidGlBridge.androidGlGetOffscreenFbo(),
                    FramebufferFormat.GR_GL_RGBA8
            );
            surface = Surface.wrapBackendRenderTarget(
                    directContext,
                    renderTarget,
                    SurfaceOrigin.BOTTOM_LEFT,
                    ColorType.RGBA_8888,
                    ColorSpace.getSRGB()
            );
        } finally {
            AndroidGlBridge.androidGlRestoreCurrent();
            sharedCurrent = false;
        }

        pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
        createUploadTarget();
    }

    private void createUploadTarget() {
        deleteUploadTarget();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        uploadTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, uploadTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        uploadFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, uploadFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, uploadTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Android Skia upload FBO incomplete: 0x" + Integer.toHexString(status));
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
    }

    private void blitBack(int targetFramebuffer) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, uploadTexture);
        ((Buffer) pixelBuffer).position(0);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, uploadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
    }

    private int currentFramebufferId() {
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    private void closeTarget() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (directContext != null) {
            directContext.flushAndSubmit(true);
            directContext.close();
            directContext = null;
        }
        if (AndroidGlBridge.isLoaded()) {
            AndroidGlBridge.androidGlDestroyOffscreen();
        }
        colorTexture = 0;
        deleteUploadTarget();
        pixelBuffer = null;
        width = 0;
        height = 0;
    }

    private void deleteUploadTarget() {
        RenderSystem.assertOnRenderThread();
        if (uploadFramebuffer != 0) {
            GL30.glDeleteFramebuffers(uploadFramebuffer);
            uploadFramebuffer = 0;
        }
        if (uploadTexture != 0) {
            GL11.glDeleteTextures(uploadTexture);
            uploadTexture = 0;
        }
    }
}
