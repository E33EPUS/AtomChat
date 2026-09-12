package com.atom.chat.chat;

import java.util.Collection;

/**
 * Own-name matching for echo suppression.
 *
 * <p>Servers put the <em>rendered</em> name on the wire: a scoreboard team
 * prefix/suffix or a tab-list display name wraps our profile name
 * ({@code 123E33EPUS} for prefix {@code 123} + {@code E33EPUS}), and NCR-style
 * relays drop the signed identity that would otherwise let us match by UUID.
 * Comparing that wire name against the bare profile name alone therefore fails,
 * and our own message gets stored a second time as someone else's bubble — the
 * 2026-09-11 "{@code /team} prefix echo" bug.
 *
 * <p>Matching is exact after stripping formatting codes: no prefix/suffix
 * guessing, so a lookalike ({@code NotE33EPUS}, {@code 123E33EPUSx}) can never
 * swallow a real message. When in doubt we show a duplicate rather than drop a
 * line (e33chat's "宁重复不吞" rule).
 */
public final class OwnNameMatcher {
    private OwnNameMatcher() {
    }

    /** True when {@code wireName} is one of our own renderings. */
    public static boolean matches(String wireName, Collection<String> ownCandidates) {
        String needle = normalize(wireName);
        if (needle == null || ownCandidates == null) {
            return false;
        }
        for (String candidate : ownCandidates) {
            String normalized = normalize(candidate);
            if (normalized != null && normalized.equals(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Drops legacy colour codes and trims; null/blank becomes null. */
    static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String stripped = name.replaceAll("§.", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
