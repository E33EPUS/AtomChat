package com.atom.chat.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropMathTest {
    private static final float EPS = 1e-4F;

    @Test
    void baseScaleCoversFrameWithShortSide() {
        // 800x400 image, 200x200 frame: short side 400 must span the frame.
        assertEquals(0.5F, CropMath.baseScale(800, 400, 200, 200), EPS);
        assertEquals(1.0F, CropMath.baseScale(200, 400, 200, 200), EPS);
        // 400x400 image over a 400x200 frame: scale 1 already covers both axes.
        assertEquals(1.0F, CropMath.baseScale(400, 400, 400, 200), EPS);
    }

    @Test
    void clampScaleBoundsZoomRange() {
        float base = 1.0F;
        assertEquals(1.0F, CropMath.clampScale(0.1F, base), EPS);
        assertEquals(4.0F, CropMath.clampScale(99.0F, base), EPS);
        assertEquals(2.5F, CropMath.clampScale(2.5F, base), EPS);
    }

    @Test
    void clampCoverConfinesPan() {
        // 400x400 image at scale 1 (half=200), frame r=100 at (200,200):
        // the centre may slide within [c+r-half, c-r+half] = [100, 300].
        float[] p = CropMath.clampCover(400, 400, 1.0F, 200, 200, 100, 100, 999, -999);
        assertEquals(300.0F, p[0], EPS);
        assertEquals(100.0F, p[1], EPS);
    }

    @Test
    void clampCoverRectFrameConstrainsPerAxis() {
        // 800x400 image (hw=400/hh=200), frame halfW=200 / halfH=100 at
        // (200,200): X slides to 400, Y clamps to 300 (= c - ry + hh).
        float[] p = CropMath.clampCover(800, 400, 1.0F, 200, 200, 200, 100, 400, 999);
        assertEquals(400.0F, p[0], EPS);
        assertEquals(300.0F, p[1], EPS);
    }

    @Test
    void zoomAtCentreKeepsPointUnderCentrePinned() {
        float[] p = CropMath.zoomAtCentre(400, 400, 1.0F, 0, 0, 100, 100,
                100, 0, 2.0F, 0.5F);
        assertEquals(2.0F, p[0], EPS);
        // ux was -100 image px; new centre = 0 - (-100 * 2) = 200.
        assertEquals(200.0F, p[1], EPS);
        assertEquals(0.0F, p[2], EPS);
    }

    @Test
    void zoomClampsToRange() {
        float[] p = CropMath.zoomAtCentre(400, 400, 4.0F, 0, 0, 100, 100,
                0, 0, 2.0F, 1.0F);
        assertEquals(4.0F, p[0], EPS);
    }

    @Test
    void visibleRectMatchesGeometry() {
        // 400x400 image centred on the frame, scale 1, frame 200x200:
        // the frame sees image px (100..300) on both axes.
        float[] sq = CropMath.visibleRect(400, 400, 1.0F, 0, 0, 100, 100, 0, 0);
        assertEquals(100.0F, sq[0], EPS);
        assertEquals(100.0F, sq[1], EPS);
        assertEquals(200.0F, sq[2], EPS);
        assertEquals(200.0F, sq[3], EPS);
    }

    @Test
    void visibleRectClampsToImageBounds() {
        float[] sq = CropMath.visibleRect(400, 400, 4.0F, 0, 0, 100, 100, 700, 700);
        assertEquals(0.0F, sq[0], EPS);
        assertEquals(0.0F, sq[1], EPS);
        assertEquals(50.0F, sq[2], EPS);
        assertEquals(50.0F, sq[3], EPS);
        assertTrue(sq[0] + sq[2] <= 400 + EPS);
    }
}
