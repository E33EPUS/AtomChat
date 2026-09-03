package com.atom.chat.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thumbnail pane shown beside the file list in the image picker.
 *
 * <p>The native Windows dialog previews the highlighted image; Swing's chooser
 * has no such pane built in, but {@link JFileChooser#setAccessory} reserves a
 * slot for exactly this, and a property listener on
 * {@link JFileChooser#SELECTED_FILE_CHANGED_PROPERTY} fires on every highlight
 * change.
 *
 * <p>Decoding happens off the EDT with source subsampling: a 20-megapixel photo
 * is read as every Nth pixel, so a thumbnail costs milliseconds instead of
 * stalling the whole picker for seconds. Only the newest request may touch the
 * label, so arrowing quickly through a folder can never show a stale preview.
 */
public final class ImagePreview {
    private static final String PLACEHOLDER = "无预览";
    private static final String LOADING = "加载中…";
    private static final AtomicLong REQUEST = new AtomicLong();

    private ImagePreview() {
    }

    /** Builds the pane and wires it to the chooser's selection changes. */
    public static JComponent attachTo(JFileChooser chooser, int width, int height) {
        JLabel thumbnail = new JLabel(PLACEHOLDER, SwingConstants.CENTER);
        thumbnail.setPreferredSize(new Dimension(width, height));
        thumbnail.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnail.setVerticalAlignment(SwingConstants.CENTER);
        thumbnail.setForeground(new Color(0xFF8A8F98));
        thumbnail.setOpaque(true);
        thumbnail.setBackground(new Color(0xFFF2F3F5));

        chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, event -> {
            if (event.getNewValue() instanceof File file) {
                show(thumbnail, file, width, height);
            }
        });

        JPanel pane = new JPanel(new BorderLayout());
        pane.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        pane.add(thumbnail, BorderLayout.CENTER);
        return pane;
    }

    private static void show(JLabel target, File file, int width, int height) {
        // Bumping the token is what cancels whatever decode is in flight.
        long token = REQUEST.incrementAndGet();
        if (file == null || !file.isFile() || !ImageFiles.isImageName(file.getName())) {
            placeholder(target, PLACEHOLDER);
            return;
        }
        placeholder(target, LOADING);
        Thread loader = new Thread(() -> {
            BufferedImage image = decode(file, width, height);
            SwingUtilities.invokeLater(() -> {
                if (REQUEST.get() != token) {
                    return; // the user has already moved on to another file
                }
                if (image == null) {
                    placeholder(target, PLACEHOLDER);
                } else {
                    target.setText(null);
                    target.setIcon(new ImageIcon(image));
                }
            });
        }, "AtomChat-Preview");
        loader.setDaemon(true);
        loader.start();
    }

    private static void placeholder(JLabel target, String text) {
        target.setText(text);
        target.setIcon(null);
    }

    /**
     * Decodes at a fraction of the source resolution —
     * {@link ImageReadParam#setSourceSubsampling} makes the decoder skip pixels
     * instead of reading them — and scales the remainder down to the pane.
     * Returns null for anything ImageIO cannot read (webp, for instance).
     */
    private static BufferedImage decode(File file, int maxWidth, int maxHeight) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                int step = Math.max(1, Math.min(
                        sourceWidth / Math.max(1, maxWidth),
                        sourceHeight / Math.max(1, maxHeight)));
                ImageReadParam params = reader.getDefaultReadParam();
                params.setSourceSubsampling(step, step, 0, 0);
                return fit(reader.read(0, params), maxWidth, maxHeight);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Never upscales: a small picture is shown at its own size, not blurred. */
    private static BufferedImage fit(BufferedImage source, int maxWidth, int maxHeight) {
        if (source == null) {
            return null;
        }
        double scale = Math.min(1.0D, Math.min(
                maxWidth / (double) source.getWidth(),
                maxHeight / (double) source.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage fitted = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = fitted.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return fitted;
    }
}
