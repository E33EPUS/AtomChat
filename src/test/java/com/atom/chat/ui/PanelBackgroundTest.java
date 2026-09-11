package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelBackgroundTest {
    /** The shipped default: near-black blue-grey, translucent panel. */
    private static final int BASE = 0xEE16191F;
    private static final float OPACITY = 0.93F;

    @Test
    void opaqueDropsTheConfiguredAlpha() {
        assertEquals(0xFF16191F, PanelBackground.opaque(BASE));
        assertEquals(0xFF16191F, PanelBackground.opaque(0x0016191F));
        assertEquals(0xFF16191F, PanelBackground.opaque(0xFF16191F));
    }

    @Test
    void withOpacityAppliesAndClampsTheOpacity() {
        assertEquals(0xED16191F, PanelBackground.withOpacity(BASE, OPACITY));
        assertEquals(0x8016191F, PanelBackground.withOpacity(BASE, 0.5F));
        assertEquals(0x0016191F, PanelBackground.withOpacity(BASE, -1.0F));
        assertEquals(0xFF16191F, PanelBackground.withOpacity(BASE, 2.0F));
    }

    /** Blur landed: translucent tint, blur shows through. */
    @Test
    void landedBlurKeepsTheTranslucentTint() {
        int tint = PanelBackground.tintFor(true, true, BASE, OPACITY);
        assertEquals(PanelBackground.withOpacity(BASE, OPACITY), tint);
        assertTrue((tint >>> 24) < 0xFF, "a landed blur must not be forced opaque");
        assertEquals(0x16191F, tint & 0x00FFFFFF);
    }

    /**
     * The 2026-09-11 regression: blur requested but not delivered. Showing the
     * translucent tint over a broken/black world is what reads as a full-screen
     * black panel, so this branch must go opaque.
     */
    @Test
    void missingBlurFallsBackToAnOpaquePanel() {
        int tint = PanelBackground.tintFor(true, false, BASE, OPACITY);
        assertEquals(0xFF16191F, tint);
        assertEquals(0xFF, tint >>> 24, "a missing blur must never leave the panel see-through");
    }

    /** Blur deliberately disabled: the panel is meant to sit over the live world. */
    @Test
    void disabledBlurStaysTranslucent() {
        assertEquals(PanelBackground.withOpacity(BASE, OPACITY), PanelBackground.tintFor(false, false, BASE, OPACITY));
        assertEquals(PanelBackground.withOpacity(BASE, OPACITY), PanelBackground.tintFor(false, true, BASE, OPACITY));
    }

    /** Every branch keeps the configured RGB. */
    @Test
    void everyBranchKeepsTheConfiguredRgb() {
        int[] tints = {
                PanelBackground.tintFor(true, true, 0x12345678, 0.5F),
                PanelBackground.tintFor(true, false, 0x12345678, 0.5F),
                PanelBackground.tintFor(false, false, 0x12345678, 0.5F),
        };
        for (int tint : tints) {
            assertEquals(0x345678, tint & 0x00FFFFFF);
        }
    }
}
