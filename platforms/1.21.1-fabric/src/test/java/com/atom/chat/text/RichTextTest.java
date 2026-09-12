package com.atom.chat.text;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextTest {
    @Test
    void flattenPreservesRunStyles() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/x"));
        Text text = Text.literal("a").setStyle(click).append(Text.literal("b"));
        RichText rich = RichText.of(text);
        assertEquals("ab", rich.getString());
        assertEquals(1, rich.runs().size());
        assertEquals(click, rich.runs().get(0).style());
    }

    @Test
    void sliceKeepsStyles() {
        Text text = Text.literal("abc").setStyle(Style.EMPTY.withColor(0xFF0000))
                .append(Text.literal("def").setStyle(Style.EMPTY.withUnderline(true)));
        RichText sliced = RichText.of(text).slice(2, 5);
        assertEquals("cde", sliced.getString());
        assertEquals(0xFF0000, sliced.runs().get(0).style().getColor().getRgb());
    }

    @Test
    void linkifyBareUrls() {
        RichText rich = RichText.literal("see https://example.com/x now");
        RichText linked = rich.linkifyUrls();
        assertTrue(linked.runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL));
    }

    @Test
    void sliceNeverSplitsSurrogatePairs() {
        RichText rich = RichText.literal("a\uD83D\uDE00b");
        assertEquals("a\uD83D\uDE00b", rich.getString());
        assertEquals("\uD83D\uDE00", rich.slice(1, 3).getString());
        assertEquals("\uD83D\uDE00", rich.slice(1, 2).getString());
        assertEquals("\uD83D\uDE00", rich.slice(2, 3).getString());
    }

    @Test
    void linkifyPreservesFullTextAndMultipleUrls() {
        RichText linked = RichText.literal("go https://a.test and https://b.test end").linkifyUrls();
        assertEquals("go https://a.test and https://b.test end", linked.getString());
        assertEquals(5, linked.runs().size());
        assertEquals(2, linked.runs().stream()
                .filter(r -> r.style().getClickEvent() != null
                        && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL)
                .count());
    }

    @Test
    void linkifyLeavesExistingClickRunsUntouched() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://already.example"));
        RichText rich = RichText.of(Text.literal("https://example.com").setStyle(click));
        RichText linked = rich.linkifyUrls();
        assertEquals(1, linked.runs().size());
        assertEquals(click, linked.runs().get(0).style());
    }

    @Test
    void linkifyPreservesOriginalRunStyle() {
        Style colored = Style.EMPTY.withColor(0x00FF00);
        RichText rich = RichText.of(Text.literal("see https://example.com/x").setStyle(colored));
        RichText linked = rich.linkifyUrls();
        RichText.RichRun link = linked.runs().stream()
                .filter(r -> r.style().getClickEvent() != null)
                .findFirst().orElseThrow();
        assertEquals(0x00FF00, link.style().getColor().getRgb());
        assertEquals(colored, link.style().withClickEvent(null));
    }

    @Test
    void ofParsesLegacyFormattingCodesAndStripsControlPairs() {
        RichText rich = RichText.of(Text.literal("§aHello §rWorld"));
        assertEquals("Hello World", rich.getString());
        assertEquals(2, rich.runs().size());
        assertEquals("Hello ", rich.runs().get(0).text());
        assertEquals(0x55FF55, rich.runs().get(0).style().getColor().getRgb());
        assertEquals("World", rich.runs().get(1).text());
        assertEquals(null, rich.runs().get(1).style().getColor());
    }

    @Test
    void ofGroupsContiguousCharactersByEffectiveStyle() {
        RichText rich = RichText.of(Text.literal("§aA§cB"));
        assertEquals("AB", rich.getString());
        assertEquals(2, rich.runs().size());
        assertEquals(0x55FF55, rich.runs().get(0).style().getColor().getRgb());
        assertEquals(0xFF5555, rich.runs().get(1).style().getColor().getRgb());
    }

    @Test
    void emptyAndEmptySlicesReportEmpty() {
        assertTrue(RichText.empty().isEmpty());
        assertTrue(RichText.literal("").isEmpty());
        assertTrue(RichText.literal("abc").slice(2, 2).isEmpty());
    }

    @Test
    void slicePreservesRootStyle() {
        Style root = Style.EMPTY.withColor(0x123456);
        RichText emptyText = RichText.of(Text.literal("").setStyle(root));
        assertEquals(root, emptyText.slice(0, 0).rootStyle());
    }
}
