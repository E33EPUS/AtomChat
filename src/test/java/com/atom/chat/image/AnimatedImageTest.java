package com.atom.chat.image;

import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnimatedImageTest {

    private static Image frame(int width) {
        try (Surface surface = Surface.makeRasterN32Premul(width, 1)) {
            return surface.makeImageSnapshot();
        }
    }

    private static AnimatedImage of(int repeat, Image... frames) {
        int[] durations = new int[frames.length];
        Arrays.fill(durations, 100);
        return new AnimatedImage(List.of(frames), durations, repeat, 0L);
    }

    @Test
    void loopingAnimationWraps() {
        AnimatedImage a = of(-1, frame(1), frame(2), frame(3));
        assertEquals(3, a.frameCount());
        assertEquals(300, a.totalMs());
        assertEquals(1, a.frameAt(0).getWidth());
        assertEquals(1, a.frameAt(99).getWidth());
        assertEquals(2, a.frameAt(100).getWidth());
        assertEquals(3, a.frameAt(250).getWidth());
        assertEquals(1, a.frameAt(300).getWidth());
    }

    @Test
    void finiteAnimationHoldsLastFrame() {
        AnimatedImage a = of(1, frame(1), frame(2), frame(3));
        assertEquals(3, a.frameAt(250).getWidth());
        assertEquals(3, a.frameAt(299).getWidth());
        assertEquals(3, a.frameAt(1000).getWidth());
    }

    @Test
    void singleFrameIgnoresClock() {
        Image only = frame(7);
        AnimatedImage a = of(-1, only);
        assertSame(only, a.frameAt(0));
        assertSame(only, a.frameAt(99999));
    }

    @Test
    void negativeClockClampsToFirstFrame() {
        AnimatedImage a = of(-1, frame(1), frame(2));
        assertEquals(1, a.frameAt(-500).getWidth());
    }

    @Test
    void pixelCountSumsEveryFrame() {
        AnimatedImage a = of(-1, frame(2), frame(3));
        assertEquals(5, a.pixelCount());
    }
}
