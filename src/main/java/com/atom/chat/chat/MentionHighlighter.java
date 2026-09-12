package com.atom.chat.chat;

import com.atom.chat.text.RichText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Re-colours plain-text @mentions of the local player with the locally known
 * decorated name (team prefix/suffix, title colour).
 *
 * <p>Chat wires carry mentions as plain text: a decorated label may survive as
 * characters ("@[称号]Name") but its colour does not. The sender band already
 * knows the decorated {@link RichText}, so the panel restores the colour here
 * instead of showing the title in the body's default colour. Deliberately
 * local-only: another player's decoration is not reliably known on this
 * client.
 */
public final class MentionHighlighter {
    private MentionHighlighter() {
    }

    /**
     * Styles every {@code @profileName} and {@code @decoratedName} token in
     * {@code content} with the decorated rich name, preserving all surrounding
     * text and its runs.
     *
     * @param content       rendered message body
     * @param profileName   local player's real profile name (the @ token target)
     * @param decoratedName locally known decorated rich name (prefix + colour)
     * @return the body with coloured mentions, or {@code content} when there is
     *         nothing to style
     */
    public static RichText highlightLocal(RichText content, String profileName, RichText decoratedName) {
        if (content == null || content.isEmpty()
                || profileName == null || profileName.isBlank()
                || decoratedName == null || decoratedName.isEmpty()) {
            return content;
        }
        String plain = content.getString();
        if (plain.indexOf('@') < 0) {
            return content;
        }
        RichText replacement = RichText.concat(RichText.literal("@"), decoratedName.stripInteractions());
        String decoratedText = replacement.getString().substring(1);
        List<String> candidates = new ArrayList<>(2);
        candidates.add(profileName);
        if (!decoratedText.equalsIgnoreCase(profileName)) {
            candidates.add(decoratedText);
        }
        List<int[]> matches = findMatches(content, plain, candidates);
        if (matches.isEmpty()) {
            return content;
        }
        // Apply right-to-left so earlier matches keep their original indices.
        RichText out = content;
        for (int i = matches.size() - 1; i >= 0; i--) {
            int[] match = matches.get(i);
            out = out.splice(match[0], match[1], replacement);
        }
        return out;
    }

    private static List<int[]> findMatches(RichText content, String plain, List<String> names) {
        List<int[]> out = new ArrayList<>();
        String lower = plain.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < plain.length()) {
            int bestStart = -1;
            int bestEnd = -1;
            for (String name : names) {
                String needle = ("@" + name).toLowerCase(Locale.ROOT);
                int idx = lower.indexOf(needle, from);
                if (idx < 0) {
                    continue;
                }
                int end = idx + needle.length();
                // Earliest start wins; at the same start the longest token wins.
                if (bestStart < 0 || idx < bestStart || (idx == bestStart && end > bestEnd)) {
                    bestStart = idx;
                    bestEnd = end;
                }
            }
            if (bestStart < 0) {
                break;
            }
            if (isBoundary(plain, bestStart - 1) && isBoundary(plain, bestEnd)
                    && !isInsideInteraction(content, bestStart)) {
                out.add(new int[]{bestStart, bestEnd});
            }
            from = Math.max(bestEnd, bestStart + 1);
        }
        return out;
    }

    /**
     * True when the token starting at {@code start} sits inside a run that
     * already carries a click/hover interaction (a linkified URL, for example).
     * Replacing such a run would silently drop the interaction, so the mention
     * is left as the server sent it.
     */
    private static boolean isInsideInteraction(RichText content, int start) {
        int pos = 0;
        for (RichText.RichRun run : content.runs()) {
            int end = pos + run.text().length();
            if (start < end) {
                return run.style().getClickEvent() != null || run.style().getHoverEvent() != null;
            }
            pos = end;
        }
        return false;
    }

    /**
     * Whether the character at {@code index} can sit outside a mention token.
     * Only ASCII name characters count: player names are {@code [A-Za-z0-9_]},
     * so "x@Name" (e-mail shaped) and "@Name2" must not match.
     */
    private static boolean isBoundary(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return true;
        }
        char c = text.charAt(index);
        return !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_');
    }
}
