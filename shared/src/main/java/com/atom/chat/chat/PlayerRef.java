package com.atom.chat.chat;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable reference to a chat partner. UUID wins when known (online/captured
 * routes); the clean real profile name is always kept for command sending and
 * skin/name lookup fallback.
 */
public record PlayerRef(UUID uuid, String name) {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public PlayerRef {
        uuid = uuid != null && uuid.equals(NIL_UUID) ? null : uuid;
        name = clean(name);
    }

    public static PlayerRef of(UUID uuid, String name) {
        return new PlayerRef(uuid, name);
    }

    /**
     * A key safe for maps/lists. Real name is the stable conversation key for
     * v0.1.5: online entries carry a UUID while outgoing /msg echoes are parsed
     * from text and may only know the name, and those must land in the same
     * conversation. UUID remains available for skins and future identity work.
     */
    public String key() {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return "n:" + n;
    }

    /** The real profile name to use in commands ({@code /msg}, {@code /tp}, ...). */
    public String realName() {
        return name;
    }

    public String displayName() {
        return name != null ? name : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlayerRef other)) {
            return false;
        }
        return key().equals(other.key());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key());
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "");
        return stripped.isBlank() ? null : stripped.trim();
    }
}
