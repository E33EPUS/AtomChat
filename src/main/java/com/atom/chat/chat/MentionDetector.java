package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of e33chat's MentionDetector: decides whether a chat line mentions the
 * local player. Two shapes count:
 * <ul>
 *   <li>{@code @Name} — always a mention when the name ends the token;</li>
 *   <li>the bare name as a standalone token — only when {@code requireAt} is
 *       off (servers where people just say your name).</li>
 * </ul>
 * A reply to the local player's own message also counts ({@code replySender}).
 */
public final class MentionDetector {
    private MentionDetector() {
    }

    public static boolean isMentioned(String text, String localPlayerName,
                                      boolean requireAt, String replySender) {
        if (text == null || localPlayerName == null || localPlayerName.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        String needle = localPlayerName.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf(needle, idx)) >= 0) {
            int end = idx + needle.length();
            boolean hasAt = idx > 0 && text.charAt(idx - 1) == '@';
            if (hasAt) {
                if (end >= text.length() || !isNameCharacter(text.charAt(end))) {
                    return true;
                }
            } else if (!requireAt) {
                boolean leftOk = idx == 0 || !isNameCharacter(text.charAt(idx - 1));
                boolean rightOk = end >= text.length() || !isNameCharacter(text.charAt(end));
                if (leftOk && rightOk) {
                    return true;
                }
            }
            idx = end;
        }
        return replySender != null && replySender.equals(localPlayerName);
    }

    public static List<MentionRange> findMentionRanges(String text, String localPlayerName,
                                                       boolean requireAt) {
        List<MentionRange> ranges = new ArrayList<>();
        if (text == null || localPlayerName == null || localPlayerName.isBlank()) {
            return ranges;
        }
        String lower = text.toLowerCase();
        String needle = localPlayerName.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf(needle, idx)) >= 0) {
            int end = idx + needle.length();
            boolean hasAt = idx > 0 && text.charAt(idx - 1) == '@';
            int matchStart = hasAt ? idx - 1 : idx;
            if ((!requireAt || hasAt) && (end >= text.length() || !isNameCharacter(text.charAt(end)))) {
                ranges.add(new MentionRange(matchStart, end));
            }
            idx = end;
        }
        return ranges;
    }

    private static boolean isNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public record MentionRange(int start, int end) {
    }
}
