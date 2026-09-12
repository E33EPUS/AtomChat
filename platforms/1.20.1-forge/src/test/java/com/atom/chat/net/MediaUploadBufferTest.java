package com.atom.chat.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaUploadBufferTest {

    @Test
    void assemblesOrderedChunks() {
        MediaUploadBuffer buffer = new MediaUploadBuffer(6, 1024);
        assertTrue(buffer.write(0, new byte[]{1, 2, 3}));
        assertTrue(buffer.write(3, new byte[]{4, 5, 6}));
        assertTrue(buffer.isComplete());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, buffer.bytes());
    }

    @Test
    void rejectsOutOfOrderChunk() {
        MediaUploadBuffer buffer = new MediaUploadBuffer(4, 1024);
        assertTrue(buffer.write(0, new byte[]{1, 2}));
        assertFalse(buffer.write(4, new byte[]{3, 4}));
        assertTrue(buffer.isFailed());
        assertNull(buffer.bytes());
    }

    @Test
    void rejectsOverflowPastDeclaredSize() {
        MediaUploadBuffer buffer = new MediaUploadBuffer(3, 1024);
        assertFalse(buffer.write(0, new byte[]{1, 2, 3, 4}));
        assertTrue(buffer.isFailed());
    }

    @Test
    void rejectsInvalidDeclaration() {
        assertTrue(new MediaUploadBuffer(0, 1024).isFailed());
        assertTrue(new MediaUploadBuffer(-5, 1024).isFailed());
        assertTrue(new MediaUploadBuffer(2048, 1024).isFailed());
        assertFalse(new MediaUploadBuffer(1024, 1024).isFailed());
    }

    @Test
    void incompleteIsNotComplete() {
        MediaUploadBuffer buffer = new MediaUploadBuffer(4, 1024);
        assertTrue(buffer.write(0, new byte[]{1, 2}));
        assertFalse(buffer.isComplete());
        assertNull(buffer.bytes());
        assertEqualsWritten(buffer, 2);
    }

    private static void assertEqualsWritten(MediaUploadBuffer buffer, int expected) {
        assertTrue(buffer.size() == expected);
    }
}
