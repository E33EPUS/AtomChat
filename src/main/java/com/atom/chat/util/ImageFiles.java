package com.atom.chat.util;

import com.atom.chat.AtomChat;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

/**
 * The one place that knows which files count as images and how big they are.
 * The picker, the clipboard paste and the file drop all agree here, so the
 * accepted extension list can never drift apart between entry points.
 */
public final class ImageFiles {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"};

    private ImageFiles() {
    }

    public static boolean isImage(Path file) {
        return file != null && Files.isRegularFile(file) && isImageName(file.getFileName().toString());
    }

    public static boolean isImageName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Intrinsic pixel size, read from the file header without decoding the whole
     * image. The uploader needs it so the CICode can carry the aspect ratio and
     * the message list can reserve the right height before the download lands.
     * Returns null for anything ImageIO cannot read (webp, for instance) —
     * callers must fall back to a placeholder size.
     */
    public static int[] dimensions(Path file) {
        if (file == null) {
            return null;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Could not read image dimensions of {}", file, e);
        }
        return null;
    }
}
