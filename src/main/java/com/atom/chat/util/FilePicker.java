package com.atom.chat.util;

import com.atom.chat.AtomChat;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Image picker: a {@link JFileChooser} in a plain {@link JFrame}, skinned with
 * FlatLaf.
 *
 * <p>It took a while to get here, so the constraint is worth writing down: any
 * window <em>we</em> create can be made topmost and will then float above
 * Minecraft's topmost fullscreen window — that is proven. What cannot be done
 * is lifting a native {@code GetOpenFileName} dialog, which inherits neither
 * {@code WS_EX_TOPMOST} nor any hint we set, and sits at the bottom of the
 * z-order below the desktop. So the dialog is ours, and the look comes from
 * FlatLaf instead of Windows.
 *
 * <p>The look and feel is process-global: it has to be installed before any
 * Swing component exists, and it also affects the rest of the JVM. Minecraft
 * barely uses Swing, so that costs nothing in practice.
 *
 * <p>Runs on the EDT; the calling worker thread waits on a latch, so the render
 * thread keeps running and the game does not freeze.
 */
public final class FilePicker {
    private static boolean lookAndFeelInstalled;

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
                AtomChat.LOGGER.warn("Image picker failed", t);
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
        installLookAndFeel();

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
        chooser.setCurrentDirectory(defaultDirectory());
        // The native dialog's preview pane, rebuilt as a chooser accessory.
        chooser.setAccessory(ImagePreview.attachTo(chooser, 220, 280));

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

    /**
     * FlatLaf is what makes the chooser look modern; without it Swing falls
     * back to Metal, the grey Java look. The system look and feel is the
     * fallback so a FlatLaf failure degrades rather than breaks.
     */
    private static void installLookAndFeel() {
        if (lookAndFeelInstalled) {
            return;
        }
        lookAndFeelInstalled = true;
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            AtomChat.LOGGER.info("Image picker look and feel: FlatLaf");
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("FlatLaf unavailable, falling back to the system look and feel", t);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable t2) {
                AtomChat.LOGGER.warn("System look and feel unavailable too, keeping the Swing default", t2);
            }
        }
    }

    /** Starts in the folder people actually keep pictures in. */
    private static File defaultDirectory() {
        String home = System.getProperty("user.home");
        File pictures = new File(home, "Pictures");
        if (pictures.isDirectory()) {
            return pictures;
        }
        // Chinese Windows names the shell folder 图片 instead.
        File localized = new File(home, "图片");
        if (localized.isDirectory()) {
            return localized;
        }
        return new File(home);
    }
}
