package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

/**
 * Rounded panel background blur rendered outside Skia.
 *
 * <p>The world behind the panel is captured straight from Minecraft's main
 * framebuffer with {@code glBlitFramebuffer}, then blurred with several Kawase
 * passes on raw GL FBOs, and finally drawn back through an AtomChat-owned
 * rounded-rect core shader so only the phone panel's rounded area is replaced
 * by the blurred image.</p>
 *
 * <p>Everything here is raw GL + Minecraft shaders: no Skia GPU resource is
 * kept across frames, so the per-frame {@code DirectContext.resetAll()} cannot
 * destroy it.</p>
 */
public final class PanelBlurRenderer {
    private static final String ROUNDED_SHADER_NAME = "atomchat_panel_blur";
    private static final String KAWASE_SHADER_NAME = "atomchat_kawase_blur";
    private static final int KAWASE_PASSES = 5;

    private static ShaderInstance roundedShader;
    private static ShaderInstance kawaseShader;

    // Panel-sized GPU ping-pong buffers.
    private static int inputFbo = -1, inputTex = -1;   // 1:1 capture from main framebuffer
    private static int tempFbo = -1, tempTex = -1;     // Kawase ping-pong buffer A
    private static int resultFbo = -1, resultTex = -1; // Kawase ping-pong buffer B
    private static int texW;
    private static int texH;
    private static int lastBlurTex = -1;
    private static boolean nextFullRefresh = true;
    private static boolean recreated = true;

    private PanelBlurRenderer() {
    }

