package com.atom.chat.chat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Parses decorated server chat lines into structured player-name + content pairs.
 *
 * <p>This is a trimmed port of e33chat's MessagePresentation (MIT, same author).
 * It deliberately works on plain strings only — AtomChat renders Skia text
 * without per-run styles today, so the style-slicing machinery is not needed yet.
 */
public final class MessagePresentation {
    private MessagePresentation() {
    }

    public record PlayerLine(String playerName, String displayLabel, String content,
                             int nameStart, int nameEnd, int contentStart) {
    }

    /**
     * Tries every online/known name (longest first to avoid substring mismatches)
     * against the raw chat line. Returns the first successful parse.
     */
    public static Optional<PlayerLine> parseDecoratedPlayerLine(
            String text, Collection<String> onlineNames
    ) {
        if (text == null || onlineNames == null) {
            return Optional.empty();
        }
        return onlineNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .flatMap(name -> parseGeneric(text, name).stream())
                .findFirst();
    }

    /**
     * Generic separator-skipping approach. Finds a player name with word-boundary
     * checks, then skips any mix of whitespace and common separator characters
     * (>, :, ：, », -, |) to locate the message content.
     */
    static Optional<PlayerLine> parseGeneric(String text, String name) {
        if (text == null || name == null) {
            return Optional.empty();
        }
        String cleanName = name.replaceAll("§.", "");
        if (cleanName.isEmpty()) {
            return Optional.empty();
        }
        int idx = text.indexOf(cleanName);
        if (idx < 0) {
            return Optional.empty();
        }

        // Broadcast-spoof guard: separators before the name mean a label like
        // "系统>>Steve" — real chat keeps only decorations before the name.
        String beforeName = text.substring(0, idx);
        if (beforeName.indexOf('>') >= 0 || beforeName.indexOf('»') >= 0
                || beforeName.indexOf('|') >= 0 || beforeName.indexOf(':') >= 0
                || beforeName.indexOf('：') >= 0) {
            return Optional.empty();
        }
        if (isBroadcastLabelPrefix(text, idx)) {
            return Optional.empty();
        }

        int minLen = 3;
        if (idx > 0 && text.charAt(idx - 1) == '<') {
            int closeAngle = text.indexOf('>', idx + cleanName.length());
            if (closeAngle >= 0 && closeAngle - (idx - 1) <= 64) {
                minLen = 1;
            }
        }
        if (minLen == 3 && idx > 0) {
            int bracketClose = text.lastIndexOf(']', idx);
            if (bracketClose >= 0 && idx - bracketClose <= 2) {
                int bracketOpen = text.lastIndexOf('[', bracketClose);
                if (bracketOpen >= 0) {
                    int after = idx + cleanName.length();
                    if (after < text.length()) {
                        char next = text.charAt(after);
                        if (next == ':' || next == '：') {
                            minLen = 1;
                        }
                    }
                }
            }
        }
        if (minLen == 3) {
            int after = idx + cleanName.length();
            if (after < text.length()) {
                char next = text.charAt(after);
                if (next == ':' || next == '：') {
                    minLen = 1;
                }
            }
        }
        if (cleanName.length() < minLen) {
            return Optional.empty();
        }

        int decorativeLen = countDecorativePrefix(text, idx);
        if (idx - decorativeLen >= 30) {
            return Optional.empty();
        }

        if (idx > 0) {
            char prev = text.charAt(idx - 1);
            boolean prevIsColorCode = prev == '§' || (idx >= 2 && text.charAt(idx - 2) == '§');
            if (!prevIsColorCode && (Character.isLetterOrDigit(prev) || prev == '_')) {
                int openAngle = text.lastIndexOf('<', idx);
                int closeAngle = text.indexOf('>', idx + cleanName.length());
                if (openAngle >= 0 && closeAngle >= 0 && closeAngle - openAngle <= 64) {
                    // inside angle brackets like <[VIP]Steve>
                } else {
                    int bracketClose = text.lastIndexOf(']', idx);
                    if (bracketClose >= 0) {
                        int bracketOpen = text.lastIndexOf('[', bracketClose);
                        if (bracketOpen < 0 || idx - bracketClose > 2) {
                            return Optional.empty();
                        }
                    } else {
                        return Optional.empty();
                    }
                }
            }
        }

        int after = idx + cleanName.length();
        if (after < text.length()) {
            char next = text.charAt(after);
            if (Character.isLetterOrDigit(next) || next == '_') {
                return Optional.empty();
            }
        }

        int sep = skipSeparators(text, after);
        if (sep <= after || sep >= text.length()) {
            return Optional.empty();
        }

        String displayLabel = text.substring(0, idx + cleanName.length());
        return Optional.of(new PlayerLine(cleanName, displayLabel, text.substring(sep).strip(),
                idx, idx + cleanName.length(), sep));
    }

    /**
     * Skips the separator run between name and content: whitespace, common chat
     * separators, § color pairs, and whole bracket pairs so name-suffix
     * decorations parse the same way prefix decorations do.
     */
    public static int skipSeparators(String text, int from) {
        int sep = from;
        while (sep < text.length()) {
            char ch = text.charAt(sep);
            if (ch == '§' && sep + 1 < text.length()) {
                sep += 2;
                continue;
            }
            if (ch == '[' || ch == '(' || ch == '<' || ch == '【') {
                char close = ch == '[' ? ']' : ch == '(' ? ')' : ch == '<' ? '>' : '】';
                int end = text.indexOf(close, sep + 1);
                if (end > sep && end - sep <= 32) {
                    sep = end + 1;
                    continue;
                }
            }
            if (Character.isWhitespace(ch) || ch == '>' || ch == ':'
                    || ch == '：' || ch == '»' || ch == '-' || ch == '|') {
                sep++;
            } else {
                break;
            }
        }
        return sep;
    }

    /**
     * True when the gap between name and content holds only whitespace — the
     * shape of a broadcast sentence (Steve joined the game), not chat.
     */
    public static boolean isWhitespaceOnlyGap(String text, int from, int to) {
        if (text == null || to <= from) {
            return false;
        }
        for (int i = from; i < to && i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final java.util.Set<String> BROADCAST_LABELS = java.util.Set.of(
            "系统", "公告", "服务器", "广播", "提示", "通知",
            "system", "server", "notice", "broadcast", "announcement", "alert");

    static boolean isBroadcastLabelPrefix(String cleanText, int nameIdx) {
        String zone = cleanText.substring(0, nameIdx).trim();
        if (zone.isEmpty()) {
            return false;
        }
        while (zone.length() >= 2) {
            char open = zone.charAt(0);
            char close = zone.charAt(zone.length() - 1);
            if ((open == '[' && close == ']') || (open == '【' && close == '】')
                    || (open == '<' && close == '>') || (open == '(' && close == ')')) {
                zone = zone.substring(1, zone.length() - 1).trim();
            } else {
                break;
            }
        }
        if (zone.isEmpty()) {
            return false;
        }
        return BROADCAST_LABELS.contains(zone.toLowerCase(java.util.Locale.ROOT));
    }

    private static int countDecorativePrefix(String text, int upTo) {
        int i = 0;
        while (i < upTo) {
            char c = text.charAt(i);
            if (c == '[') {
                int close = text.indexOf(']', i + 1);
                if (close >= 0 && close < upTo) {
                    i = close + 1;
                    continue;
                }
            }
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close >= 0 && close < upTo) {
                    i = close + 1;
                    continue;
                }
            }
            if (c == '§' && i + 1 < upTo) {
                i += 2;
                continue;
            }
            if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) {
                i++;
                continue;
            }
            break;
        }
        return i;
    }
}
