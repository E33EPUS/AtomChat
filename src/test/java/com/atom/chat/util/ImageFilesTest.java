package com.atom.chat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFilesTest {
    @Test
    void isImageUrlRejectsOrdinaryWebPages() {
        assertFalse(ImageFiles.isImageUrl("https://github.com/E33EPUS/AtomChat"));
        assertFalse(ImageFiles.isImageUrl("https://example.com/docs"));
        assertFalse(ImageFiles.isImageUrl("https://example.com/path?page=1"));
    }

    @Test
    void isImageUrlAcceptsDirectImageFiles() {
        assertTrue(ImageFiles.isImageUrl("https://example.com/a.png"));
        assertTrue(ImageFiles.isImageUrl("https://example.com/a.JPG"));
        assertTrue(ImageFiles.isImageUrl("https://example.com/a.webp?raw=1"));
        assertTrue(ImageFiles.isImageUrl("https://example.com/a.gif#fragment"));
        assertTrue(ImageFiles.isImageUrl("http://i.imgur.com/abc.png"));
    }
}
