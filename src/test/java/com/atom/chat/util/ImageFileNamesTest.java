package com.atom.chat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageFileNamesTest {
    /** The url shape that produced the bug report: hosted media has no path segment. */
    private static final String HOSTED_MEDIA =
            "atomchat-media:307a9ad949351e0b33c2eee588918a69952f9b6347a123a30184c49339e7c0ef.png";

    @Test
    void hostedMediaUrlLosesItsScheme() {
        String name = ImageFileNames.fromUrl(HOSTED_MEDIA);
        assertEquals("307a9ad949351e0b33c2eee588918a69952f9b6347a123a30184c49339e7c0ef.png", name);
        // A colon in a suggested name is not a legal Windows file name.
        assertEquals(-1, name.indexOf(':'));
    }

    @Test
    void httpUrlKeepsOnlyTheLastSegment() {
        assertEquals("a.png", ImageFileNames.fromUrl("https://example.com/dir/a.png"));
        assertEquals("a.png", ImageFileNames.fromUrl("https://example.com/dir/a.png?raw=1"));
        assertEquals("a.png", ImageFileNames.fromUrl("https://example.com/dir/a.png#fragment"));
        assertEquals("a.png", ImageFileNames.fromUrl("https://example.com/a.png?x=1#y"));
    }

    @Test
    void unusableUrlsFallBackToTheDefaultName() {
        assertEquals("image.png", ImageFileNames.fromUrl(null));
        assertEquals("image.png", ImageFileNames.fromUrl(""));
        assertEquals("image.png", ImageFileNames.fromUrl("   "));
        assertEquals("image.png", ImageFileNames.fromUrl("https://example.com/dir/"));
        assertEquals("image.png", ImageFileNames.fromUrl("https://example.com/"));
    }

    @Test
    void namesThatAFileSystemWouldRejectAreRepaired() {
        assertEquals("a_b.png", ImageFileNames.sanitize("a:b.png"));
        assertEquals("a_b.png", ImageFileNames.sanitize("a|b.png"));
        assertEquals("weird name.png", ImageFileNames.sanitize("weird name.png"));
        // sanitize repairs characters only; fromUrl is what yields a leaf name.
        assertEquals("C__pics_a.png", ImageFileNames.sanitize("C:\\pics\\a.png"));
        assertEquals("image.png", ImageFileNames.sanitize(".."));
        assertEquals("image.png", ImageFileNames.sanitize("   "));
        assertEquals("image.png", ImageFileNames.sanitize(null));
    }

    @Test
    void aWindowsStylePathStillYieldsTheLeafName() {
        assertEquals("a.png", ImageFileNames.fromUrl("C:\\Users\\me\\Pictures\\a.png"));
    }
}
