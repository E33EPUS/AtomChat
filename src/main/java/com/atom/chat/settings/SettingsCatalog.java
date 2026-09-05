package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.wallpaper.WallpaperStore;

import java.util.List;

/**
 * The switch rows of every settings section.
 *
 * <p>Only options that are read live are listed here. An entry whose config
 * field nothing consults would be a switch wired to nothing, which is exactly
 * the false affordance this screen must not ship.</p>
 */
public final class SettingsCatalog {
    private SettingsCatalog() {
    }

    public static List<SettingsItem> items(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> List.of(
                    new SettingsItem("blur",
                            "atomchat.settings.appearance.blur",
                            "atomchat.settings.appearance.blur.desc",
                            () -> AtomChatConfig.get().blurEnabled,
                            v -> AtomChatConfig.get().blurEnabled = v,
                            () -> !WallpaperStore.isSet()),
                    new SettingsItem("motion",
                            "atomchat.settings.appearance.motion",
                            "atomchat.settings.appearance.motion.desc",
                            () -> AtomChatConfig.get().animationEnabled,
                            v -> AtomChatConfig.get().animationEnabled = v));
            case CHAT -> List.of(
                    new SettingsItem("entry",
                            "atomchat.settings.chat.entry",
                            "atomchat.settings.chat.entry.desc",
                            () -> AtomChatConfig.get().messageEntryAnimation,
                            v -> AtomChatConfig.get().messageEntryAnimation = v),
                    new SettingsItem("poke",
                            "atomchat.settings.chat.poke",
                            "atomchat.settings.chat.poke.desc",
                            () -> AtomChatConfig.get().avatarPokeEnabled,
                            v -> AtomChatConfig.get().avatarPokeEnabled = v));
            case PRIVACY -> List.of(
                    new SettingsItem("hideBlocked",
                            "atomchat.settings.privacy.hide",
                            "atomchat.settings.privacy.hide.desc",
                            () -> AtomChatConfig.get().hideBlockedMessages,
                            v -> AtomChatConfig.get().hideBlockedMessages = v));
            case ABOUT -> List.of(
                    new SettingsItem("debug",
                            "atomchat.settings.about.debug",
                            "atomchat.settings.about.debug.desc",
                            () -> AtomChatConfig.get().debug,
                            v -> AtomChatConfig.get().debug = v));
        };
    }

    /**
     * Continuous settings, in display order per section. Rendered as slider
     * rows under the switches. Like the switches, only live-read config fields
     * qualify — a slider wired to nothing would be worse than a missing one.
     */
    public static List<SettingsSlider> sliders(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> List.of(
                    new SettingsSlider("opacity",
                            "atomchat.settings.appearance.opacity",
                            0.30F, 1.00F, 0.05F,
                            () -> AtomChatConfig.get().panelOpacity,
                            v -> AtomChatConfig.get().panelOpacity = v,
                            v -> Math.round(v * 100.0F) + "%"),
                    new SettingsSlider("width",
                            "atomchat.settings.appearance.width",
                            400.0F, 600.0F, 10.0F,
                            () -> AtomChatConfig.get().panelWidth,
                            v -> AtomChatConfig.get().panelWidth = v,
                            v -> String.valueOf(Math.round(v))),
                    new SettingsSlider("scale",
                            "atomchat.settings.appearance.scale",
                            0.75F, 1.50F, 0.05F,
                            () -> AtomChatConfig.get().uiScale,
                            v -> AtomChatConfig.get().uiScale = v,
                            v -> "x" + String.format(java.util.Locale.ROOT, "%.2f", v)));
            default -> List.of();
        };
    }

    /**
     * Read-only entries for the about page. Kept out of {@link SettingsItem}
     * because they have no config binding at all — they are facts, not options.
     * A non-null {@code uri} renders the value as a link.
     */
    public static List<InfoRow> aboutCoreRows() {
        return List.of(
                new InfoRow("atomchat.settings.about.version", AtomChat.version(),
                        "https://github.com/E33EPUS/AtomChat/releases"),
                new InfoRow("atomchat.settings.about.license", "MIT",
                        "https://opensource.org/licenses/MIT"),
                new InfoRow("atomchat.settings.about.repo", "E33EPUS/AtomChat",
                        "https://github.com/E33EPUS/AtomChat"));
    }

    /** Bundled third-party components, each linking to its own project page. */
    public static List<InfoRow> thirdPartyRows() {
        return List.of(
                new InfoRow("atomchat.settings.about.thirdparty", "Skija",
                        "https://github.com/HumbleUI/Skija"),
                new InfoRow("atomchat.settings.about.thirdparty.skia", "Skia",
                        "https://skia.org/"),
                new InfoRow("atomchat.settings.about.thirdparty.flatlaf", "FlatLaf",
                        "https://github.com/JFormDesigner/FlatLaf"));
    }

    public record InfoRow(String titleKey, String value, String uri) {
        public boolean isLink() {
            return uri != null && !uri.isBlank();
        }
    }
}
