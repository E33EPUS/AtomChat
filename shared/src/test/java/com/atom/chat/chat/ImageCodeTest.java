package com.atom.chat.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The grammar the panel, the vanilla-HUD rewrite and the media scheme all share.
 *
 * <p>Several cases here are regressions from a real report: a code the panel
 * could not read fell through to a TEXT bubble, printed its raw protocol text
 * and handed the linkifier a bogus URL.
 */
class ImageCodeTest {

    private static final String URL = "https://h.uguu.se/abc.png";

    @Test
    void parsesTheCanonicalForm() {
        ImageCode.Meta meta = ImageCode.parse("[[CICode,url=" + URL + ",name=a.png,w=128,h=64]]");
        assertNotNull(meta);
        assertEquals(URL, meta.url());
        assertEquals("a.png", meta.name());
        assertEquals(128, meta.width());
        assertEquals(64, meta.height());
    }

    @Test
    void acceptsALowerCaseTag() {
        assertNotNull(ImageCode.parse("[[cicode,url=" + URL + ",name=a.png]]"));
    }

    @Test
    void acceptsParametersInAnyOrder() {
        ImageCode.Meta meta = ImageCode.parse("[[CICode,name=a.png,url=" + URL + ",h=64,w=128]]");
        assertNotNull(meta);
        assertEquals(URL, meta.url());
        assertEquals("a.png", meta.name());
        assertEquals(128, meta.width());
        assertEquals(64, meta.height());
    }

    @Test
    void trimsValues() {
        // "url= https://…" used to reach the image loader with the space intact.
        ImageCode.Meta meta = ImageCode.parse("[[CICode,url= " + URL + " ,name= a.png ]]");
        assertNotNull(meta);
        assertEquals(URL, meta.url());
        assertEquals("a.png", meta.name());
    }

    @Test
    void toleratesWhitespaceAroundTheTag() {
        assertNotNull(ImageCode.parse("[[ CICode , url=" + URL + "]]"));
    }

    @Test
    void acceptsTheSingleBracketForm() {
        assertEquals(URL, ImageCode.parse("[CICode,url=" + URL + ",name=a.png]").url());
    }

    @Test
    void aCodeWithoutAUrlIsNotAnImageButIsStillACode() {
        assertNull(ImageCode.parse("[[CICode,name=a.png]]"));
        assertTrue(ImageCode.contains("[[CICode,name=a.png]]"),
                "a code we cannot render still must not be printed as raw text");
    }

    @Test
    void ignoresTextWithoutACode() {
        assertNull(ImageCode.parse("hello"));
        assertNull(ImageCode.parse(null));
        assertFalse(ImageCode.contains("hello"));
        assertTrue(ImageCode.ranges("hello").isEmpty());
    }

    @Test
    void rangesCoverEveryCode() {
        String text = "a [[CICode,url=" + URL + ",name=a]] b [CICode,url=" + URL + ",name=b] c";
        List<int[]> ranges = ImageCode.ranges(text);
        assertEquals(2, ranges.size());
        assertTrue(ImageCode.covers(ranges, text.indexOf("[[CICode")));
        assertTrue(ImageCode.covers(ranges, text.indexOf("[CICode,url", text.indexOf(" b "))));
        assertFalse(ImageCode.covers(ranges, 0));
        assertFalse(ImageCode.covers(ranges, text.length() - 1));
    }

    @Test
    void malformedSizeFallsBackToZero() {
        ImageCode.Meta meta = ImageCode.parse("[[CICode,url=" + URL + ",name=a,w=abc,h=]]");
        assertNotNull(meta);
        assertEquals(0, meta.width());
        assertEquals(0, meta.height());
    }
}
