package com.atom.chat.image;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GifDecoderTest {

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = GifDecoderTest.class.getResourceAsStream(name)) {
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

    private static byte[] png(int w, int h) {
        try (Surface surface = Surface.makeRasterN32Premul(w, h)) {
            return surface.makeImageSnapshot().encodeToData().getBytes();
        }
    }

    @Test
    void decodesEveryFrameWithDurations() throws IOException {
        AnimatedImage a = GifDecoder.decode(resource("/atomchat/animated_3frames.gif"), 0L);
        assertNotNull(a);
        assertEquals(3, a.frameCount());
        assertEquals(300, a.totalMs());
        assertEquals(0xFFFF0000, argb(a.frameAt(0), 0, 0));
        assertEquals(0xFF00FF00, argb(a.frameAt(150), 0, 0));
        assertEquals(0xFF0000FF, argb(a.frameAt(250), 0, 0));
    }

    @Test
    void compositesPartialFramesAgainstTheBase() throws IOException {
        AnimatedImage a = GifDecoder.decode(resource("/atomchat/animated_patch.gif"), 0L);
        assertNotNull(a);
        assertEquals(2, a.frameCount());
        assertEquals(0xFFFF0000, argb(a.frameAt(0), 0, 0));
        // Frame 1 only paints a green patch at (0,0); the red base must survive.
        assertEquals(0xFF00FF00, argb(a.frameAt(200), 0, 0));
        assertEquals(0xFFFF0000, argb(a.frameAt(200), 8, 8));
    }

    @Test
    void singleFrameGifIsNotAnimated() throws IOException {
        assertNull(GifDecoder.decode(resource("/atomchat/single_frame.gif"), 0L));
    }

    @Test
    void staticPngIsNotAnimated() {
        assertNull(GifDecoder.decode(png(4, 4), 0L));
    }

    @Test
    void frameCountIsCapped() throws IOException {
        AnimatedImage a = GifDecoder.decode(resource("/atomchat/animated_many.gif"), 0L);
        assertNotNull(a);
        assertEquals(ImageLoader.MAX_ANIM_FRAMES, a.frameCount());
    }

    @Test
    void rejectsNonGifBytes() {
        assertNull(GifDecoder.decode(new byte[]{1, 2, 3}, 0L));
        assertNull(GifDecoder.decode(null, 0L));
    }
}
