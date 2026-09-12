package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MessageCaptureTest {
    @Test
    void consumeReturnsTheFreshMetaAndClearsIt() {
        SenderMeta meta = new SenderMeta(null, "Steve", "Steve", "hi", false);
        MessageCapture.set(meta);
        assertSame(meta, MessageCapture.consume());
        assertNull(MessageCapture.consume());
    }

    @Test
    void setNullIsIgnored() {
        MessageCapture.set(null);
        assertNull(MessageCapture.consume());
    }

    @Test
    void eachThreadKeepsItsOwnHandoff() throws Exception {
        SenderMeta renderMeta = new SenderMeta(null, "Render", "Render", "r", false);
        MessageCapture.set(renderMeta);

        Thread other = new Thread(() -> {
            SenderMeta workerMeta = new SenderMeta(null, "Worker", "Worker", "w", false);
            MessageCapture.set(workerMeta);
            assertSame(workerMeta, MessageCapture.consume());
            assertNull(MessageCapture.consume());
        });
        other.start();
        other.join(2000);

        // The render thread's own meta must not have been consumed by the worker.
        assertSame(renderMeta, MessageCapture.consume());
        assertNull(MessageCapture.consume());
    }
}
