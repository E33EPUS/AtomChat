package com.atom.chat.util;

import com.atom.chat.AtomChat;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Runtime data/cache location for AtomChat.
 *
 * <p>User-editable settings and user-selected content stay under
 * {@code config/atomchat/} (atomchat-client.json, emotes, wallpaper, local
 * avatar, history). Auto-downloaded / auto-uploaded runtime data — chat image
 * cache and companion avatar uploads — belong beside the config dir but not
 * inside it, so the config folder does not silently grow into a media dump.
 *
 * <p>Layout:
 * <ul>
 *   <li>{@code <gameDir>/atomchat-data/image-cache/} — raw chat-image bytes
 *       (safe to delete, capped by {@code ImageLoader})</li>
 *   <li>{@code <gameDir>/atomchat-data/avatars/} — companion server avatar
 *       uploads (functional data, not auto-deleted)</li>
 * </ul>
 *
 * <p>Old v0.2.3 locations under {@code config/atomchat/} are migrated once on
 * first start. Migration never deletes the old directory; it leaves a short
 * marker file explaining where the data went.
 */
public final class CacheDirs {
    private CacheDirs() {
    }

    public static Path dataRoot() {
        return FMLPaths.GAMEDIR.get().resolve("atomchat-data");
    }

    /** Decoded-image raw bytes downloaded from chat links. */
    public static Path imageCacheDir() {
        return dataRoot().resolve("image-cache");
    }

    /** Companion server's uploaded custom-avatar PNGs. */
    public static Path avatarDataDir() {
        return dataRoot().resolve("avatars");
    }

    public static void migrateFromOldConfigPaths() {
        Path config = FMLPaths.CONFIGDIR.get().resolve("atomchat");
        migrateDir(config.resolve("image-cache"), imageCacheDir(), "image cache");
        migrateDir(config.resolve("avatars"), avatarDataDir(), "companion avatar data");
    }

    private static void migrateDir(Path oldDir, Path newDir, String label) {
        if (oldDir == null || newDir == null || !Files.isDirectory(oldDir)) {
            return;
        }
        try {
            if (Files.isDirectory(newDir) && hasEntries(newDir)) {
                // New location already has data: keep it authoritative, do not
                // merge/overwrite. Just mark the old one so users know it is
                // no longer read.
                markMoved(oldDir, newDir);
                return;
            }
            Files.createDirectories(newDir.getParent());
            if (Files.exists(newDir)) {
                deleteEmptyTree(newDir);
            }
            Files.move(oldDir, newDir, StandardCopyOption.ATOMIC_MOVE);
            markMoved(newDir, newDir);
            AtomChat.LOGGER.info("Migrated AtomChat {} from {} to {}", label, oldDir, newDir);
        } catch (Exception e) {
            // Never fail startup over a cache move; the old path simply stays.
            AtomChat.LOGGER.warn("Failed to migrate AtomChat {} from {} to {}",
                    label, oldDir, newDir, e);
        }
    }

    private static boolean hasEntries(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.findAny().isPresent();
        }
    }

    private static void deleteEmptyTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            if (stream.findAny().isPresent()) {
                return; // not empty, leave it alone
            }
        }
        Files.deleteIfExists(dir);
    }

    private static void markMoved(Path dir, Path destination) {
        try {
            Path marker = dir.resolve(".moved-to-atomchat-data.txt");
            if (!Files.exists(marker)) {
                Files.writeString(marker,
                        "AtomChat moved this runtime data to:\n" + destination.toAbsolutePath()
                                + "\n\nThe files here are no longer read and may be deleted.\n");
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to write migration marker in {}", dir, e);
        }
    }
}
