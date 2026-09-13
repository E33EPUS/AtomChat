package com.atom.chat.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * CICode parsing shared by the screen and the message list view: URL
 * extraction (both bracket styles, missing delimiters) and metadata parsing
 * (intrinsic size present, absent, malformed).
 */
class CicodesTest {

    @Test
    void extractsUrlFromDoubleBracketCode() {
        String text = "look [[CICode,url=https://example.com/a.png,name=pic,w=100,h=50]] nice";
        assertEquals("https://example.com/a.png", Cicodes.extractImageUrl(text));
    }

    @Test
    void extractsUrlFromSingleBracketCode() {
        assertEquals("https://example.com/a.png",
                Cicodes.extractImageUrl("[CICode,url=https://example.com/a.png,name=pic]"));
    }

    @Test
    void returnsNullWhenNoCodeOrNoUrl() {
        assertNull(Cicodes.extractImageUrl("plain message"));
        assertNull(Cicodes.extractImageUrl("[[CICode,url=]]"));
    }

    @Test
    void parsesMetaWithIntrinsicSize() {
        Cicodes.ImageMeta meta = Cicodes.parseImageMeta(
                "[[CICode,url=https://example.com/a.png,name=pic,w=320,h=240]]");
        assertEquals("https://example.com/a.png", meta.url());
        assertEquals("pic", meta.name());
        assertEquals(320, meta.width());
        assertEquals(240, meta.height());
    }

    @Test
    void parsesMetaWithoutSizeAsZero() {
        Cicodes.ImageMeta meta = Cicodes.parseImageMeta("[[CICode,url=https://example.com/a.png,name=pic]]");
        assertEquals(0, meta.width());
        assertEquals(0, meta.height());
    }

    @Test
    void malformedSizeStillCountsAsAnImage() {
        // The old grammar required ",w=digits,h=digits" to match at all, so a
        // non-numeric size made the whole code unmatchable — the message fell
        // through to a TEXT bubble and printed its raw protocol text, and the
        // linkifier turned that text's tail into a clickable URL. A code with a
        // usable url is an image; only its size is unknown, so the bubble falls
        // back to the placeholder box.
        Cicodes.ImageMeta meta = Cicodes.parseImageMeta(
                "[[CICode,url=https://example.com/a.png,name=pic,w=abc,h=def]]");
        assertEquals("https://example.com/a.png", meta.url());
        assertEquals(0, meta.width());
        assertEquals(0, meta.height());
        assertNull(Cicodes.parseImageMeta("plain message"));
        assertNull(Cicodes.parseImageMeta(null));
    }
}