    /** NeoForge shader registration; fired from the mod event bus. */
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, ROUNDED_SHADER_NAME),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> roundedShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, KAWASE_SHADER_NAME),
                            DefaultVertexFormat.POSITION),
                    shader -> kawaseShader = shader);
        } catch (Exception e) {
            AtomChat.LOGGER.warn("AtomChat panel blur shaders failed to load; blur disabled", e);
            roundedShader = null;
            kawaseShader = null;
        }
    }

    /**
     * Blurs the panel region and draws it back with a rounded mask.
     *
     * @param pose   current GUI pose (identity for AtomChat's own UI)
     * @param x      panel left in GUI-scaled coordinates
     * @param y      panel top in GUI-scaled coordinates
     * @param width  panel width in GUI-scaled coordinates
     * @param height panel height in GUI-scaled coordinates
     * @param radius rounded-corner radius in GUI-scaled coordinates
     * @param alpha  overall fade alpha (0..1, follows the panel open animation)
     * @return true only if the rounded blur quad was actually submitted
     */
    public static boolean render(Matrix4f pose, float x, float y, float width, float height, float radius, float alpha) {
        if (alpha <= 0.003F || width <= 0.0F || height <= 0.0F) {
            return false;
        }
        if (kawaseShader == null || roundedShader == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        var fb = client.getMainRenderTarget();
        double scale = client.getWindow().getGuiScale();

        int px = (int) Math.round(x * scale);
        int py = (int) Math.round(y * scale);
        int pw = (int) Math.round(width * scale);
        int ph = (int) Math.round(height * scale);
        if (pw <= 0 || ph <= 0) {
            return false;
        }

        ensureTextures(pw, ph);
        int blurTex = refreshBlur(fb.frameBufferId, fb.height, px, py, pw, ph);
        if (blurTex == -1) {
            return false;
        }

        return drawRoundedQuad(pose, x, y, width, height, radius, alpha, blurTex);
    }

    public static boolean isAvailable() {
        return roundedShader != null && kawaseShader != null;
    }

    public static void resetShader() {
        // NeoForge re-registers shaders through RegisterShadersEvent; this hook
        // exists for parity with the Fabric reload listener and is intentionally
        // a no-op here (a null shader would not be lazily re-created).
    }

    private static boolean drawRoundedQuad(Matrix4f pose, float x, float y, float w, float h, float radius, float alpha, int texture) {
        ShaderInstance sh = roundedShader;
        if (sh == null) {
            return false;
        }

        var uRect = sh.safeGetUniform("u_Rect");
        var uRadius = sh.safeGetUniform("u_Radius");
        var uFlipV = sh.safeGetUniform("u_FlipV");

        float poseScale = Math.abs(pose.m00());
        Vector4f center = pose.transform(new Vector4f(x + w / 2.0F, y + h / 2.0F, 0.0F, 1.0F));
        uRect.set(center.x(), center.y(), w / 2.0F * poseScale, h / 2.0F * poseScale);
        uRadius.set(Math.min(radius, Math.min(w, h) / 2.0F) * poseScale);
        // GL textures are bottom-up; the shader flips V so GUI top-left is texture top.
        uFlipV.set(1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(() -> sh);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(pose, x, y, 0).setUv(0.0F, 0.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
        bb.addVertex(pose, x, y + h, 0).setUv(0.0F, 1.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
        bb.addVertex(pose, x + w, y + h, 0).setUv(1.0F, 1.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
        bb.addVertex(pose, x + w, y, 0).setUv(1.0F, 0.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
        BufferUploader.drawWithShader(bb.buildOrThrow());

        RenderSystem.disableBlend();
        return true;
    }

    private static void ensureTextures(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (w == texW && h == texH) {
            return;
        }
        destroyTextures();
        recreated = true;
        texW = w;
        texH = h;

        int[] a = makeTexture(w, h);
        inputFbo = a[0];
        inputTex = a[1];
        int[] b = makeTexture(w, h);
        tempFbo = b[0];
        tempTex = b[1];
        int[] c = makeTexture(w, h);
        resultFbo = c[0];
        resultTex = c[1];
    }

    private static int[] makeTexture(int w, int h) {
        int fbo = GL30.glGenFramebuffers();
        int tex = GlStateManager._genTexture();
        GlStateManager._bindTexture(tex);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, w, h, 0,
                GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, tex, 0);
        return new int[]{fbo, tex};
    }

    private static void destroyTextures() {
        if (inputFbo != -1) {
            GL30.glDeleteFramebuffers(inputFbo);
            GlStateManager._deleteTexture(inputTex);
            GL30.glDeleteFramebuffers(tempFbo);
            GlStateManager._deleteTexture(tempTex);
            GL30.glDeleteFramebuffers(resultFbo);
            GlStateManager._deleteTexture(resultTex);
            inputFbo = tempFbo = resultFbo = -1;
            inputTex = tempTex = resultTex = -1;
            texW = texH = 0;
        }
    }

    private static void blit(int srcFbo, int sx0, int sy0, int sx1, int sy1,
                             int dstFbo, int dx0, int dy0, int dw, int dh) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx0 + dw, dy0 + dh,
                GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
    }

    private static int refreshBlur(int mainFbo, int fbHeight, int x, int y, int w, int h) {
        int y2 = Math.min(y + h, fbHeight);
        if (y2 <= y) {
            return lastBlurTex;
        }
        h = y2 - y;

        int oldFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);
        boolean scissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);
        GL30.glDisable(GL30.GL_SCISSOR_TEST);

        int glY0 = fbHeight - (y + h);
        int glY1 = fbHeight - y;

        boolean full = nextFullRefresh || recreated;
        nextFullRefresh = !full;
        if (full) {
            blit(mainFbo, x, glY0, x + w, glY1, inputFbo, 0, 0, w, h);

            int srcTex = inputTex;
            for (int i = 0; i < KAWASE_PASSES; i++) {
                int dstFbo = (i % 2 == 0) ? tempFbo : resultFbo;
                kawasePass(dstFbo, srcTex, w, h, 2.0F + i * 1.5F);
                srcTex = (i % 2 == 0) ? tempTex : resultTex;
            }
            lastBlurTex = (KAWASE_PASSES % 2 == 0) ? resultTex : tempTex;
            recreated = false;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GL30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (scissor) {
            GL30.glEnable(GL30.GL_SCISSOR_TEST);
        }
        return lastBlurTex;
    }

    private static void kawasePass(int dstFbo, int srcTex, int w, int h, float spacing) {
        ShaderInstance sh = kawaseShader;
        if (sh == null) {
            return;
        }

        var uSpacing = sh.safeGetUniform("u_Spacing");
        if (uSpacing == null) {
            return;
        }

        int oldFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dstFbo);
        GL30.glViewport(0, 0, w, h);

        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, srcTex);
        uSpacing.set(spacing);
        RenderSystem.setShader(() -> sh);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.addVertex(-1.0F, -1.0F, 0.0F);
        bb.addVertex(1.0F, -1.0F, 0.0F);
        bb.addVertex(1.0F, 1.0F, 0.0F);
        bb.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(bb.buildOrThrow());

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
        GL30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
