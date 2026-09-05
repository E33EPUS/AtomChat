package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Memory of players once seen in chat, so messages from players who have since
 * gone offline can still be claimed as player chat and their UUID recovered
 * from the name. Port of e33chat's seen-player cache (MIT, same author).
 *
 * <p>Why this exists: on servers with relay/bot bridges (and once cross-server
 * history lands), chat lines can name a player who is not in the tab list. The
 * name-keyed memory lets {@code ChatPipeline} still parse those lines and
 * recover identity instead of rendering them as system bubbles; the skin side
 * keeps the last-known head via {@code SkinResolver}'s name cache.
 *
 * <p>Deliberately pure Java (no Minecraft imports) so the remember/lookup/LRU
 * rules are testable offline.
 */
public final class SeenPlayers {
    /** LRU bound — matches e33chat's 512-entry seen cache. */
    private static final int CAP = 512;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private static final Map<String, Seen> BY_KEY = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Seen> eldest) {
            return size() > CAP;
        }
    };

    /** A once-seen player: real profile name (the key's source) and last display form. */
    public record Seen(UUID uuid, String profileName, String displayName) {
    }

    private SeenPlayers() {
    }

    private static String clean(String s) {
        return s == null ? null : s.replaceAll("§.", "").trim();
    }

    private static String key(String profileName) {
        String clean = clean(profileName);
        return clean == null || clean.isEmpty() ? null : clean.toLowerCase(Locale.ROOT);
    }

    /**
     * Remembers a player seen in chat. Nil/unknown UUIDs and blank profile
     * names are skipped; a new blank display name keeps the previous one.
     */
    public static void remember(UUID uuid, String profileName, String displayName) {
        if (uuid == null || uuid.equals(NIL_UUID)) {
            return;
        }
        String k = key(profileName);
        if (k == null) {
            return;
        }
        Seen existing = BY_KEY.get(k);
        String display = clean(displayName);
        if (display == null || display.isEmpty()) {
            display = existing != null ? existing.displayName() : null;
        }
        BY_KEY.put(k, new Seen(uuid, clean(profileName), display));
    }

    /** Reverse lookup: real profile name (display forms tolerated) → UUID. */
    public static UUID findUuid(String name) {
        String k = key(name);
        if (k == null) {
            return null;
        }
        Seen seen = BY_KEY.get(k);
        return seen != null ? seen.uuid() : null;
    }

    public static boolean isKnown(String name) {
        String k = key(name);
        return k != null && BY_KEY.containsKey(k);
    }

    /** All remembered real profile names (clean), newest-touched first. */
    public static List<String> profileNames() {
        List<String> out = new ArrayList<>(BY_KEY.size());
        for (Seen seen : BY_KEY.values()) {
            if (seen.profileName() != null) {
                out.add(seen.profileName());
            }
        }
        return out;
    }

    /** Drops everything; called on server disconnect to avoid cross-server bleed. */
    public static void clear() {
        BY_KEY.clear();
    }
}
