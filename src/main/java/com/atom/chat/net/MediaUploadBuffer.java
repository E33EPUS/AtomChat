package com.atom.chat.net;

/**
 * Accumulates the chunks of one in-flight upload and enforces the size the
 * client declared in its {@code UploadStart} (0.2.7). Pure logic so the chunk
 * ordering/overflow rules are unit-testable without a server.
 */
public final class MediaUploadBuffer {
    private final int expected;
    private final byte[] data;
    private int written;
    private boolean failed;

    public MediaUploadBuffer(int expectedBytes, int maxBytes) {
        if (expectedBytes <= 0 || expectedBytes > maxBytes) {
            this.expected = 0;
            this.data = new byte[0];
            this.failed = true;
            return;
        }
        this.expected = expectedBytes;
        this.data = new byte[expectedBytes];
    }

    /** @return true when the chunk was accepted; false poisons the buffer. */
    public boolean write(int offset, byte[] chunk) {
        if (failed || chunk == null || chunk.length == 0) {
            failed = true;
            return false;
        }
        if (offset != written || offset < 0 || (long) offset + chunk.length > data.length) {
            failed = true;
            return false;
        }
        System.arraycopy(chunk, 0, data, offset, chunk.length);
        written += chunk.length;
        return true;
    }

    public boolean isComplete() {
        return !failed && written == expected && expected > 0;
    }

    public boolean isFailed() {
        return failed;
    }

    public int size() {
        return written;
    }

    /** The assembled bytes when complete, otherwise null. */
    public byte[] bytes() {
        return isComplete() ? data : null;
    }
}
