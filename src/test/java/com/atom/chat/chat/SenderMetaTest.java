package com.atom.chat.chat;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SenderMetaTest {
    @Test
    void nilUuidIsNormalizedToNull() {
        SenderMeta meta = new SenderMeta(new UUID(0L, 0L), "Steve", "Steve", "hi", false);
        assertNull(meta.senderUuid());
    }

    @Test
    void realUuidIsKept() {
        UUID uuid = UUID.randomUUID();
        SenderMeta meta = new SenderMeta(uuid, "Steve", "Steve", "hi", false);
        assertEquals(uuid, meta.senderUuid());
    }

    @Test
    void carriesStyledComponents() {
        Component sender = Component.literal("Alice").setStyle(Style.EMPTY.withUnderlined(true));
        SenderMeta meta = new SenderMeta(null, "Alice", "Alice", "hi", false, sender, Component.literal("hi"));
        assertEquals(sender, meta.senderComponent());
    }

    @Test
    void legacyConstructorLeavesComponentsNull() {
        SenderMeta meta = new SenderMeta(null, "Alice", "Alice", "hi", false);
        assertNull(meta.senderComponent());
        assertNull(meta.contentComponent());
    }
}
