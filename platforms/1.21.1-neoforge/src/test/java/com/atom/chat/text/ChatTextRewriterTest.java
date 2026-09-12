package com.atom.chat.text;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTextRewriterTest {
    @Test
    void rewriteReturnsNullForPlainText() {
        assertNull(ChatTextRewriter.rewrite(Component.literal("<Steve> hello")));
    }

    @Test
    void rewriteImageCodeReplacesRawCicode() {
        Component rewritten = ChatTextRewriter.rewrite(Component.literal(
                "<Steve> [[CICode,url=https://example.com/a.png,name=a.png]]"));
        assertNotNull(rewritten);
        assertFalse(rewritten.getString().contains("[[CICode"));
        assertTrue(rewritten.getString().startsWith("<Steve>"));
    }

    @Test
    void rewriteQuoteStripsQuotePrefix() {
        Component rewritten = ChatTextRewriter.rewrite(Component.literal(
                "<Steve> 「引用 @Bob: old message」hello"));
        assertNotNull(rewritten);
        assertFalse(rewritten.getString().contains("「引用"));
        assertTrue(rewritten.getString().startsWith("<Steve>"));
        assertTrue(rewritten.getString().endsWith("hello"));
    }

    @Test
    void rewriteDoesNotTouchMentionInMiddleOfSentence() {
        assertNull(ChatTextRewriter.rewrite(Component.literal("<Steve> I said 「引用 @Bob: hi」 just now")));
    }
}
