package com.atom.chat.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chunk reassembly: holes and lies must never reach the disk. */
class PackAssemblerTest {
    private static byte[] range(int from, int length) {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = (byte) (from + i);
        }
        return out;
    }

    private static ServerPack.FileEntry entry(byte[] data) {
        return new ServerPack.FileEntry("a.png", ServerPack.sha256Hex(data), data.length);
    }

    private static PackAssembler.FileBuffer buffer(byte[] data) {
        return new PackAssembler.FileBuffer(entry(data));
    }

    @Test
    void chunksInOrderVerify() {
        byte[] data = range(0, 10);
        PackAssembler.FileBuffer buffer = buffer(data);

        assertTrue(buffer.offer(0, range(0, 4)));
        assertTrue(buffer.offer(4, range(4, 6)));

        assertTrue(buffer.isComplete());
        assertEquals(10, buffer.received());
        assertArrayEquals(data, buffer.verified());
    }

    @Test
    void aHoleIsRefused() {
        PackAssembler.FileBuffer buffer = buffer(range(0, 10));

        assertFalse(buffer.offer(2, range(2, 2)), "a chunk that leaves a hole must be rejected");
        assertEquals(0, buffer.received());
    }

    @Test
    void chunksOutsideTheDeclaredSizeAreRefused() {
        PackAssembler.FileBuffer buffer = buffer(range(0, 10));

        assertFalse(buffer.offer(-1, range(0, 1)));
        assertFalse(buffer.offer(9, range(9, 2)), "one byte past the end is still past the end");
        assertFalse(buffer.offer(0, null));
        assertFalse(buffer.offer(10, new byte[0]));
        assertEquals(0, buffer.received());
    }

    @Test
    void aChunkThatDoesNotMatchTheHashNeverVerifies() {
        byte[] data = range(0, 8);
        PackAssembler.FileBuffer buffer = buffer(data);
        byte[] tampered = range(0, 8);
        tampered[3] = (byte) 0xFF;

        assertTrue(buffer.offer(0, tampered));
        assertTrue(buffer.isComplete());

        assertNull(buffer.verified(), "the manifest hash is the only thing that authorises a write");
    }

    @Test
    void duplicateChunksAreIgnoredInsteadOfRebelling() {
        PackAssembler.FileBuffer buffer = buffer(range(0, 8));

        assertTrue(buffer.offer(0, range(0, 4)));
        assertTrue(buffer.offer(0, range(0, 4)), "a re-sent chunk is harmless");

        assertEquals(4, buffer.received());
        assertFalse(buffer.isComplete());
    }

    @Test
    void anOverlappingTailFillsTheRestOfTheChunk() {
        byte[] data = range(0, 8);
        PackAssembler.FileBuffer buffer = buffer(data);

        assertTrue(buffer.offer(0, range(0, 4)));
        assertTrue(buffer.offer(2, range(2, 6)));

        assertTrue(buffer.isComplete());
        assertArrayEquals(data, buffer.verified());
    }

    @Test
    void anIncompleteFileYieldsNothing() {
        PackAssembler.FileBuffer buffer = buffer(range(0, 8));

        assertTrue(buffer.offer(0, range(0, 4)));

        assertFalse(buffer.isComplete());
        assertNull(buffer.verified());
        assertEquals(8, buffer.size());
    }
}
