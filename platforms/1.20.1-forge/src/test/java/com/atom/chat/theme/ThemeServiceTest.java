package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeServiceTest {

    @Test
    void cornerFactorMapsTheThreeStyles() {
        assertEquals(1.0F, ThemeService.cornerFactor("large"), 1e-6F);
        assertEquals(0.5F, ThemeService.cornerFactor("medium"), 1e-6F);
        assertEquals(0.35F, ThemeService.cornerFactor("small"), 1e-6F);
    }

    @Test
    void cornerFactorFallsBackToLargeOnGarbage() {
        assertEquals(1.0F, ThemeService.cornerFactor("tiny"), 1e-6F);
        assertEquals(1.0F, ThemeService.cornerFactor(null), 1e-6F);
        assertEquals(1.0F, ThemeService.cornerFactor(""), 1e-6F);
    }

    @Test
    void modernPresetIsOpaqueFlat() {
        AtomChatConfig config = new AtomChatConfig();
        ThemeService.apply(config, ThemeService.MODERN);
        assertEquals(1.0F, config.panelOpacity, 1e-6F);
        assertFalse(config.blurEnabled);
        assertTrue(config.panelOutline);
        assertEquals(1.0F, config.cardTint, 1e-6F);
        assertEquals(ThemeService.MODERN, config.themeName);
    }

    @Test
    void presetsNeverTouchCornerStyle() {
        // Corner style has no settings UI; a preset silently moving it would be
        // invisible magic. It stays wherever the user (or the config file) put it.
        AtomChatConfig config = new AtomChatConfig();
        config.cornerStyle = "medium";
        ThemeService.apply(config, ThemeService.MODERN);
        assertEquals("medium", config.cornerStyle);
        ThemeService.apply(config, ThemeService.FROSTED);
        assertEquals("medium", config.cornerStyle);
    }

    @Test
    void frostedPresetRestoresTheShippedDefaults() {
        AtomChatConfig config = new AtomChatConfig();
        // Drift every preset-managed knob away from the defaults first.
        config.panelOpacity = 1.0F;
        config.blurEnabled = false;
        config.panelOutline = false;
        config.cornerStyle = "small";
        config.themeName = ThemeService.MODERN;
        ThemeService.apply(config, ThemeService.FROSTED);
        assertEquals(0.93F, config.panelOpacity, 1e-6F);
        assertTrue(config.blurEnabled);
        assertTrue(config.panelOutline);
        // 0.235 ≈ white @ alpha 60 — the shipped frosted look on the new
        // "0% means invisible" card-tint axis.
        assertEquals(0.235F, config.cardTint, 1e-6F);
        assertEquals(ThemeService.FROSTED, config.themeName);
    }

    @Test
    void presetsNeverTouchPersonalChoices() {
        AtomChatConfig config = new AtomChatConfig();
        config.ownBubbleColor = 0xFF123456;
        config.otherBubbleColor = 0xFF654321;
        config.textPrimaryColor = 0xFFFF00FF;
        config.panelWidth = 500.0F;
        ThemeService.apply(config, ThemeService.MODERN);
        assertEquals(0xFF123456, config.ownBubbleColor);
        assertEquals(0xFF654321, config.otherBubbleColor);
        assertEquals(0xFFFF00FF, config.textPrimaryColor);
        assertEquals(500.0F, config.panelWidth, 1e-6F);
    }

    @Test
    void unknownThemeIdIsIgnored() {
        AtomChatConfig config = new AtomChatConfig();
        ThemeService.apply(config, "skypunk");
        assertEquals("", config.themeName);
        assertTrue(config.blurEnabled);
    }
}
