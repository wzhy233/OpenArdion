package io.github.wzhy233.ardion.util.render;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import io.github.humbleui.skija.*;
import io.github.wzhy233.ardion.event.impl.render.EventSkiaRender;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.gui.shader.Shaders;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SkiaRenderManager {
    private static final SkiaRenderManager INSTANCE = new SkiaRenderManager();
    private static final Identifier FRAME_TEXTURE_ID = Identifier.of("ardion", "skia_frame");
    private static final long IDLE_RELEASE_MS = 5000L;

    private final List<Consumer<Canvas>> renderQuery = new ArrayList<>();
    private Surface surface;
    private Surface backdropSurface;
    private DirectContext directContext;
    private BackendRenderTarget renderTarget;
    private BackendRenderTarget backdropRenderTarget;
    private final boolean androidRuntime = AndroidGlBridge.isAndroidRuntime();
    private final AndroidSkiaRenderBackend androidBackend = androidRuntime ? new AndroidSkiaRenderBackend() : null;
    private SharedSkiaTexture texture;
    private SharedSkiaTexture backdropTexture;
    private long skiaWindow;
    private GLCapabilities skiaCapabilities;
    private int framebuffer;
    private int backdropFramebuffer;
    private SkiaGlFramebuffer frameAttachments;
    private SkiaGlFramebuffer backdropAttachments;
    private int copyFramebuffer;
    private int width;
    private int height;
    private float scaleFactor = 1.0F;
    private boolean rendering;
    private boolean usable = true;
    private boolean backdropCopied;
    private long lastRenderAt;
    private String failureReason = "Skija not initialized";

    private SkiaRenderManager() {
    }

    public static SkiaRenderManager get() {
        return INSTANCE;
    }

    public Canvas getCanvas() {
        if (androidRuntime) return androidBackend.getCanvas();
        return surface == null ? null : surface.getCanvas();
    }

    public Surface getSurface() {
        if (androidRuntime) return androidBackend.getSurface();
        return surface;
    }

    public Surface getBackdropSurface() {
        if (androidRuntime) return androidBackend.getSurface();
        return backdropCopied ? backdropSurface : null;
    }

    public void draw(Consumer<Canvas> drawingLogic) {
        if (drawingLogic != null) renderQuery.add(io.github.wzhy233.ardion.gui.ui.UiManager.compose(drawingLogic));
    }

    public boolean hasPendingDraws() {
        return !renderQuery.isEmpty();
    }

    public boolean drawFrame(DrawContext context, int scaledWidth, int scaledHeight, float tickDelta) {
        return renderFrame(context, scaledWidth, scaledHeight, tickDelta, true);
    }

    public boolean renderNow(DrawContext context, Consumer<Canvas> drawingLogic) {
        if (drawingLogic == null) return false;
        draw(drawingLogic);
        MinecraftClient client = MinecraftClient.getInstance();
        return renderFrame(context, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(),
                client.getRenderTickCounter().getTickProgress(false), false);
    }

    private boolean renderFrame(DrawContext context, int scaledWidth, int scaledHeight, float tickDelta, boolean postEvent) {
        if (rendering || !usable) {
            renderQuery.clear();
            return false;
        }
        try {
            if (!NativeSkiaLoader.ensureLoaded()) {
                usable = false;
                failureReason = NativeSkiaLoader.failureReason();
                renderQuery.clear();
                return false;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            int targetWidth = Math.max(1, client.getWindow().getFramebufferWidth());
            int targetHeight = Math.max(1, client.getWindow().getFramebufferHeight());
            float scaleX = targetWidth / (float) Math.max(1, scaledWidth);
            float scaleY = targetHeight / (float) Math.max(1, scaledHeight);
            scaleFactor = Math.max(scaleX, scaleY);
            if (androidRuntime) {
                DrawFrame frame = new DrawFrame(context, scaledWidth, scaledHeight, tickDelta, scaleX, scaleY, postEvent);
                boolean rendered = renderAndroid(context, frame);
                if (rendered) lastRenderAt = System.currentTimeMillis();
                return rendered;
            }
            ensureGpuTarget(targetWidth, targetHeight);
            DrawFrame frame = new DrawFrame(context, scaledWidth, scaledHeight, tickDelta, scaleX, scaleY, postEvent);
            if (!render(frame)) return false;
            drawMinecraftTexture(context, scaledWidth, scaledHeight);
            lastRenderAt = System.currentTimeMillis();
            return true;
        } catch (Throwable throwable) {
            org.slf4j.LoggerFactory.getLogger(SkiaRenderManager.class)
                    .error("Skia frame rendering failed", throwable);
            renderQuery.clear();
            closeGpuTarget();
            usable = false;
            failureReason = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            return false;
        }
    }

    boolean render(DrawFrame frame) {
        if (androidRuntime) {
            return renderAndroid(frame.drawContext(), frame);
        }
        if (surface == null) {
            renderQuery.clear();
            return false;
        }

        rendering = true;
        long previousContext = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCapabilities = GL.getCapabilities();
        makeSkiaContextCurrent(MinecraftClient.getInstance().getWindow().getHandle());
        try {
            directContext.resetGLAll();
            backdropCopied = copyMinecraftColorAttachment(width, height);
            directContext.resetGLAll();
            Canvas canvas = getCanvas();
            canvas.clear(0x00000000);
            int saveCount = canvas.save();
            try {
                canvas.scale(frame.scaleX(), frame.scaleY());
                if (frame.postEvent()) {
                    EventManager.post(new EventSkiaRender(canvas, frame.drawContext(), frame.scaledWidth(), frame.scaledHeight(), frame.tickDelta()));
                }
                for (Consumer<Canvas> query : renderQuery) {
                    int querySave = canvas.save();
                    try { query.accept(canvas); }
                    finally { canvas.restoreToCount(querySave); }
                }
            } finally {
                canvas.restoreToCount(saveCount);
            }
            directContext.flushAndSubmit(surface);
            directContext.resetGLAll();
            GL11.glFlush();
            return true;
        } finally {
            renderQuery.clear();
            restoreMinecraftContext(previousContext, previousCapabilities);
            rendering = false;
        }
    }

    private boolean copyMinecraftColorAttachment(int targetWidth, int targetHeight) {
        if (backdropTexture == null || backdropSurface == null || backdropFramebuffer == 0) return false;
        Framebuffer minecraftFramebuffer = MinecraftClient.getInstance().getFramebuffer();
        if (minecraftFramebuffer == null) return false;
        GpuTexture colorAttachment = minecraftFramebuffer.getColorAttachment();
        if (!(colorAttachment instanceof GlTexture sourceTexture) || sourceTexture.isClosed()) return false;

        int sourceId = sourceTexture.getGlId();
        int targetId = backdropTexture.getGlId();
        if (sourceId == 0 || targetId == 0 || sourceId == targetId) return false;

        if (copyFramebuffer == 0) {
            copyFramebuffer = GL30.glGenFramebuffers();
        }

        backdropSurface.notifyContentWillChange(ContentChangeMode.DISCARD);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copyFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceId, 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, backdropFramebuffer);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete Skia backdrop copy framebuffer");
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            blitBackdropFlipped(targetWidth, targetHeight);
            return true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copyFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, 0, 0);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    private void blitBackdropFlipped(int targetWidth, int targetHeight) {
        GL30.glBlitFramebuffer(
                0, targetHeight, targetWidth, 0,
                0, 0, targetWidth, targetHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
        );
        stampOpaqueAlpha();
    }

    private void stampOpaqueAlpha() {
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        float[] previousClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, backdropFramebuffer);
        GL11.glColorMask(false, false, false, true);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glClearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
        GL11.glColorMask(true, true, true, true);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
    }

    public void resize(int width, int height) {
        closeGpuTarget();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public void init(int width, int height) {
        resize(width, height);
    }

    public void destroy() {
        renderQuery.clear();
        FontHelper.clearCache();
        FilterCache.clear();
        Shaders.clearCache();
        Skia.getImageHelper().clear();
        closeGpuTarget();
        if (androidRuntime) {
            androidBackend.destroy();
        }
        closeSkiaContext();
        usable = true;
        lastRenderAt = 0L;
        failureReason = "Skija not initialized";
    }

    public void releaseIfIdle() {
        if (rendering || surface == null) return;
        long now = System.currentTimeMillis();
        if (lastRenderAt != 0L && now - lastRenderAt >= IDLE_RELEASE_MS) {
            if (androidRuntime) {
                androidBackend.releaseIfIdle();
            } else {
                closeGpuTarget();
            }
            lastRenderAt = 0L;
        }
    }

    public boolean isUsable() {
        return usable;
    }

    public String failureReason() {
        return failureReason;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }

    private void ensureGpuTarget(int targetWidth, int targetHeight) {
        if (surface != null && backdropSurface != null && texture != null && backdropTexture != null
                && width == targetWidth && height == targetHeight) {
            return;
        }
        closeGpuTarget();
        width = targetWidth;
        height = targetHeight;
        lastRenderAt = System.currentTimeMillis();

        MinecraftClient client = MinecraftClient.getInstance();
        texture = new SharedSkiaTexture(width, height);
        backdropTexture = new SharedSkiaTexture(width, height);
        MinecraftClient.getInstance().getTextureManager().registerTexture(FRAME_TEXTURE_ID, texture);

        long previousContext = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCapabilities = GL.getCapabilities();
        makeSkiaContextCurrent(client.getWindow().getHandle());
        try {
            if (directContext == null) {
                directContext = DirectContext.makeGL();
            }
            frameAttachments = new SkiaGlFramebuffer(texture.getGlId(), width, height);
            framebuffer = frameAttachments.id();
            backdropAttachments = new SkiaGlFramebuffer(backdropTexture.getGlId(), width, height);
            backdropFramebuffer = backdropAttachments.id();
            GL11.glViewport(0, 0, width, height);
            renderTarget = BackendRenderTarget.makeGL(
                    width,
                    height,
                    0,
                    SkiaGlFramebuffer.STENCIL_BITS,
                    framebuffer,
                    FramebufferFormat.GR_GL_RGBA8
            );
            backdropRenderTarget = BackendRenderTarget.makeGL(
                    width,
                    height,
                    0,
                    SkiaGlFramebuffer.STENCIL_BITS,
                    backdropFramebuffer,
                    FramebufferFormat.GR_GL_RGBA8
            );
            surface = Surface.wrapBackendRenderTarget(
                    directContext,
                    renderTarget,
                    SurfaceOrigin.TOP_LEFT,
                    ColorType.RGBA_8888,
                    ColorSpace.getSRGB()
            );
            backdropSurface = Surface.wrapBackendRenderTarget(
                    directContext,
                    backdropRenderTarget,
                    SurfaceOrigin.TOP_LEFT,
                    ColorType.RGBA_8888,
                    ColorSpace.getSRGB()
            );
        } finally {
            restoreMinecraftContext(previousContext, previousCapabilities);
        }
    }

    private boolean renderAndroid(DrawContext context, DrawFrame frame) {
        rendering = true;
        try {
            return androidBackend.render(context, frame.scaledWidth(), frame.scaledHeight(),
                    frame.scaleX(), frame.scaleY(), canvas -> {
                        if (frame.postEvent()) {
                            EventManager.post(new EventSkiaRender(canvas, frame.drawContext(), frame.scaledWidth(),
                                    frame.scaledHeight(), frame.tickDelta()));
                        }
                        for (Consumer<Canvas> query : renderQuery) {
                            query.accept(canvas);
                        }
                    });
        } finally {
            renderQuery.clear();
            rendering = false;
        }
    }

    private void makeSkiaContextCurrent(long minecraftWindow) {
        if (skiaWindow == 0L) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
            skiaWindow = GLFW.glfwCreateWindow(1, 1, "Ardion Skia", 0L, minecraftWindow);
            if (skiaWindow == 0L) {
                throw new IllegalStateException("Unable to create shared GLFW context for Skia");
            }
        }
        long producerFence = flushSharedContext();
        GLFW.glfwMakeContextCurrent(skiaWindow);
        if (skiaCapabilities == null) {
            skiaCapabilities = GL.createCapabilities();
        } else {
            GL.setCapabilities(skiaCapabilities);
        }
        waitForSharedContext(producerFence);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
    }

    private void restoreMinecraftContext(Long context, GLCapabilities capabilities) {
        long targetContext = context != null ? context : MinecraftClient.getInstance().getWindow().getHandle();
        long producerFence = GLFW.glfwGetCurrentContext() != targetContext ? flushSharedContext() : 0L;
        GLFW.glfwMakeContextCurrent(targetContext);
        if (capabilities != null) {
            GL.setCapabilities(capabilities);
        }
        waitForSharedContext(producerFence);
    }

    private static long flushSharedContext() {
        GLCapabilities capabilities = GL.getCapabilities();
        if (capabilities.OpenGL32 || capabilities.GL_ARB_sync) {
            long fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence != 0L) {
                GL11.glFlush();
                return fence;
            }
        }
        GL11.glFinish();
        return 0L;
    }

    private static void waitForSharedContext(long fence) {
        if (fence == 0L) return;
        GL32.glWaitSync(fence, 0, GL32.GL_TIMEOUT_IGNORED);
        GL32.glDeleteSync(fence);
    }

    private void drawMinecraftTexture(DrawContext context, int scaledWidth, int scaledHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, FRAME_TEXTURE_ID,
                0, 0, 0.0F, 0.0F, scaledWidth, scaledHeight, width, height, width, height);
    }

    private void closeGpuTarget() {
        backdropCopied = false;
        long previousContext = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCapabilities = GL.getCapabilities();
        boolean skiaCurrent = skiaWindow != 0L;
        if (skiaCurrent) {
            makeSkiaContextCurrent(MinecraftClient.getInstance().getWindow().getHandle());
        }
        if (surface != null) {
            if (directContext != null) {
                directContext.flushAndSubmit(true);
            }
            surface.close();
            surface = null;
        }
        if (backdropSurface != null) {
            if (directContext != null) {
                directContext.flushAndSubmit(true);
            }
            backdropSurface.close();
            backdropSurface = null;
        }
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (backdropRenderTarget != null) {
            backdropRenderTarget.close();
            backdropRenderTarget = null;
        }
        if (frameAttachments != null) { frameAttachments.close(); frameAttachments = null; }
        if (backdropAttachments != null) { backdropAttachments.close(); backdropAttachments = null; }
        framebuffer = backdropFramebuffer = 0;
        if (copyFramebuffer != 0) {
            GL30.glDeleteFramebuffers(copyFramebuffer);
            copyFramebuffer = 0;
        }
        if (directContext != null) {
            directContext.resetGLAll();
        }
        if (skiaCurrent) {
            restoreMinecraftContext(previousContext, previousCapabilities);
        }
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(FRAME_TEXTURE_ID);
            texture = null;
        }
        if (backdropTexture != null) {
            backdropTexture.close();
            backdropTexture = null;
        }
    }

    private void closeSkiaContext() {
        long previousContext = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCapabilities = GL.getCapabilities();
        if (skiaWindow != 0L) {
            makeSkiaContextCurrent(MinecraftClient.getInstance().getWindow().getHandle());
        }
        if (directContext != null) {
            directContext.flushAndSubmit(true);
            directContext.close();
            directContext = null;
        }
        if (skiaWindow != 0L) {
            restoreMinecraftContext(previousContext, previousCapabilities);
            GLFW.glfwDestroyWindow(skiaWindow);
            skiaWindow = 0L;
            skiaCapabilities = null;
        }
    }

    private static final class SharedSkiaTexture extends AbstractTexture {
        private SharedSkiaTexture(int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
            glTexture = device.createTexture("Ardion Skia GPU Frame", usage, TextureFormat.RGBA8, width, height, 1, 1);
            glTextureView = device.createTextureView(glTexture);
            sampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.NEAREST);
        }

        private int getGlId() {
            if (!(glTexture instanceof GlTexture gl)) {
                throw new IllegalStateException("Ardion Skia requires the OpenGL texture backend");
            }
            return gl.getGlId();
        }
    }
}
