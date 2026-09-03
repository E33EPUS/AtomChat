package com.atom.chat.chat;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageTest {
    @Test
    void capturedContentThatLooksLikeAngleChatIsNotStripped() {
        ChatMessage msg = new ChatMessage(Text.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "<Alice> hi");
        assertEquals("<Alice> hi", msg.getContentText());
    }

    @Test
    void rawFallbackStillStripsVanillaSenderPrefix() {
        ChatMessage msg = new ChatMessage(Text.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", null);
        assertEquals("hi", msg.getContentText());
    }
}
