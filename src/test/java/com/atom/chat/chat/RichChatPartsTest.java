package com.atom.chat.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichChatPartsTest {
    @Test
    void slicesDecoratedLine() {
        Component line = Component.literal("[萌新]player>>谁能给我钻石？")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player ")));
        RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals("[萌新]player", parts.sender().getString());
        assertEquals("谁能给我钻石？", parts.content().getString());
    }

    @Test
    void slicePreservesSenderStylesFromDecoratedLine() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player "));
        Component line = Component.literal("[萌新]player>>谁能给我钻石？").setStyle(click);
        RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals(click, parts.sender().runs().get(0).style());
    }

    @Test
    void slicesWhenMetaCarriesDecoratedSenderName() {
        Component line = Component.literal("[萌新]player>>谁能给我钻石？");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "[萌新]player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals("[萌新]player", parts.sender().getString());
        assertEquals("谁能给我钻石？", parts.content().getString());
    }

    @Test
    void sliceKeepsBracketSuffixDecorationInSender() {
        Component line = Component.literal("[VIP]Steve[AFK] >> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve[AFK]", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceKeepsParenthesizedSuffixDecorationInSender() {
        Component line = Component.literal("Steve(VIP) : hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve(VIP)", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceContentLinkifiesBareUrls() {
        Component line = Component.literal("[萌新]player>>see https://example.com/x now");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "player", "player", "see https://example.com/x now", false))
                .orElseThrow();
        assertEquals("see https://example.com/x now", parts.content().getString());
        assertTrue(parts.content().runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL));
    }

    @Test
    void sliceAngleLineSenderDropsVanillaAngleBrackets() {
        Component line = Component.literal("<Steve> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void slicePrefixedAngleLineSenderKeepsPrefixOnly() {
        Component line = Component.literal("[VIP]<Steve> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceTeamDecoratedAngleLineStripsBracketsKeepsStyles() {
        // "<[称号]E33EPUS> 453": the wrapping pair is dropped, the inner team
        // colour survives the slice (e33chat cleanNameArea parity).
        Style orange = Style.EMPTY.withColor(0xFFA500);
        Component line = Component.literal("<")
                .append(Component.literal("[称号]").setStyle(orange))
                .append(Component.literal("E33EPUS"))
                .append(Component.literal("> 453"));
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "E33EPUS", "E33EPUS", "453", false))
                .orElseThrow();
        assertEquals("[称号]E33EPUS", parts.sender().getString());
        assertEquals("453", parts.content().getString());
        assertTrue(parts.sender().runs().stream().anyMatch(r -> orange.equals(r.style())));
    }

    @Test
    void slicesLegacyFormattedAngleLineUsingVisibleOffsets() {
        Component line = Component.literal("§a<Steve> §bhi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void slicesLegacyDecoratedPrefixLineUsingVisibleOffsets() {
        Component line = Component.literal("§7[VIP]§rSteve>>§ahi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void emptyWhenMetaHasNoUsableName() {
        Component line = Component.literal("[系统]公告: 欢迎");
        assertTrue(ChatPipeline.sliceRichText(line, new SenderMeta(null, null, null, null, true)).isEmpty());
    }
}
