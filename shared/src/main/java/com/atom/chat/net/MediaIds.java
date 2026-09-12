package com.atom.chat.net;

import java.security.MessageDigest;

/**
 * Pure helpers for the server media store (0.2.7): image magic sniffing,
 * content-addressed ids and the id pattern that is safe both on the wire and
 * as a file name.
 *
 * <p>A {@code mediaId} is {@code <64 lowercase hex sha256>.<ext>}, so the file
 * name is derived from content (dedup for free) and can never carry a path
 * segment, a query string or a client-supplied name.
 */
public final class MediaIds {
    /** Payload chunk size; comfortably below the 1 MiB custom-payload cap. */
    public static final int CHUNK_BYTES = 24 * 1024;
    /** CICode url scheme for server-hosted media. */
    public static final String PREFIX = "atomchat-media:";
    private static final int HASH_CHARS = 64;

    private MediaIds() {
    }

    /** File extension for a supported image payload, or null when unsupported. */
    public static String extensionOf(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if (b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b[0] == (byte) 0xFF && b[1] == (byte) 0xD8 && b[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "gif";
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        if (b[0] == 'B' && b[1] == 'M') {
            return "bmp";
        }
        return null;
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is mandatory on every JVM; unreachable.
            throw new IllegalStateException(e);
        }
    }

    /** Content-addressed id; {@code ext} must come from {@link #extensionOf}. */
    public static String idFor(byte[] content, String ext) {
        return sha256Hex(content) + "." + ext;
    }

    /** True when the id matches {@code <64 lowercase hex>.<1..5 lowercase alnum>}. */
    public static boolean isSafeId(String id) {
        if (id == null || id.length() < HASH_CHARS + 2 || id.length() > HASH_CHARS + 6) {
            return false;
        }
        if (id.charAt(HASH_CHARS) != '.') {
            return false;
        }
        for (int i = 0; i < HASH_CHARS; i++) {
            char c = id.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        String ext = id.substring(HASH_CHARS + 1);
        if (ext.isEmpty() || ext.length() > 5) {
            return false;
        }
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z'))) {
                return false;
            }
        }
        return true;
    }

    public static String urlFor(String mediaId) {
        return PREFIX + mediaId;
    }

    /** The media id inside an {@code atomchat-media:} url, or null. */
    public static String idOf(String url) {
        return url != null && url.startsWith(PREFIX) ? url.substring(PREFIX.length()) : null;
    }

    public static int chunkCount(int totalBytes) {
        if (totalBytes <= 0) {
            return 0;
        }
        return (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES;
    }
}
