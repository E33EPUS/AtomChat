package com.atom.chat.image;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageLoaderAnimatedTest {

    @TempDir
    Path tempDir;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = ImageLoaderAnimatedTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return in.readAllBytes();
        }
    }

    private static int argb(Image image, int x, int y) {
        Bitmap bitmap = new Bitmap();
        try {
            bitmap.allocN32Pixels(image.getWidth(), image.getHeight());
            image.readPixels(bitmap);
            return bitmap.getColor(x, y);
        } finally {
            bitmap.close();
        }
    }

    @Test
    void animatedGifAdvancesWithTheClock() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong(0L);
        // Direct executor: every load completes inside get(), like the other
        // ImageLoader tests, so frame assertions are deterministic.
        ImageLoader loader = ImageLoader.forTest(url -> gif, now::get, Runnable::run);
        loader.init(tempDir);

        Image first = loader.get("http://test/anim.gif", true);
        assertNotNull(first);
        assertEquals(0xFFFF0000, argb(first, 0, 0));

        now.set(150L);
        Image second = loader.get("http://test/anim.gif");
        assertNotNull(second);
        assertEquals(0xFF00FF00, argb(second, 0, 0));
    }

    @Test
    void animatedDiskCacheKeepsTheAnimation() throws IOException {
        byte[] gif = resource("/atomchat/animated_3frames.gif");
        AtomicLong now = new AtomicLong(0L);
        ImageLoader first = ImageLoader.forTest(url -> gif, now::get, Runnable::run);
        first.init(tempDir);
        assertNotNull(first.get("http://test/anim2.gif", true));
        try (var stream = Files.list(tempDir)) {
            assertTrue(stream.anyMatch(p -> p.getFileName().toString().endsWith(".bin")),
                    "disk cache should hold the original GIF bytes");
        }

        // A second loader with a fetcher that must never run proves the disk
        // copy (verbatim GIF, not a flattened WebP) can be replayed.
        ImageLoader second = ImageLoader.forTest(url -> {
            throw new AssertionError("must not fetch when the disk cache has the bytes");
        }, now::get, Runnable::run);
        second.init(tempDir);
        Image replayed = second.get("http://test/anim2.gif", true);
        assertNotNull(replayed);
        assertEquals(0xFFFF0000, argb(replayed, 0, 0));
    }
}
