package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one grammar for an image CICode.
 *
 * <p>Three places used to decide independently what a code looks like: the panel
 * ({@code Cicodes}), the vanilla-HUD rewrite ({@code ChatTextRewriter}) and the
 * server media scheme ({@code MediaIds}).  They disagreed — the rewrite is
 * case-insensitive and accepts both bracket widths, while {@code Cicodes}
 * searched for the exact literal {@code [[CICode,url=}} and required
 * {@code url=} to come first.  A code that deviated therefore still became an
 * image for the vanilla HUD but was drawn by the panel as a TEXT bubble: the raw
 * protocol text went on screen and the linkifier turned its tail
 * ({@code ,name=…,w=…,h=…]]}) into a clickable URL, which threw
 * {@code URISyntaxException} when clicked.
 *
 * <p>Everything that needs to recognise a code asks here.  This class is pure
 * Java on purpose: one rule, three platforms, no mapping twins.
 */
public final class ImageCode {

    /** Server-hosted media is referenced with this scheme instead of http(s). */
    public static final String MEDIA_SCHEME = "atomchat-media:";

    // [[CICode, ...]] or [CICode, ...].  The tag is case-insensitive, whitespace
    // is allowed around the tag and the closing brackets, and the parameter list
    // is read by name so its order does not matter.
    private static final Pattern CODE = Pattern.compile(
            "(?i)\\[\\s*\\[\\s*cicode\\s*,([^\\]]*)\\]\\s*\\]"
                    + "|\\[\\s*cicode\\s*,([^\\]]*)\\]");

    private ImageCode() {
    }

    /** url / name / intrinsic size.  width and height are 0 when the code has none. */
    public record Meta(String url, String name, int width, int height) {
    }

    /**
     * The text's image code, or null when it has none — or has one with no
     * usable url, which is not an image either.
     */
    public static Meta parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = CODE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String body = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return parseBody(body);
    }

    /**
     * Whether the text carries a code at all, including one this class cannot
     * turn into an image.  Callers use this to keep raw protocol text off the
     * screen even when there is nothing to render.
     */
    public static boolean contains(String text) {
        return text != null && !text.isEmpty() && CODE.matcher(text).find();
    }

    /**
     * [start, end) of every code in the text, in order.  The linkifier uses
     * these to stay off a code's tail.
     */
    public static List<int[]> ranges(String text) {
        List<int[]> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher matcher = CODE.matcher(text);
        while (matcher.find()) {
            out.add(new int[]{matcher.start(), matcher.end()});
        }
        return out;
    }

    /** Whether any of {@code ranges} covers the given index. */
    public static boolean covers(List<int[]> ranges, int index) {
        for (int[] range : ranges) {
            if (index >= range[0] && index < range[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a parameter list by name: {@code url}, {@code name}, {@code w},
     * {@code h}, in any order, each value trimmed.
     *
     * <p>Values are split on commas, which is the same limitation the writer
     * has: it never escapes a comma inside a file name.
     */
    private static Meta parseBody(String body) {
        String url = null;
        String name = "";
        int width = 0;
        int height = 0;
        for (String part : body.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            switch (key) {
                case "url" -> url = value;
                case "name" -> name = value;
                case "w" -> width = parseSize(value);
                case "h" -> height = parseSize(value);
                default -> {
                    // Unknown parameter: ignored, so a newer sender never breaks an
                    // older reader.
                }
            }
        }
        if (url == null || url.isEmpty()) {
            return null;
        }
        return new Meta(url, name, width, height);
    }

    private static int parseSize(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0; // malformed size: the bubble falls back to the placeholder box
        }
    }
}
