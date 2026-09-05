package com.atom.chat.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G1 (e33chat parity) text-layer whisper fallback.
 *
 * <p>Vanilla {@code /msg} lines are handled authoritatively by
 * {@link PrivateChatParser} via translation keys. Chat plugins (EssentialsX,
 * CMI, DeluxeChat, ...), hybrid setups and bot relays reformat private
 * messages as plain system-channel text, which used to fall through to the
 * public-chat guard — whose separator skipping mis-claims
 * {@code "[Steve -> me] hi"} as a public bubble from Steve.
 *
 * <p>This parser only claims lines with unmistakable whisper shape, and only
 * when one side resolves to the local player (or the literal
 * {@code 我/You/me} placeholder some plugins emit). When neither side is us
 * the line is left for the guard/gray fallback — admin-visible whisper
 * channels between two other players are not our business.
 *
 * <p>Pure strings, no Minecraft imports, so the shapes stay unit-testable
 * while the real plugin formats are only documented (no test server).
 */
public final class WhisperTextParser {
    private WhisperTextParser() {
    }

    /** A detected private-message line. {@code incoming=true} means the partner sent it. */
    public record WhisperHit(boolean incoming, String partnerDisplay, String content) {
    }

    private static final String ARROW = "(?:->|→|»)";

    /** CMI receiver side: "[/msg from [Steve]] hi" / "[/msg from Steve] hi". */
    private static final Pattern CMI_FROM =
            Pattern.compile("^\\[/msg from \\[?(.{1,64}?)\\]?\\]\\s*[:：]?\\s*(.+)$");
    /** CMI sender side: "[/msg sent -> [Steve]] hi". */
    private static final Pattern CMI_SENT =
            Pattern.compile("^\\[/msg sent -> \\[?(.{1,64}?)\\]?\\]\\s*[:：]?\\s*(.+)$");
    /** EssentialsX default: "[Steve -> me] hi" (bracketed arrow, colon optional). */
    private static final Pattern ARROW_BRACKETED = Pattern.compile(
            "^\\[(.{1,48}?)\\s*" + ARROW + "\\s*(.{1,48}?)\\]\\s*[:：]?\\s*(.+)$");
    /** DeluxeChat style: "Steve -> You : hi" (unbracketed arrow requires a colon). */
    private static final Pattern ARROW_COLON = Pattern.compile(
            "^(.{1,32}?)\\s*" + ARROW + "\\s*(.{1,32}?)\\s*[:：]\\s*(.+)$");
    /** "Steve 悄悄地对你说: hi" / "Steve whispers to you: hi" / "Steve whispers: hi". */
    private static final Pattern KEYWORD_IN = Pattern.compile(
            "^(.{1,32}?)\\s*(?:悄悄地?对你说|悄悄(?:地)?跟你说|对你说|悄悄(?:地)?说|密语|"
                    + "whispers?(?:\\s+to\\s+you)?)\\s*[:：]?\\s*(.+)$");
    /**
     * "你对Steve悄悄地说：hi" / "You whisper to Steve: hi". The EN branch
     * requires the trailing colon: with an optional colon the lazy partner
     * capture stops at the first character ("S" of "Steve") and never
     * backtracks, since the colon is not needed to complete the match.
     */
    private static final Pattern KEYWORD_OUT = Pattern.compile(
            "^(?:你(?:悄悄地?|私下)?对(.{1,32}?)悄悄?地?说\\s*[:：]?"
                    + "|[Yy]ou\\s+whispers?\\s+to\\s+([^:：]{1,32}?)\\s*[:：])"
                    + "\\s*(.+)$");

    /**
     * Tries the configured shapes in order. {@code ownProfile} is the local
     * player's profile name (undecorated); may be null, in which case only
     * the CMI/keyword literal forms can match.
     */
    public static WhisperHit tryParse(String line, String ownProfile) {
        if (line == null || line.length() > 256) {
            return null;
        }
        String text = line.strip();
        if (text.isEmpty()) {
            return null;
        }

        Matcher m = CMI_FROM.matcher(text);
        if (m.matches()) {
            return incoming(m.group(1), m.group(2));
        }
        m = CMI_SENT.matcher(text);
        if (m.matches()) {
            return outgoing(m.group(1), m.group(2), ownProfile);
        }
        m = ARROW_BRACKETED.matcher(text);
        if (m.matches()) {
            return arrow(m.group(1), m.group(2), m.group(3), ownProfile);
        }
        m = ARROW_COLON.matcher(text);
        if (m.matches()) {
            return arrow(m.group(1), m.group(2), m.group(3), ownProfile);
        }
        m = KEYWORD_OUT.matcher(text);
        if (m.matches()) {
            String partner = cleanSide(m.group(1) != null ? m.group(1) : m.group(2));
            String content = m.group(3).strip();
            if (partner != null && !content.isEmpty()) {
                return new WhisperHit(false, partner, content);
            }
            return null;
        }
        // KEYWORD_OUT first: "你对X悄悄地说" would otherwise be claimed by the
        // incoming keyword ("悄悄地说") with the prefix swallowed as the sender.
        m = KEYWORD_IN.matcher(text);
        if (m.matches()) {
            String sender = cleanSide(m.group(1));
            String content = m.group(2).strip();
            if (sender != null && !content.isEmpty()) {
                return new WhisperHit(true, sender, content);
            }
            return null;
        }
        return null;
    }

    private static WhisperHit arrow(String rawSideA, String rawSideB, String content, String ownProfile) {
        String a = cleanSide(rawSideA);
        String b = cleanSide(rawSideB);
        String body = content.strip();
        if (a == null || b == null || body.isEmpty()) {
            return null;
        }
        // Only claim when one side is us; otherwise it is an admin-visible
        // whisper channel between two other players — leave it alone.
        if (isOwnSide(b, ownProfile)) {
            return new WhisperHit(true, a, body);
        }
        if (isOwnSide(a, ownProfile)) {
            return new WhisperHit(false, b, body);
        }
        return null;
    }

    private static WhisperHit incoming(String partner, String content) {
        String p = cleanSide(partner);
        String body = content.strip();
        return p == null || body.isEmpty() ? null : new WhisperHit(true, p, body);
    }

    private static WhisperHit outgoing(String partner, String content, String ownProfile) {
        String p = cleanSide(partner);
        String body = content.strip();
        if (p == null || body.isEmpty()) {
            return null;
        }
        // The CMI "sent" shape is only ever produced for our own sends; the
        // own-side check does not apply because the partner is the other player.
        return new WhisperHit(false, p, body);
    }

    private static boolean isOwnSide(String side, String ownProfile) {
        if (side.equalsIgnoreCase("me") || side.equals("我") || side.equals("自己")) {
            return true;
        }
        if (side.equalsIgnoreCase("you") && ownProfile == null) {
            return true;
        }
        return ownProfile != null && side.equalsIgnoreCase(ownProfile);
    }

    private static String cleanSide(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        if (stripped.isEmpty() || stripped.length() > 48) {
            return null;
        }
        // A separator inside a "name" means we matched into prose, not a name.
        if (stripped.indexOf(':') >= 0 || stripped.indexOf('：') >= 0) {
            return null;
        }
        return stripped;
    }
}
