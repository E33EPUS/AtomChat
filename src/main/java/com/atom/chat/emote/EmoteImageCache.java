package com.atom.chat.emote;

import com.atom.chat.AtomChat;
import com.atom.chat.image.AnimatedImage;
import com.atom.chat.image.GifDecoder;
import io.github.humbleui.skija.Image;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lazily decodes emote files into Skia images, cached by file (0.2.7 GIF pass).
 * Static images (png/jpg/jpeg) cache one {@link Image}; an animated GIF caches
 * an {@link AnimatedImage} and every lookup returns the frame for the current
 * tick, so the emote grid animates exactly like the sent message does.
 *
 * <p>Files that vanish (removed externally) are dropped on the next lookup, and
 * files that fail to decode are remembered so we neither re-decode nor re-log
 * them every frame. Frames are never eagerly closed: the render thread may
 * still hold the frame it was handed, and Skija finalises the native memory.
 */
public final class EmoteImageCache {
    private final Map<File, Image> staticCache = new HashMap<>();
    private final Map<File, AnimatedImage> animatedCache = new HashMap<>();
    private final Set<File> failed = new HashSet<>();

    /** Current frame of the emote, or null while absent/undecodable. */
    public Image image(File file) {
        return image(file, System.currentTimeMillis());
    }

    /** Test seam: pick the animation frame for an explicit wall clock. */
    public Image image(File file, long nowMs) {
        if (file == null) {
            return null;
        }
        AnimatedImage animated = animatedCache.get(file);
        if (animated != null) {
            return animated.frameAt(nowMs);
        }
        Image img = staticCache.get(file);
        if (img != null) {
            return img;
        }
        if (failed.contains(file)) {
            return null;
        }
        if (!file.isFile()) {
            staticCache.remove(file);
            animatedCache.remove(file);
            return null;
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            AnimatedImage decodedAnimation = GifDecoder.decode(bytes, nowMs);
            if (decodedAnimation != null) {
                animatedCache.put(file, decodedAnimation);
                return decodedAnimation.frameAt(nowMs);
            }
            Image decoded = Image.makeFromEncoded(bytes);
            if (decoded != null) {
                staticCache.put(file, decoded);
                return decoded;
            }
            failed.add(file);
            return null;
        } catch (Exception e) {
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
        staticCache.remove(file);
        animatedCache.remove(file);
        failed.remove(file);
    }

    public void clear() {
        staticCache.clear();
        animatedCache.clear();
        failed.clear();
    }
}
