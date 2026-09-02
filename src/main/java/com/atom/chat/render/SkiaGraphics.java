package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceColorFormat;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33C;

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
     * Reserved for the panel background blur. Currently always null — see
     * snapshotWorld() for why the blur is disabled.
     */

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
     * Returns the world image the panel can blur, or null when unavailable.
     *
     * <p><b>Currently always null: the blur is disabled.</b> Three Skia-side
     * approaches were tried and all failed. Do not re-attempt them:</p>
     * <ol>
     *   <li>{@code surface.makeImageSnapshot()} — on a wrapBackendRenderTarget
     *       surface Skia has not drawn on yet this frame it reports the backing
     *       FBO as empty and returns a fully transparent image. The blur drew
     *       nothing and only the tint showed (read as an oil film).</li>
     *   <li>{@code glReadPixels} into a Skia bitmap — correct pixels, but needs
     *       glFinish plus an 8&nbsp;MB readback, row flip and per-frame upload:
     *       tens of ms per frame.</li>
     *   <li>{@code glCopyTexSubImage2D} + {@code Image.adoptGLTextureFrom} —
     *       fatally broken. {@code context.resetAll()} runs every frame in this
     *       pipeline (it is required: MC mutates GL state between draws) and it
     *       abandons <i>all</i> Skia GPU resources. An adopted texture image is
     *       such a resource, so frame 2 draws a resource frame 1's resetAll
     *       already destroyed → GPU hang, no Java stack trace, window killed.
     *       Any persistent Skia GPU resource is incompatible with resetAll().</li>
     * </ol>
     *
     * <p>The working approach is Tuui's {@code BlurProgram}: blit the main
     * framebuffer to an offscreen target with glBlitFramebuffer and blur it
     * with an MC post-chain shader ({@code assets/tuui/shaders/core/blur.fsh}),
     * i.e. keep the blur outside Skia entirely. See the Tuui decompilation
     * notes before attempting this again.</p>
     */
    private Image snapshotWorld() {
        return null;
    }

    /** No-op while the blur is disabled; kept so callers need no change. */
    public void releaseWorldSnapshot() {
    }

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
