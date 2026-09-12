package com.atom.chat.settings;

/**
 * The settings sub-pages reachable from the settings home grid. Kept as a plain
 * enum with no navigation dependency so {@code NavPage} can carry it as a
 * parameter without the two packages depending on each other.
 */
public enum SettingsSection {
    APPEARANCE("appearance"),
    CHAT("chat"),
    PRIVACY("privacy"),
    ABOUT("about");

    private final String id;

    SettingsSection(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SettingsSection fromId(String id) {
        for (SettingsSection section : values()) {
            if (section.id.equals(id)) {
                return section;
            }
        }
        throw new IllegalArgumentException("Unknown SettingsSection id: " + id);
    }
}
