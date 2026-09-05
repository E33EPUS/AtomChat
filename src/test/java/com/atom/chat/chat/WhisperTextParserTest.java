package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperTextParserTest {
    private static final String OWN = "Notch";

    private static WhisperTextParser.WhisperHit parse(String line) {
        return WhisperTextParser.tryParse(line, OWN);
    }

    // ---- arrow family ----

    @Test
    void essentialsIncomingWithMePlaceholder() {
        var hit = parse("[Steve -> me] hello there");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hello there", hit.content());
    }

    @Test
    void essentialsIncomingWithOwnName() {
        var hit = parse("[Steve -> Notch] hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    @Test
    void outgoingArrowFromOwnName() {
        var hit = parse("[Notch -> Steve] hey");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hey", hit.content());
    }

    @Test
    void deluxeColonArrowIncoming() {
        var hit = parse("Steve -> me : hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hi", hit.content());
    }

    @Test
    void decoratedSideIsStripped() {
        var hit = parse("[§6Steve§r -> me] hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    // ---- CMI family ----

    @Test
    void cmiFromIsIncoming() {
        var hit = parse("[/msg from [Steve]] hello");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hello", hit.content());
    }

    @Test
    void cmiSentIsOutgoing() {
        var hit = parse("[/msg sent -> [Steve]] hello");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    // ---- keyword family ----

    @Test
    void chineseKeywordIncoming() {
        var hit = parse("Steve 悄悄地对你说: 你好");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("你好", hit.content());
    }

    @Test
    void englishKeywordIncoming() {
        var hit = parse("Steve whispers to you: hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    @Test
    void chineseKeywordOutgoingBeatsIncomingKeyword() {
        // The outgoing shape must not be mis-claimed as incoming from "你对X".
        var hit = parse("你对Steve悄悄地说：在吗");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("在吗", hit.content());
    }

    @Test
    void englishKeywordOutgoing() {
        var hit = parse("You whisper to Steve: brb");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    // ---- rejection guards ----

    @Test
    void thirdPartyArrowIsIgnored() {
        assertNull(parse("[Alice -> Bob] secret channel talk"));
    }

    @Test
    void normalChatIsNotClaimed() {
        assertNull(parse("Steve: hello world"));
        assertNull(parse("<Steve> hello world"));
    }

    @Test
    void broadcastIsNotClaimed() {
        assertNull(parse("Steve joined the game"));
        assertNull(parse("[服务器] 维护公告"));
    }

    @Test
    void nullAndOversizedLinesAreIgnored() {
        assertNull(WhisperTextParser.tryParse(null, OWN));
        assertNull(WhisperTextParser.tryParse("x".repeat(300), OWN));
        assertFalse(WhisperTextParser.tryParse("[Steve -> me] hi", null) == null);
    }
}
