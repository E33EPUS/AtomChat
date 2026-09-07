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
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33C;

/**
 * Skia rendering bridge that draws directly onto Minecraft's main framebuffer.
 * Pattern ported from Tuui's Graphics class.
 */
public class SkiaGraphics {
    /**
     * Shared bridge. AtomChatScreen and the notification HUD overlay both draw
     * through the same Skia instance (never at the same time: screens render
     * only while open, the overlay only while no screen is open), so the two
     * never create competing GL contexts on Minecraft's main framebuffer.
     */
    public static final SkiaGraphics INSTANCE = new SkiaGraphics();

    private Canvas canvas;
    private DirectContext context;
    private Surface surface;
    private BackendRenderTarget renderTarget;
    private int lastFrameBufferId = -1;
    private int lastFramebufferWidth = -1;
    private int lastFramebufferHeight = -1;
    /**
     * Reserved for the panel background blur. Currently always null — see
     * snapshotWorld() for why the blur is disabled.
     */

    public void checkFrameBufferId() {
        var fb = Minecraft.getInstance().getMainRenderTarget();
        int current = fb.frameBufferId;
        int width = fb.width;
        int height = fb.height;
        // Minecraft resizes the main framebuffer in place when the window
        // changes (F11 fullscreen toggle, dragging a windowed border), keeping
        // the same FBO id. The Skia surface must be recreated on size changes
        // too, otherwise its coordinate system goes stale while the raw-GL blur
        // pre-pass keeps using the new size — that is the small-window
        // misalignment/blur-offset bug.
        if (lastFrameBufferId != -1
                && (lastFrameBufferId != current
                || lastFramebufferWidth != width
                || lastFramebufferHeight != height)) {
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

        var fb = Minecraft.getInstance().getMainRenderTarget();
        int width = fb.width;
        int height = fb.height;
        int fbo = fb.frameBufferId;
        lastFramebufferWidth = width;
        lastFramebufferHeight = height;

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
        draw(null, renderer);
    }

    public void draw(Runnable preUi, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, 0.0F, renderer);
    }

    /**
     * Draws the Skia UI after an optional raw-GL pre-pass, with an explicit
     * design density. AtomChat passes {@code baseDensity * uiScale} so its own
     * scale option scales the whole panel uniformly — UiTokens constants stay
     * untouched. The pre-pass runs after {@link GlStateUtil#save()} and before
     * Skia paints anything; {@link GlStateUtil#restore()} cleans up afterwards.
     *
     * @param density {@code <= 0} falls back to the 1080p-anchored default.
     */
    public void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        if (context == null || surface == null || canvas == null) {
            createSurface();
        }
        if (canvas == null) {
            AtomChat.LOGGER.warn("Skia canvas is null after createSurface, skipping frame");
            return;
        }
        if (density <= 0.0F) {
            density = Math.max(1.0F, Minecraft.getInstance().getMainRenderTarget().height / 1080.0F);
        }

        GlStateUtil.save();
        glStorePixel();
        if (preUi != null) {
            preUi.run();
        }
        // Capture the world before Skia resets its resource tracking: resetAll()
        // can drop the bookkeeping for our adopted texture-backed image.
        Image snapshot = snapshotWorld();
        context.resetAll();
        RenderSystem.enableBlend();

        canvas.save();
        // Decouple from vanilla GUI scale: design density anchored at 1080p.
        canvas.scale(density, density);
        renderer.accept(canvas, snapshot);
        canvas.restore();

        surface.flush();
        GlStateUtil.restore();
        RenderSystem.disableBlend();
    }

    /**
     * Legacy Skia-side snapshot path. Intentionally returns null: the panel
     * blur is now implemented by {@link PanelBlurRenderer} with raw GL and a
     * core shader outside Skia, so no Skia GPU resource crosses
     * {@code context.resetAll()}. Do not re-introduce Skia snapshots here.
     */
    private Image snapshotWorld() {
        return null;
    }

    /** No-op; kept so callers need no change. */
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
