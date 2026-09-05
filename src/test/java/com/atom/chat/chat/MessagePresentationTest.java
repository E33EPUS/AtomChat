package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagePresentationTest {
    @Test
    void parsesAngleBracketChat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<Steve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().playerName());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void prefixedAngleBracketDisplayLabelKeepsPrefix() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[VIP]<Steve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void parsesColonChat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve: hello world", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().playerName());
        assertEquals("hello world", line.get().content());
    }

    @Test
    void parsesBracketDecoratedColonChat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[VIP]Steve: hello", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve", line.get().displayLabel());
        assertEquals("hello", line.get().content());
    }

    @Test
    void includesBracketSuffixDecorationInDisplayLabel() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[VIP]Steve[AFK] >> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve[AFK]", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void includesParenthesizedSuffixDecorationInDisplayLabel() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve(VIP) : hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve(VIP)", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void longestNameWinsOverSubstring() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve2: hi", List.of("Steve", "Steve2"));
        assertTrue(line.isPresent());
        assertEquals("Steve2", line.get().playerName());
    }

    @Test
    void broadcastSentenceHasWhitespaceGap() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve joined the game", List.of("Steve"));
        assertTrue(line.isPresent());
        assertTrue(MessagePresentation.isWhitespaceOnlyGap(
                "Steve joined the game", line.get().nameEnd(), line.get().contentStart()));
    }

    @Test
    void broadcastLabelPrefixIsRejected() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[系统]Steve: hi", List.of("Steve"));
        assertTrue(line.isEmpty());
    }

    @Test
    void unknownNameIsNotParsed() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine("Alice: hi", List.of("Steve")).isEmpty());
    }

    @Test
    void hyphenatedLongerNameDoesNotMatchShortCandidate() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "Steve-Master: hi", List.of("Steve")).isEmpty());
    }

    @Test
    void suffixInsideAngleBracketsDoesNotMatchShortCandidate() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "<Notch> hi", List.of("tch")).isEmpty());
    }

    @Test
    void bracketOnlyKaomojiIsContentNotLabel() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<Steve> (￣▽￣)", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("(￣▽￣)", line.get().content());
    }

    @Test
    void bracketOnlyKaomojiColonFormat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve: (≧▽≦)", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("(≧▽≦)", line.get().content());
    }

    @Test
    void fullwidthBracketOnlyKaomojiIsContent() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<Steve> 【滑稽】", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("【滑稽】", line.get().content());
    }

    // ---- G3: names split by § color pairs inside the text ----

    @Test
    void colorCodeSplitNameStillMatches() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("S§6t§beve: hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().playerName());
        assertEquals("hi", line.get().content());
    }

    @Test
    void colorCodeSplitNameOffsetsStayRaw() {
        String text = "[VIP] S§6t§beve§r: hi there";
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine(text, List.of("Steve"));
        assertTrue(line.isPresent());
        // Offsets must be raw-line coordinates so RichText slicing keeps working.
        assertEquals('S', text.charAt(line.get().nameStart()));
        assertEquals('e', text.charAt(line.get().nameEnd() - 1));
        assertEquals("hi there", text.substring(line.get().contentStart()));
        assertEquals("[VIP] S§6t§beve§r", line.get().displayLabel());
    }

    @Test
    void colorCodeSplitNameLongerCandidateWinsOverSuffix() {
        // "Ste§6ve2" must not be claimed by candidate "Steve" (next-char check).
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "Ste§6ve2: hi", List.of("Steve")).isEmpty());
    }

    @Test
    void colorCodeSplitNameInsideAngleBrackets() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<S§6t§beve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }
}
