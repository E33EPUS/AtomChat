package com.atom.chat.util;

import com.atom.chat.AtomChat;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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
 *   <li>{@code <gameDir>/atomchat-data/image-cache/} — processed chat-image
 *       cache (safe to delete, capped by {@code ImageLoader})</li>
 *   <li>{@code <gameDir>/atomchat-data/avatars/} — companion server avatar
 *       uploads (functional data, not auto-deleted)</li>
 *   <li>{@code <gameDir>/atomchat-data/media/} — companion server hosted chat
 *       media (content-addressed, auto-trimmed by the server config)</li>
 * </ul>
 *
 * <p>Old v0.2.3 locations under {@code config/atomchat/} are migrated once on
 * first start. A directory that has been superseded by the new location is
 * marked with {@code .moved-to-atomchat-data.txt}; startup removes those old
 * trees automatically so a stale multi-GB cache cannot keep occupying disk.
 */
public final class CacheDirs {
    private CacheDirs() {
    }

    public static Path dataRoot() {
        return FMLPaths.GAMEDIR.get().resolve("atomchat-data");
    }

    /** Processed chat-image cache files decoded from downloaded chat links. */
    public static Path imageCacheDir() {
        return dataRoot().resolve("image-cache");
    }

    /** Companion server's uploaded custom-avatar PNGs. */
    public static Path avatarDataDir() {
        return dataRoot().resolve("avatars");
    }

    /** Companion server's hosted chat media (images / animated GIFs). */
    public static Path mediaDataDir() {
        return dataRoot().resolve("media");
    }

    public static void migrateFromOldConfigPaths() {
        Path config = FMLPaths.CONFIGDIR.get().resolve("atomchat");
        migrateDir(config.resolve("image-cache"), imageCacheDir(), "image cache");
        migrateDir(config.resolve("avatars"), avatarDataDir(), "companion avatar data");
        cleanupMovedLegacyDirs(config);
    }

    private static void migrateDir(Path oldDir, Path newDir, String label) {
        if (oldDir == null || newDir == null || !Files.isDirectory(oldDir)) {
            return;
        }
        try {
            if (Files.isDirectory(newDir) && hasEntries(newDir)) {
                // New location already has data: keep it authoritative, do not
                // merge/overwrite. Just mark the old one as no longer read;
                // cleanup below deletes it in the same pass.
                markMoved(oldDir, newDir);
                return;
            }
            Files.createDirectories(newDir.getParent());
            if (Files.exists(newDir)) {
                deleteEmptyTree(newDir);
            }
            Files.move(oldDir, newDir, StandardCopyOption.ATOMIC_MOVE);
            AtomChat.LOGGER.info("Migrated AtomChat {} from {} to {}", label, oldDir, newDir);
        } catch (Exception e) {
            // Never fail startup over a cache move; the old path simply stays.
            AtomChat.LOGGER.warn("Failed to migrate AtomChat {} from {} to {}",
                    label, oldDir, newDir, e);
        }
    }

    /**
     * Deletes old v0.2.3 runtime-data directories that carry the migration
     * marker. The marker proves the new {@code atomchat-data} location is
     * authoritative, so these trees are pure dead weight.
     */
    private static void cleanupMovedLegacyDirs(Path config) {
        cleanupMovedLegacyDir(config.resolve("image-cache"), "image cache");
        cleanupMovedLegacyDir(config.resolve("avatars"), "companion avatar data");
    }

    private static void cleanupMovedLegacyDir(Path oldDir, String label) {
        if (oldDir == null || !Files.isDirectory(oldDir)
                || !Files.exists(oldDir.resolve(".moved-to-atomchat-data.txt"))) {
            return;
        }
        try {
            deleteTree(oldDir);
            AtomChat.LOGGER.info("Removed migrated AtomChat {} at {}", label, oldDir);
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to remove migrated AtomChat {} at {}",
                    label, oldDir, e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
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
