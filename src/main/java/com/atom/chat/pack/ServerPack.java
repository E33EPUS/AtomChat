package com.atom.chat.pack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The immutable snapshot a server hands to clients: the emote files it hosts,
 * the phrases its admin wrote, and the identity it shows in the panel.
 *
 * <p>Everything is derived from content, so the same directory always produces
 * the same {@link #packHash()}: clients only re-download when that hash changes
 * (0.2.9 decision 18), and they verify every received file against the manifest
 * before accepting any of it (decision 21).
 *
 * <p>The canonical form is line based and escaped, so no file name, phrase or
 * server name can make two different packs hash the same.
 */
public final class ServerPack {
    /** Manifest format version; bump it if the canonical form ever changes. */
    public static final int FORMAT_VERSION = 1;
    /** Longest allowed file name, matching the whitelist pattern below. */
    public static final int MAX_NAME_CHARS = 64;
    /** Server-side cap on distributed phrases (decision 11). */
    public static final int MAX_PHRASES = 20;
    /** Longest single phrase, in characters. */
    public static final int MAX_PHRASE_CHARS = 200;
    /** Longest server name shown in the panel. */
    public static final int MAX_SERVER_NAME_CHARS = 48;
    /** Largest accepted {@code server-icon.png}. */
    public static final int MAX_ICON_BYTES = 64 * 1024;

    /**
     * Names the client is allowed to write to disk. Lowercase-only on purpose:
     * the builder lowercases what it finds, so the wire contract has exactly one
     * spelling per file and a client can never be handed a path fragment.
     */
    private static final Pattern SAFE_NAME =
            Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}\\.(?:png|jpg|jpeg|gif)");
    private static final Pattern FORMATTING_CODE = Pattern.compile("\u00A7.");

    /** One hosted emote file, addressed by content. */
    public record FileEntry(String name, String sha256Hex, int size) {
        public FileEntry {
            if (!isSafeName(name)) {
                throw new IllegalArgumentException("unsafe pack file name: " + name);
            }
            if (sha256Hex == null || sha256Hex.length() != 64) {
                throw new IllegalArgumentException("bad sha256 for " + name);
            }
            if (size < 0) {
                throw new IllegalArgumentException("bad size for " + name);
            }
        }
    }

    private final String packHash;
    private final List<FileEntry> files;
    private final List<String> phrases;
    private final String serverName;
    private final byte[] icon;

    public ServerPack(String packHash, List<FileEntry> files, List<String> phrases,
                      String serverName, byte[] icon) {
        this.packHash = packHash;
        this.files = List.copyOf(files);
        this.phrases = List.copyOf(phrases);
        this.serverName = serverName == null ? "" : serverName;
        this.icon = icon == null || icon.length == 0 ? null : icon.clone();
    }

    public String packHash() {
        return packHash;
    }

    /** Emote files, sorted by name (the canonical order). */
    public List<FileEntry> files() {
        return files;
    }

    public List<String> phrases() {
        return phrases;
    }

    /** Cleaned server name; empty when the server has nothing to show. */
    public String serverName() {
        return serverName;
    }

    /** {@code server-icon.png} bytes, or null when the server has none. */
    public byte[] icon() {
        return icon == null ? null : icon.clone();
    }

    public boolean hasIcon() {
        return icon != null;
    }

    public long totalBytes() {
        long total = 0L;
        for (FileEntry file : files) {
            total += file.size();
        }
        return total;
    }

    /** True when the pack carries nothing but (possibly) a name and an icon. */
    public boolean isEmpty() {
        return files.isEmpty() && phrases.isEmpty();
    }

    /**
     * The text the pack hash covers. File order is normalised here rather than
     * required from callers, so a reviewer re-sorting the directory cannot
     * silently invalidate every client's cache.
     */
    public static String canonical(List<FileEntry> files, List<String> phrases,
                                   String serverName, byte[] icon) {
        List<FileEntry> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(FileEntry::name));
        StringBuilder sb = new StringBuilder(256);
        sb.append("atomchat-pack\t").append(FORMAT_VERSION).append('\n');
        for (FileEntry file : sorted) {
            sb.append("file\t").append(file.name()).append('\t')
                    .append(file.sha256Hex()).append('\t').append(file.size()).append('\n');
        }
        for (String phrase : phrases) {
            sb.append("phrase\t");
            appendEscaped(sb, phrase);
            sb.append('\n');
        }
        sb.append("server\t");
        appendEscaped(sb, serverName == null ? "" : serverName);
        sb.append('\n');
        sb.append("icon\t")
                .append(icon == null || icon.length == 0 ? "-" : sha256Hex(icon))
                .append('\n');
        return sb.toString();
    }

    public static String hashOf(String canonical) {
        return sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isSafeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    /**
     * Turns a raw server name — the admin's override or the MOTD — into the one
     * line the panel shows: formatting codes stripped, first non-blank line
     * only, trimmed and capped.
     */
    public static String cleanServerName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String plain = FORMATTING_CODE.matcher(raw).replaceAll("");
        for (String line : plain.split("\\R", -1)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > MAX_SERVER_NAME_CHARS
                        ? trimmed.substring(0, MAX_SERVER_NAME_CHARS)
                        : trimmed;
            }
        }
        return "";
    }

    /** Lowercases a name for the wire; the caller decides whether it is safe. */
    public static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    /** SHA-256 as lowercase hex; mirrors {@code MediaIds.sha256Hex} for pack data. */
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

    private static void appendEscaped(StringBuilder sb, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
    }
}
