package com.atom.chat.net;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaIdsTest {

    private static byte[] head(int... bytes) {
        byte[] out = new byte[Math.max(bytes.length, 12)];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) bytes[i];
        }
        return out;
    }

    @Test
    void sniffsSupportedImageMagic() {
        assertEquals("png", MediaIds.extensionOf(head(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        assertEquals("jpg", MediaIds.extensionOf(head(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals("gif", MediaIds.extensionOf(head('G', 'I', 'F', '8', '9', 'a')));
        assertEquals("webp", MediaIds.extensionOf(
                head('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
        assertEquals("bmp", MediaIds.extensionOf(head('B', 'M')));
    }

    @Test
    void rejectsNonImagesAndShortInput() {
        assertNull(MediaIds.extensionOf(null));
        assertNull(MediaIds.extensionOf(new byte[4]));
        assertNull(MediaIds.extensionOf(head('P', 'K', 3, 4)));
        // RIFF but not WEBP (e.g. a WAV) is not an image.
        assertNull(MediaIds.extensionOf(head('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E')));
    }

    @Test
    void idIsContentAddressed() {
        byte[] content = head(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        String a = MediaIds.idFor(content, "png");
        String b = MediaIds.idFor(Arrays.copyOf(content, content.length), "png");
        assertEquals(a, b);
        assertEquals(64 + 4, a.length());
        assertTrue(MediaIds.isSafeId(a));
        assertEquals(a, MediaIds.idOf(MediaIds.urlFor(a)));
        assertNull(MediaIds.idOf("http://example.com/a.png"));
    }

    @Test
    void safeIdRejectsPathAndCaseTricks() {
        String valid = "a".repeat(64) + ".png";
        assertTrue(MediaIds.isSafeId(valid));
        assertFalse(MediaIds.isSafeId(null));
        assertFalse(MediaIds.isSafeId("../../etc/passwd"));
        assertFalse(MediaIds.isSafeId("a".repeat(63) + ".png"));
        assertFalse(MediaIds.isSafeId("A".repeat(64) + ".png"));
        assertFalse(MediaIds.isSafeId("a".repeat(64) + ".PNG"));
        assertFalse(MediaIds.isSafeId("a".repeat(64) + "."));
        assertFalse(MediaIds.isSafeId("a".repeat(64) + ".toolong"));
        assertFalse(MediaIds.isSafeId("a".repeat(64) + "/../x.png"));
    }

    @Test
    void chunkMath() {
        assertEquals(0, MediaIds.chunkCount(0));
        assertEquals(1, MediaIds.chunkCount(1));
        assertEquals(1, MediaIds.chunkCount(MediaIds.CHUNK_BYTES));
        assertEquals(2, MediaIds.chunkCount(MediaIds.CHUNK_BYTES + 1));
        assertEquals(3, MediaIds.chunkCount(MediaIds.CHUNK_BYTES * 2 + 5));
    }
}
