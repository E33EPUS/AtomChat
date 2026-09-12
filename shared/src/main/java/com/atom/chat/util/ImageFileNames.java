package com.atom.chat.util;

import java.util.regex.Pattern;

/**
 * Turns an image url into a file name the file system will accept.
 *
 * <p>The file picker's suggested name and the save dialog's default both come
 * from here, so the two can never disagree about how a url is spelled on disk.
 *
 * <p>Hosted media is why the rules look like this: an {@code atomchat-media:}
 * url carries no path segment, so the usual "take everything after the last
 * slash" split handed back the scheme as part of the name
 * ({@code atomchat-media:<sha>.png}), and a colon is not a legal file name
 * character on Windows.
 */
public final class ImageFileNames {
    /** Name used whenever nothing usable can be derived from the url. */
    public static final String FALLBACK = "image.png";

    /** A leading url scheme, e.g. {@code atomchat-media:} or {@code https:}. */
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*:");
    /** Characters Windows rejects in a file name, path separators included. */
    private static final Pattern ILLEGAL = Pattern.compile("[/\\\\:*?\"<>|]");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private ImageFileNames() {
    }

    /** Last path segment of a url, without query, fragment, scheme or frame. */
    public static String fromUrl(String url) {
        if (url == null || url.isBlank()) {
            return FALLBACK;
        }
        String clean = url.trim();
        int query = clean.indexOf('?');
        int fragment = clean.indexOf('#');
        int cut = query < 0 ? fragment : (fragment < 0 ? query : Math.min(query, fragment));
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        clean = SCHEME.matcher(clean).replaceFirst("");
        int slash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        return sanitize(name);
    }

    /** Replaces everything a file system refuses to store, and never returns blank. */
    public static String sanitize(String name) {
        if (name == null) {
            return FALLBACK;
        }
        String cleaned = ILLEGAL.matcher(CONTROL.matcher(name).replaceAll("_")).replaceAll("_").strip();
        // "." and ".." are directories, never file names; blank carries no name.
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return FALLBACK;
        }
        return cleaned;
    }
}
