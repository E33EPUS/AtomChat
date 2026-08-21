package com.atom.chat.util;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple file picker for image upload. Uses Swing on the EDT so it is safe to
 * call from a background worker thread.
 */
public final class FilePicker {
    private FilePicker() {
    }

    public static Path pickImage() {
        AtomicReference<Path> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("AtomChat - 选择图片");
                chooser.setFileFilter(new FileNameExtensionFilter("图片文件", "png", "jpg", "jpeg", "gif", "webp", "bmp"));
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    result.set(chooser.getSelectedFile().toPath());
                }
            });
        } catch (Exception e) {
            return null;
        }
        return result.get();
    }
}
