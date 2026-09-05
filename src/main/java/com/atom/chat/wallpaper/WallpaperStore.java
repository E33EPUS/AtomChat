package com.atom.chat.wallpaper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * The custom panel wallpaper: at most one image, living in
 * {@code <config>/atomchat/wallpaper/}. Setting a new one copies it in (the
 * source is kept) and drops any previous {@code wallpaper.*}; clearing removes
 * it, which falls the panel back to the blur/solid background.
 *
 * <p>Deliberately pure {@code java.nio} like {@code EmoteStore}: no Skia and
 * no Minecraft imports, so the set/clear/scan rules are testable offline.
 * Decoding into a Skia image is the separate {@link WallpaperImage} concern.</p>
 */
public final class WallpaperStore {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};
    private static final String BASE_NAME = "wallpaper";

    private static Path dir;
    private static Path current;

    private WallpaperStore() {
    }

    /** Must be called once from client init, before any UI reads the wallpaper. */
    public static void init(Path wallpaperDir) {
        dir = wallpaperDir;
        refresh();
    }

    public static boolean isSupportedName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** Re-scans the wallpaper dir for a {@code wallpaper.*} file. */
    public static void refresh() {
        current = null;
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.startsWith(BASE_NAME) && isSupportedName(n);
                    })
                    .findFirst()
                    .ifPresent(p -> current = p);
        } catch (IOException e) {
            current = null;
        }
    }

    /** The active wallpaper file, or null when the default background is in use. */
    public static Path current() {
        return current;
    }

    public static boolean isSet() {
        return current != null;
    }

    /** Copies {@code source} in as the wallpaper, replacing any previous one. */
    public static boolean set(Path source) {
        if (dir == null || source == null || !Files.isRegularFile(source)) {
            return false;
        }
        String name = source.getFileName() != null ? source.getFileName().toString() : "";
        if (!isSupportedName(name)) {
            return false;
        }
        String ext = name.substring(name.lastIndexOf('.')).toLowerCase();
        try {
            Files.createDirectories(dir);
            removeExisting();
            Path dest = dir.resolve(BASE_NAME + ext);
            if (source.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                refresh();
                return true;
            }
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes the wallpaper; the panel falls back to blur/solid. */
    public static boolean clear() {
        if (dir == null) {
            return false;
        }
        boolean removed = removeExisting();
        refresh();
        return removed;
    }

    private static boolean removeExisting() {
        boolean removed = false;
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString().toLowerCase();
                if (n.startsWith(BASE_NAME) && isSupportedName(n)) {
                    try {
                        Files.deleteIfExists(p);
                        removed = true;
                    } catch (IOException ignored) {
                        // Leave the file; refresh() will still report it.
                    }
                }
            }
        } catch (IOException ignored) {
            // Treated as "nothing to remove".
        }
        return removed;
    }
}
