package com.atom.chat.render;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Captures and restores the OpenGL state around Skia rendering.
 * Ported from the Tuui mod's GlStateUtil (Kotlin -> Java).
 */
public final class GlStateUtil {
    private static State savedState;

    private GlStateUtil() {
    }

    public static void save() {
        savedState = State.capture();
    }

    public static void restore() {
        if (savedState == null) {
            throw new IllegalStateException("GlStateUtil.restore() called without save()");
        }
        savedState.restore();
    }

    private static final class State {
        private final boolean blendEnabled;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final boolean depthTestEnabled;
        private final boolean depthMask;
        private final int depthFunc;
        private final boolean cullEnabled;
        private final int cullFace;
        private final int activeTexture;
        private final int[] textureBindings2D;
        private final int[] samplerBindings;
        private final int program;
        private final int vaoBinding;
        private final boolean colorMaskR;
        private final boolean colorMaskG;
        private final boolean colorMaskB;
        private final boolean colorMaskA;
        private final int unpackAlignment;
        private final int pixelUnpackBufferBinding;
        private final boolean scissorTestEnabled;
        private final int[] scissorBox;

        private State(boolean blendEnabled, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
                      boolean depthTestEnabled, boolean depthMask, int depthFunc, boolean cullEnabled, int cullFace,
                      int activeTexture, int[] textureBindings2D, int[] samplerBindings, int program, int vaoBinding,
                      boolean colorMaskR, boolean colorMaskG, boolean colorMaskB, boolean colorMaskA,
                      int unpackAlignment, int pixelUnpackBufferBinding, boolean scissorTestEnabled, int[] scissorBox) {
            this.blendEnabled = blendEnabled;
            this.blendSrcRgb = blendSrcRgb;
            this.blendDstRgb = blendDstRgb;
            this.blendSrcAlpha = blendSrcAlpha;
            this.blendDstAlpha = blendDstAlpha;
            this.depthTestEnabled = depthTestEnabled;
            this.depthMask = depthMask;
            this.depthFunc = depthFunc;
            this.cullEnabled = cullEnabled;
            this.cullFace = cullFace;
            this.activeTexture = activeTexture;
            this.textureBindings2D = textureBindings2D;
            this.samplerBindings = samplerBindings;
            this.program = program;
            this.vaoBinding = vaoBinding;
            this.colorMaskR = colorMaskR;
            this.colorMaskG = colorMaskG;
            this.colorMaskB = colorMaskB;
            this.colorMaskA = colorMaskA;
            this.unpackAlignment = unpackAlignment;
            this.pixelUnpackBufferBinding = pixelUnpackBufferBinding;
            this.scissorTestEnabled = scissorTestEnabled;
            this.scissorBox = scissorBox;
        }

        void restore() {
            if (blendEnabled) {
                GL11C.glEnable(GL11C.GL_BLEND);
            } else {
                GL11C.glDisable(GL11C.GL_BLEND);
            }
            GL14C.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);

            if (depthTestEnabled) {
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            }
            GL11C.glDepthMask(depthMask);
            GL11C.glDepthFunc(depthFunc);

            if (cullEnabled) {
                GL11C.glEnable(GL11C.GL_CULL_FACE);
            } else {
                GL11C.glDisable(GL11C.GL_CULL_FACE);
            }
            GL11C.glCullFace(cullFace);

            for (int i = 0; i < textureBindings2D.length; i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBindings2D[i]);
                GL33C.glBindSampler(i, samplerBindings[i]);
            }
            GL13C.glActiveTexture(activeTexture);
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vaoBinding);
            GL11C.glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBufferBinding);

            if (scissorTestEnabled) {
                GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            }
            GL11C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        }

        static State capture() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int maxTextureUnits = GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
                int[] textureBindings = new int[maxTextureUnits];
                int[] samplerBindings = new int[maxTextureUnits];
                int activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);

                for (int i = 0; i < maxTextureUnits; i++) {
                    GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i);
                    textureBindings[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                    samplerBindings[i] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
                }
                GL13C.glActiveTexture(activeTexture);

                ByteBuffer colorMask = stack.malloc(4);
                GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);

                boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
                int blendSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
                int blendDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
                int blendSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
                int blendDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);

                boolean depthTestEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
                boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
                int depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
                boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
                int cullFace = GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE);

                int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
                int vaoBinding = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
                int unpackAlignment = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT);
                int pixelUnpackBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
                boolean scissorTestEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);

                IntBuffer scissorBox = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBox);

                return new State(
                        blendEnabled, blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha,
                        depthTestEnabled, depthMask, depthFunc, cullEnabled, cullFace,
                        activeTexture, textureBindings, samplerBindings, program, vaoBinding,
                        colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0,
                        unpackAlignment, pixelUnpackBufferBinding, scissorTestEnabled,
                        new int[]{scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3)}
                );
            }
        }
    }
}
