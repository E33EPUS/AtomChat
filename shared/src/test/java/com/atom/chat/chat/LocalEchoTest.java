package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalEchoTest {

    @Test
    void quotePrefixStaysOnTheWireAndOutOfTheBubbleContent() {
        String wire = "「引用 @Tester: xxx」yyy";
        ChatMessage message = LocalEcho.build(wire, "yyy", "Tester", "xxx",
                UUID.randomUUID(), "Tester", RichText.empty());

        assertEquals("Tester", message.getQuoteName());
        assertEquals("xxx", message.getQuoteText());
        assertEquals("yyy", message.getContentText());
        assertEquals("yyy", message.getDisplayText(), "bubble content must not include the quote prefix");
        assertEquals(wire, message.getRawText(), "the wire text must be kept for CICode/image extraction");
    }

    @Test
    void plainMessageKeepsItsBody() {
        ChatMessage message = LocalEcho.build("hello", "hello", null, null,
                UUID.randomUUID(), "Tester", RichText.empty());

        assertNull(message.getQuoteName());
        assertEquals("hello", message.getContentText());
        assertEquals("hello", message.getDisplayText());
    }
}
