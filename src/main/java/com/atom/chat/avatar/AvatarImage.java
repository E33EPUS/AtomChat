package com.atom.chat.avatar;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decoded custom-avatar cache. The avatar is always rendered as a circle, so
 * non-square photos are centre-cropped to a square (QQ behaviour, grilled
 * 2026-09-05) before being downscaled.
 *
 * <p>The same two rules as {@code WallpaperImage} apply: the decode never
 * runs on the render thread (background daemon + generation guard, latest
 * request wins), and the bitmap is kept small — avatars display well under
 * 100 px, so {@link #MAX_DIM} is far beyond anything the UI will ever draw.
 */
public final class AvatarImage {
    /** Longest allowed side of the cached bitmap, in pixels. */
    public static final int MAX_DIM = 256;

    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static volatile Image decoded;
    private static volatile Path decodedFrom;
    private static volatile boolean failed;

    private AvatarImage() {
    }

    /**
     * Returns the decoded avatar, kicking off a background decode the first
     * time a file appears (and again whenever the file changes). Returns null
     * while that is in flight or when the decode failed — callers keep drawing
     * the skin fallback.
     */
    public static Image current(Path path) {
        if (path == null) {
            release();
            return null;
        }
        if (path.equals(decodedFrom)) {
            return failed ? null : decoded;
        }
        startLoad(path);
        return null;
    }

    /** Drops the cached bitmap; the next frame re-decodes from disk. */
    public static void release() {
        decodedFrom = null;
        failed = false;
        if (decoded != null) {
            decoded.close();
            decoded = null;
        }
    }

    private static void startLoad(Path path) {
        if (path.equals(decodedFrom)) {
            return;
        }
        // Claimed synchronously so the next frame cannot re-trigger the load
        // while the decode is still in flight; the generation counter makes
        // the latest request win if the user swaps files mid-decode.
        decodedFrom = path;
        failed = false;
        int generation = GENERATION.incrementAndGet();
        Thread worker = new Thread(() -> {
            Image result = null;
            boolean ok = false;
            try {
                result = squareCrop(Image.makeFromEncoded(Files.readAllBytes(path)));
                ok = result != null;
            } catch (Throwable ignored) {
                ok = false;
            }
            if (generation == GENERATION.get()) {
                decoded = result;
                failed = !ok;
            } else if (result != null) {
                result.close();
            }
        }, "AtomChat-AvatarDecode");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Centre-crops to a square (cover, never stretch) and fits inside
     * {@link #MAX_DIM} without upscaling.
     */
    private static Image squareCrop(Image source) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        int side = Math.min(w, h);
        int sx = (w - side) / 2;
        int sy = (h - side) / 2;
        float scale = Math.min(1.0F, (float) MAX_DIM / side);
        int target = Math.max(1, Math.round(side * scale));
        try (Surface surface = Surface.makeRasterN32Premul(target, target)) {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(source,
                        Rect.makeXYWH(sx, sy, side, side),
                        Rect.makeXYWH(0, 0, target, target),
                        SamplingMode.LINEAR, paint, false);
            }
            source.close();
            return surface.makeImageSnapshot();
        }
    }
}
