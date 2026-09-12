package com.atom.chat.image;

import io.github.humbleui.skija.Image;

import java.util.List;

/**
 * A decoded animated image (GIF) held as a pre-composited frame list plus
 * per-frame durations (0.2.7).
 *
 * <p>Skija already resolves each frame against its {@code requiredFrame} and
 * disposal state when reading by index — verified by the 0.2.7 offline spike
 * (a "full red frame + small green patch" GIF decodes frame 1 to
 * {@code p(0,0)=green / p(center)=red}) — so playback here is a pure
 * time to frame-index lookup, not a GIF compositor.
 *
 * <p>Frames are never eagerly closed, matching {@link ImageLoader}'s static
 * cache: the render thread may still hold the frame it was handed for the
 * current frame, and Skija finalises the native memory once unreferenced.
 */
public final class AnimatedImage {
    private final List<Image> frames;
    private final int[] durationsMs;
    private final int totalMs;
    /** Skia convention: negative = loop forever, otherwise the play count. */
    private final int repeatCount;
    private final long startMs;

    AnimatedImage(List<Image> frames, int[] durationsMs, int repeatCount, long startMs) {
        this.frames = List.copyOf(frames);
        this.durationsMs = durationsMs.clone();
        int total = 0;
        for (int d : this.durationsMs) {
            total += Math.max(1, d);
        }
        this.totalMs = total;
        this.repeatCount = repeatCount;
        this.startMs = startMs;
    }

    public int frameCount() {
        return frames.size();
    }

    public int totalMs() {
        return totalMs;
    }

    /** Decoded pixel count across every frame, for the cache budget. */
    public long pixelCount() {
        long pixels = 0L;
        for (Image frame : frames) {
            pixels += (long) frame.getWidth() * frame.getHeight();
        }
        return pixels;
    }

    /**
     * The frame to show at wall-clock {@code nowMs}. Finite animations hold the
     * last frame once their play count is exhausted; looping animations wrap.
     */
    public Image frameAt(long nowMs) {
        if (frames.isEmpty()) {
            return null;
        }
        if (frames.size() == 1 || totalMs <= 0) {
            return frames.get(0);
        }
        long elapsed = Math.max(0L, nowMs - startMs);
        if (repeatCount >= 0) {
            long span = (long) totalMs * Math.max(1, repeatCount);
            if (elapsed >= span) {
                return frames.get(frames.size() - 1);
            }
        }
        long t = elapsed % totalMs;
        int acc = 0;
        for (int i = 0; i < durationsMs.length; i++) {
            acc += Math.max(1, durationsMs[i]);
            if (t < acc) {
                return frames.get(i);
            }
        }
        return frames.get(frames.size() - 1);
    }
}
