package io.github.wzhy233.ardion.util.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class SkiaGlFramebuffer implements AutoCloseable {
    public static final int STENCIL_BITS = 8;
    private int framebuffer;
    private int depthStencil;

    public SkiaGlFramebuffer(int texture, int width, int height) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        try {
            framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            depthStencil = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencil);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthStencil);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_STENCIL_SIZE) != STENCIL_BITS) {
                throw new IllegalStateException("Incomplete Skia color/stencil framebuffer: 0x" + Integer.toHexString(status));
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        } finally {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }

    public int id() { return framebuffer; }

    @Override public void close() {
        if (framebuffer != 0) { GL30.glDeleteFramebuffers(framebuffer); framebuffer = 0; }
        if (depthStencil != 0) { GL30.glDeleteRenderbuffers(depthStencil); depthStencil = 0; }
    }
}
