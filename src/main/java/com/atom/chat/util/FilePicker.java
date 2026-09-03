package com.atom.chat.util;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Image picker hosted in a plain Swing {@link JFrame}.
 *
 * <p>This deliberately is not the native file dialog any more. Four rounds of
 * owner games proved the native one unusable here: an AWT {@code FileDialog} is
 * created by {@code GetOpenFileName}, which ignores AWT's always-on-top state
 * and inherits the z-band of its owner — and in practice that owner's band was
 * the bottom of the z-order, so the dialog surfaced below <em>every</em> window
 * including the desktop and had no taskbar entry to rescue it with.
 *
 * <p>A {@code JFrame} sidesteps that whole class of problems: it is a top-level
 * window we own outright, so it lands in the taskbar, can be activated, and
 * honours {@code setAlwaysOnTop} once it is visible. Being visible and
 * focusable is the part the old hidden owner frame could never offer.
 *
 * <p>Runs on the EDT; the calling worker thread waits on a latch, so the render
 * thread keeps running and the game does not freeze.
 */
public final class FilePicker {
    private FilePicker() {
    }

    /**
     * @param beforeShow runs on the EDT immediately before the chooser is shown
     * @param afterShow  runs on the EDT once the chooser is disposed
     */
    public static Path pickImage(Runnable beforeShow, Runnable afterShow) {
        AtomicReference<Path> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                if (beforeShow != null) {
                    beforeShow.run();
                }
                result.set(showChooser());
            } catch (Throwable t) {
                com.atom.chat.AtomChat.LOGGER.warn("Image picker failed", t);
            } finally {
                try {
                    if (afterShow != null) {
                        afterShow.run();
                    }
                } finally {
                    done.countDown();
                }
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }

    private static Path showChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("AtomChat - 选择图片");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || ImageFiles.isImageName(file.getName());
            }

            @Override
            public String getDescription() {
                return "图片 (png, jpg, jpeg, gif, webp, bmp)";
            }
        });

        JFrame frame = new JFrame("AtomChat - 选择图片");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(chooser, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        // Always-on-top only sticks reliably on a window that is already
        // visible and focusable, and the activation is what actually raises it
        // above Minecraft's topmost fullscreen window.
        frame.setAlwaysOnTop(true);
        frame.toFront();

        Path result = null;
        try {
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (file != null && file.isFile()) {
                    result = file.toPath();
                }
            }
        } finally {
            frame.dispose();
        }
        return result;
    }
}
