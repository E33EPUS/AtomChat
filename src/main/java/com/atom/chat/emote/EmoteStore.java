package com.atom.chat.emote;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local emote pack: static images in {@code <configDir>/atomchat/emotes/}
 * (png/jpg/jpeg only). Adding a file copies it in (the source is kept),
 * duplicates overwrite by name, the list is sorted by file name and capped at
 * {@link #MAX} entries — mirroring e33chat's EmoteStore.
 *
 * <p>This class is deliberately pure {@code java.nio}: no Skia and no
 * Minecraft/Fabric imports, so the scan/sort/cap/add/remove logic is
 * unit-testable offline. Decoding files into Skia {@link io.github.humbleui.skija.Image}s
 * is the separate {@link EmoteImageCache} concern.
 */
public final class EmoteStore {
    public static final int MAX = 10;

    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

    private final Path dir;
    private List<File> cached = Collections.emptyList();

    public EmoteStore(Path dir) {
        this.dir = dir;
        refresh();
    }

    public Path dir() {
        return dir;
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

    /** Re-scans the emote dir, sorts by name and truncates to {@link #MAX}. */
    public void refresh() {
        List<File> files = new ArrayList<>();
        File[] listed = dir.toFile().listFiles();
        if (listed != null) {
            for (File f : listed) {
                if (f.isFile() && isSupportedName(f.getName())) {
                    files.add(f);
                }
            }
            files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            while (files.size() > MAX) {
                files.remove(files.size() - 1);
            }
        }
        cached = Collections.unmodifiableList(files);
    }

    /** Current emotes, sorted by name. Never null. */
    public List<File> list() {
        return cached;
    }

    public int count() {
        return cached.size();
    }

    public boolean isFull() {
        return cached.size() >= MAX;
    }

    /** Copies {@code source} into the emote dir. False when full or invalid. */
    public boolean add(File source) {
        if (source == null || !source.isFile() || !isSupportedName(source.getName())) {
            return false;
        }
        if (isFull()) {
            return false;
        }
        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve(source.getName());
            // Re-picking a file that already lives in the emote dir is a no-op.
            if (source.toPath().toAbsolutePath().normalize()
                    .equals(dest.toAbsolutePath().normalize())) {
                refresh();
                return true;
            }
            Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes one of our emote files. Refuses anything outside the emote dir. */
    public boolean remove(File emote) {
        if (emote == null) {
            return false;
        }
        Path emotePath = emote.toPath().toAbsolutePath().normalize();
        if (!emotePath.startsWith(dir.toAbsolutePath().normalize())) {
            return false;
        }
        try {
            boolean deleted = emote.delete();
            if (deleted) {
                refresh();
            }
            return deleted;
        } catch (Exception e) {
            return false;
        }
    }
}
