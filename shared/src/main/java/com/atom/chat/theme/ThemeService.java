package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;

/**
 * Built-in theme presets and the corner-style scale factor.
 *
 * <p>A theme is a <em>snapshot application</em>: picking one writes a fixed
 * set of appearance values into the existing config fields (and persists the
 * config). There is no overlay layer — after applying, every single knob
 * stays individually editable, and the subtitle on the theme card simply
 * shows the last applied preset. Colours and the wallpaper are deliberately
 * <b>not</b> part of a preset: they are personal choices a re-skin has no
 * business overwriting.</p>
 *
 * <p>Pure Java — unit tests cover the mappings.</p>
 */
public final class ThemeService {
    /** The shipped default: translucent panel, blur on, white bezel, large corners. */
    public static final String FROSTED = "frosted";
    /** Opaque modern flat: full opacity, no blur, no bezel, small corners. */
    public static final String MODERN = "modern";

    private ThemeService() {
    }

    /**
     * Corner scale factor for panel/card/pill/popup surfaces. Chat bubbles are
     * excluded by design — their radius is part of the message identity, not
     * of the surrounding chrome.
     */
    public static float cornerFactor(String cornerStyle) {
        return switch (cornerStyle == null ? "large" : cornerStyle) {
            case "medium" -> 0.5F;
            case "small" -> 0.35F;
            default -> 1.0F; // large — the shipped default
        };
    }

    /**
     * Writes the preset's appearance values into the config. The caller
     * persists. Unknown ids are ignored so a hand-edited themeName can never
     * scramble the settings. Both presets keep the panel outline — it is the
     * mod's signature, only the surface style changes.
     */
    public static void apply(AtomChatConfig config, String themeId) {
        switch (themeId == null ? "" : themeId) {
            case MODERN -> {
                config.panelOpacity = 1.0F;
                config.blurEnabled = false;
                config.panelOutline = true;
                config.cardTint = 1.0F;
                config.cardColor = 0xFF222831;
            }
            case FROSTED -> {
                config.panelOpacity = 0.93F;
                config.blurEnabled = true;
                config.panelOutline = true;
                config.cardTint = 0.235F;
                config.cardColor = 0xFFFFFFFF;
            }
            default -> {
                return;
            }
        }
        config.themeName = themeId;
    }
}
