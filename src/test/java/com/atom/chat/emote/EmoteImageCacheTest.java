package com.atom.chat.emote;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EmoteImageCacheTest {

    @TempDir
    Path tmp;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = EmoteImageCacheTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            return in.readAllBytes();
        }
    }

    private static byte[] png(int w, int h) {
        try (Surface surface = Surface.makeRasterN32Premul(w, h)) {
            return surface.makeImageSnapshot().encodeToData().getBytes();
        }
    }

    private File file(String name, byte[] bytes) throws IOException {
        Path path = tmp.resolve(name);
        Files.write(path, bytes);
        return path.toFile();
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
    void staticImageIsCachedAndClockIndependent() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("dot.png", png(4, 4));

        Image first = cache.image(file, 0L);
        assertNotNull(first);
        assertSame(first, cache.image(file, 999L), "a static emote must not depend on the clock");
    }

    @Test
    void animatedGifAdvancesWithTheClock() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("anim.gif", resource("/atomchat/animated_3frames.gif"));

        Image first = cache.image(file, 0L);
        assertNotNull(first);
        assertEquals(0xFFFF0000, argb(first, 0, 0));
        assertEquals(0xFF00FF00, argb(cache.image(file, 150L), 0, 0));
        assertEquals(0xFF0000FF, argb(cache.image(file, 250L), 0, 0));
    }

    @Test
    void failedDecodeIsRememberedAndInvalidateRetries() throws IOException {
        EmoteImageCache cache = new EmoteImageCache();
        File file = file("bad.png", new byte[]{1, 2, 3});

        assertNull(cache.image(file, 0L));
        assertNull(cache.image(file, 0L), "a failed file must not be re-decoded every frame");
        cache.invalidate(file);
        assertNull(cache.image(file, 0L));
    }
}
