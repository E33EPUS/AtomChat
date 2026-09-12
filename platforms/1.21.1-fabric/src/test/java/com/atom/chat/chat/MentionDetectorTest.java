package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionDetectorTest {
    private static final String ME = "Steve";

    @Test
    void atMentionAlwaysCounts() {
        assertTrue(MentionDetector.isMentioned("@Steve hi", ME, true, null));
        assertTrue(MentionDetector.isMentioned("hello @Steve!", ME, true, null));
    }

    @Test
    void bareNameCountsOnlyWithoutRequireAt() {
        assertTrue(MentionDetector.isMentioned("hey Steve look", ME, false, null));
        assertFalse(MentionDetector.isMentioned("hey Steve look", ME, true, null));
    }

    @Test
    void longerNameDoesNotMatchShortCandidate() {
        assertFalse(MentionDetector.isMentioned("Steve2 is here", ME, false, null));
        assertFalse(MentionDetector.isMentioned("@Steve2 hi", ME, true, null));
    }

    @Test
    void nameInsideWordDoesNotCount() {
        assertFalse(MentionDetector.isMentioned("xSteve", ME, false, null));
        assertFalse(MentionDetector.isMentioned("SteveX", ME, false, null));
    }

    @Test
    void replyToOwnMessageCounts() {
        assertTrue(MentionDetector.isMentioned("replying", ME, true, ME));
    }

    @Test
    void caseInsensitiveMatch() {
        assertTrue(MentionDetector.isMentioned("@STEVE hi", ME, true, null));
    }

    @Test
    void findMentionRangesCoversTheAtSign() {
        List<MentionDetector.MentionRange> ranges =
                MentionDetector.findMentionRanges("hi @Steve!", ME, true);
        assertEquals(1, ranges.size());
        assertEquals(3, ranges.get(0).start());
        assertEquals(9, ranges.get(0).end());
        assertEquals("@Steve", "hi @Steve!".substring(ranges.get(0).start(), ranges.get(0).end()));
    }

    @Test
    void nullInputsAreSafe() {
        assertFalse(MentionDetector.isMentioned(null, ME, true, null));
        assertFalse(MentionDetector.isMentioned("hi", null, true, null));
    }
}
