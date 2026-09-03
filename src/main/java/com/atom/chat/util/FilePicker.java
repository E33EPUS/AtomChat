package com.atom.chat.util;

import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Native image picker built on AWT's {@link FileDialog}.
 *
 * <p>What keeps the dialog above Minecraft's window: MC draws into a window that
 * covers the monitor but is an ordinary, <em>not</em> topmost HWND. A native
 * dialog is placed in its owner's z-band, so with a {@code null} owner (the
 * original implementation) the dialog landed at the bottom of the z-order and
 * the game painted straight over it. The fix is a real owner frame that is
 * topmost: the modal dialog inherits the owner's band and surfaces above the
 * game. That owner is an undecorated 1x1 frame parked off screen, so it never
 * becomes visible itself. It is created once and reused — AWT's toolkit cannot
 * be restarted and repeatedly showing/disposing a topmost window is what makes
 * it flash.
 *
 * <p>Two things this deliberately does <em>not</em> do:
 *
 * <ul>
 *   <li><b>No {@code setOpacity(0)}.</b> AWT implements window opacity with
 *       {@code WS_EX_LAYERED}, and that style is inherited by the modal dialog
 *       created on top of it — which turns the picker invisible rather than
 *       merely mis-layered. The frame is parked off screen instead, which is
 *       invisible without touching the window style.</li>
 *   <li><b>No fighting GLFW.</b> GLFW pins a fullscreen window to
 *       {@code HWND_TOPMOST}, so no amount of AWT top-most work is guaranteed to
 *       win there. {@code beforeShow}/{@code afterShow} exist so the caller can
 *       get the game window out of the way as a fallback.</li>
 * </ul>
 *
 * <p>Runs on the EDT from a worker thread so the render thread never blocks.
 */
public final class FilePicker {
    private static Frame owner;

    private FilePicker() {
    }

    /**
     * @param beforeShow runs on the EDT immediately before the dialog goes modal
     * @param afterShow  runs on the EDT as soon as the dialog is disposed
     */
    public static Path pickImage(Runnable beforeShow, Runnable afterShow) {
        AtomicReference<Path> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                Frame parent = ownerFrame();
                FileDialog fd = new FileDialog(parent, "AtomChat - 选择图片");
                fd.setModal(true);
                try {
                    fd.setAlwaysOnTop(true);
                } catch (Exception ignored) {
                    // Not honoured on every platform; the topmost owner is the
                    // part that actually lifts the dialog above the game.
                }
                fd.setFilenameFilter((dir, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".png") || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                            || lower.endsWith(".webp") || lower.endsWith(".bmp");
                });
                try {
                    if (beforeShow != null) {
                        beforeShow.run();
                    }
                    fd.setVisible(true);
                    File[] files = fd.getFiles();
                    if (files != null && files.length > 0 && files[0] != null) {
                        result.set(files[0].toPath());
                    }
                } finally {
                    fd.dispose();
                    if (afterShow != null) {
                        afterShow.run();
                    }
                }
            });
        } catch (Exception e) {
            return null;
        }
        return result.get();
    }

    /**
     * Invisible topmost owner used only as the dialog's z-order anchor.
     * {@code setVisible(true)} is what allocates the native HWND, and
     * {@code setAlwaysOnTop} only takes effect on a displayable window.
     */
    private static synchronized Frame ownerFrame() {
        if (owner != null && owner.isDisplayable()) {
            return owner;
        }
        Frame frame = new Frame();
        frame.setUndecorated(true);
        // Parked outside the screen instead of made transparent: transparency
        // would mark the window WS_EX_LAYERED and that style is inherited by
        // the modal dialog, making the picker itself invisible.
        frame.setBounds(-64, -64, 1, 1);
        // Never steal focus from the game; it exists purely as an owner.
        frame.setFocusableWindowState(false);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        owner = frame;
        return owner;
    }
}
