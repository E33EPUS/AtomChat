package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceColorFormat;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;

import java.nio.ByteBuffer;

/**
 * Skia rendering bridge that draws directly onto Minecraft's main framebuffer.
 * Pattern ported from Tuui's Graphics class.
 */
public class SkiaGraphics {
    private Canvas canvas;
    private DirectContext context;
    private Surface surface;
    private BackendRenderTarget renderTarget;
    private int lastFrameBufferId = -1;
    /**
     * GPU-side copy of the world, used as the panel's blurred background.
     *
     * <p>The texture is adopted by Skia once and kept alive across frames; each
     * frame only refreshes its contents with glCopyTexSubImage2D. Everything
     * stays on the GPU — no readback, no pipeline stall, no per-frame upload.</p>
     */
    private Image worldImage;
    private int worldTexId = -1;
    private int worldTexW = -1, worldTexH = -1;

    public void checkFrameBufferId() {
        int current = MinecraftClient.getInstance().getFramebuffer().fbo;
        if (lastFrameBufferId != -1 && lastFrameBufferId != current) {
            createSurface();
        }
    }

    public void createSurface() {
        if (context == null) {
            context = DirectContext.makeGL();
        }
        if (surface != null) {
            surface.close();
        }
        if (renderTarget != null) {
            renderTarget.close();
        }

        var fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth;
        int height = fb.textureHeight;
        int fbo = fb.fbo;

        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbo, 32856);
        surface = Surface.wrapBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getDisplayP3(),
                new SurfaceProps(PixelGeometry.RGB_H)
        );
        lastFrameBufferId = fbo;
        canvas = surface != null ? surface.getCanvas() : null;
    }

    public void draw(java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        if (context == null || surface == null || canvas == null) {
            createSurface();
        }
        if (canvas == null) {
            AtomChat.LOGGER.warn("Skia canvas is null after createSurface, skipping frame");
            return;
        }

        GlStateUtil.save();
        glStorePixel();
        // Capture the world before Skia resets its resource tracking: resetAll()
        // can drop the bookkeeping for our adopted texture-backed image.
        Image snapshot = snapshotWorld();
        context.resetAll();
        RenderSystem.enableBlend();

        canvas.save();
        // Decouple from vanilla GUI scale: design density anchored at 1080p.
        float density = Math.max(1.0F, MinecraftClient.getInstance().getFramebuffer().textureHeight / 1080.0F);
        canvas.scale(density, density);
        renderer.accept(canvas, snapshot);
        canvas.restore();

        surface.flush();
        GlStateUtil.restore();
        RenderSystem.disableBlend();
    }

    /**
     * Captures the world as a GPU texture the panel can blur.
     *
     * <p>Two approaches were tried and rejected before this one:</p>
     * <ul>
     *   <li>{@code surface.makeImageSnapshot()} — on a fresh
     *       wrapBackendRenderTarget surface Skia has not drawn yet this frame,
     *       so it treats the backing FBO as empty and hands back a fully
     *       transparent image. The "blur" then draws nothing and only the tint
     *       shows, which reads as an oil film.</li>
     *   <li>{@code glReadPixels} into a Skia bitmap — correct pixels, but the
     *       required glFinish stalls the pipeline and the 8&nbsp;MB readback plus
     *       row flip costs tens of ms per frame.</li>
     * </ul>
     *
     * <p>This version keeps the copy entirely on the GPU (Tuui's BlurProgram
     * pattern): one texture, adopted by Skia once, refreshed each frame with
     * glCopyTexSubImage2D. glCopyTexSubImage2D is an ordered GL command, so it
     * reads the framebuffer as of its position in the stream — no glFinish.</p>
     */
    private Image snapshotWorld() {
        var fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth;
        int height = fb.textureHeight;
        if (width <= 0 || height <= 0 || context == null) {
            return null;
        }

        if (worldTexW != width || worldTexH != height || worldImage == null) {
            releaseWorldSnapshot();
            worldTexW = width;
            worldTexH = height;
            worldTexId = createTexture(width, height);
            // We must not render to the main FBO while sampling it, so the blur
            // samples this private copy instead of the live framebuffer.
            // Adopting transfers ownership: closing the image frees the texture.
            worldImage = Image.adoptGLTextureFrom(context, width, height, worldTexId,
                    GL11C.GL_TEXTURE_2D, GL_RGBA8,
                    SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888);
        }
        if (worldImage == null) {
            return null;
        }

        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int prevTex = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, fb.fbo);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, worldTexId);
            GL11C.glCopyTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTex);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
        }
        return worldImage;
    }

    private static int createTexture(int width, int height) {
        int tex = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, tex);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        return tex;
    }

    /** Frees the world-copy texture. Safe to call repeatedly. */
    public void releaseWorldSnapshot() {
        if (worldImage != null) {
            // Closing the adopted image deletes the GL texture behind it.
            worldImage.close();
            worldImage = null;
            worldTexId = -1;
        } else if (worldTexId >= 0) {
            GL11C.glDeleteTextures(worldTexId);
            worldTexId = -1;
        }
        worldTexW = -1;
        worldTexH = -1;
    }

    /** GL_RGBA8 = 0x8058 (same value createSurface passes to makeGL). */
    private static final int GL_RGBA8 = 32856;
    /** GL_CLAMP_TO_EDGE = 0x812F. */
    private static final int GL_CLAMP_TO_EDGE = 33071;

    private void glStorePixel() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
    }

    public DirectContext requireContext() {
        if (context == null) {
            throw new IllegalStateException("Skia context not initialized");
        }
        return context;
    }

    public BackendRenderTarget requireRenderTarget() {
        if (renderTarget == null) {
            throw new IllegalStateException("Render target not created");
        }
        return renderTarget;
    }

    public Surface requireSurface() {
        if (surface == null) {
            throw new IllegalStateException("Surface not created");
        }
        return surface;
    }
}
