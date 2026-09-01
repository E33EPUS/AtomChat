package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
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
import java.nio.ByteOrder;

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
     * Last framebuffer image captured at the start of a draw pass. Released the
     * next time we capture (one frame of staleness is fine, GC plus native
     * cleanup will happen shortly after). The Screen reads it to draw a blurred
     * panel background.
     */
    private Image lastWorldSnapshot;
    /** Reused CPU-side buffers for the framebuffer readback. */
    private ByteBuffer snapshotBuffer;
    private byte[] snapshotPixels;
    private byte[] snapshotRowScratch;
    private int snapshotW = -1, snapshotH = -1;

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
        context.resetAll();
        RenderSystem.enableBlend();

        canvas.save();
        // Decouple from vanilla GUI scale: design density anchored at 1080p.
        float density = Math.max(1.0F, MinecraftClient.getInstance().getFramebuffer().textureHeight / 1080.0F);
        canvas.scale(density, density);
        // Capture the world before we draw anything on top. makeImageSnapshot
        // reads the FBO that the surface wraps — at this point it still holds
        // what vanilla GL drew this frame.
        Image snapshot = snapshotWorld();
        renderer.accept(canvas, snapshot);
        canvas.restore();

        surface.flush();
        GlStateUtil.restore();
        RenderSystem.disableBlend();
    }

    private Image snapshotWorld() {
        var fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth;
        int height = fb.textureHeight;
        if (width <= 0 || height <= 0) {
            return null;
        }

        // Release the previous frame's image before we touch the shared pixel
        // buffer: Skia images reference (not copy) their installPixels source,
        // so overwriting the buffer while a previous image is still alive
        // would corrupt the panel's blur the next frame.
        if (lastWorldSnapshot != null) {
            lastWorldSnapshot.close();
            lastWorldSnapshot = null;
        }

        if (snapshotW != width || snapshotH != height) {
            snapshotBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            snapshotPixels = new byte[width * height * 4];
            snapshotRowScratch = new byte[width * 4];
            snapshotW = width;
            snapshotH = height;
        }

        // Vanilla GL may still have pending world-draw commands in flight when
        // we reach this point; force them to complete before reading the FBO.
        GL11C.glFinish();

        // Bypass Skia's surface snapshot — makeImageSnapshot on a fresh
        // wrapBackendRenderTarget surface returns a transparent image because
        // Skia thinks the surface is empty (we haven't drawn on it this
        // frame). Read the FBO ourselves with glReadPixels so the panel blur
        // actually blurs the world behind it.
        int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, fb.fbo);
        snapshotBuffer.position(0);
        try {
            // BGRA matches Skia N32 byte order on little-endian, so no per-pixel
            // R/B swap is needed. We only have to flip rows because GL's framebuffer
            // origin is bottom-left while Skia bitmaps are top-down.
            GL11C.glReadPixels(0, 0, width, height, GL_BGRA, GL11C.GL_UNSIGNED_BYTE, snapshotBuffer);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
        }

        int stride = width * 4;
        snapshotBuffer.position(0);
        snapshotBuffer.get(snapshotPixels);
        for (int y = 0; y < height / 2; y++) {
            int top = y * stride;
            int bot = (height - 1 - y) * stride;
            System.arraycopy(snapshotPixels, top, snapshotRowScratch, 0, stride);
            System.arraycopy(snapshotPixels, bot, snapshotPixels, top, stride);
            System.arraycopy(snapshotRowScratch, 0, snapshotPixels, bot, stride);
        }

        Bitmap bitmap = new Bitmap();
        bitmap.allocN32Pixels(width, height, false);
        bitmap.installPixels(ImageInfo.makeN32Premul(width, height), snapshotPixels, stride);
        Image image = Image.makeFromBitmap(bitmap);
        lastWorldSnapshot = image;
        return image;
    }

    /** GL_BGRA = 0x80E1 (added in GL 1.2, available in MC's core 3.2 profile). */
    private static final int GL_BGRA = 0x80E1;

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
