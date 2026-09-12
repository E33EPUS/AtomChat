package com.atom.chat.chat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRefTest {
    @Test
    void nilUuidNormalizesToNull() {
        PlayerRef ref = new PlayerRef(new UUID(0L, 0L), "Steve");
        assertNull(ref.uuid());
        assertEquals("n:steve", ref.key());
    }

    @Test
    void nameIsTheConversationKey() {
        UUID uuid = UUID.randomUUID();
        PlayerRef a = new PlayerRef(uuid, "Steve");
        PlayerRef b = new PlayerRef(null, "Steve");
        assertEquals(a.key(), b.key());
        assertEquals(a, b);
        assertEquals(a, new PlayerRef(uuid, "Steve"));
        assertNotEquals(a, new PlayerRef(uuid, "Steve2"));
    }

    @Test
    void nameKeyIsCaseInsensitive() {
        assertEquals(new PlayerRef(null, "Steve").key(), new PlayerRef(null, "steve").key());
    }
}
