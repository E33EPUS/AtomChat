package com.atom.chat.emote;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lazily decodes emote files into Skia {@link Image}s, cached by file. Emotes
 * are small stickers so a full decode per file is fine; the cache is bounded
 * by {@link EmoteStore#MAX} plus whatever the user picks. Files that vanish
 * (removed externally) are dropped on the next lookup. Files that fail to
 * decode are remembered so we do not re-decode and re-log them every frame.
 */
public final class EmoteImageCache {
    private final Map<File, Image> cache = new HashMap<>();
    private final Set<File> failed = new HashSet<>();

    public Image image(File file) {
        if (file == null) {
            return null;
        }
        Image img = cache.get(file);
        if (img != null) {
            return img;
        }
        if (failed.contains(file)) {
            return null;
        }
        if (!file.isFile()) {
            cache.remove(file);
            return null;
        }
        try {
            Image decoded = Image.makeFromEncoded(Files.readAllBytes(file.toPath()));
            if (decoded != null) {
                cache.put(file, decoded);
                return decoded;
            }
            failed.add(file);
            return null;
        } catch (IOException e) {
            failed.add(file);
            AtomChat.LOGGER.warn("Emote decode failed: {}", file.getName(), e);
            return null;
        }
    }

    /** Called after an emote file is deleted or replaced. */
    public void invalidate(File file) {
        if (file == null) {
            return;
        }
        cache.remove(file);
        failed.remove(file);
    }

    public void clear() {
        cache.clear();
        failed.clear();
    }
}
