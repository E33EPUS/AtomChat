package com.atom.chat.chat;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichChatPartsTest {
    @Test
    void slicesDecoratedLine() {
        Text line = Text.literal("[萌新]player>>谁能给我钻石？")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player ")));
        RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals("[萌新]player", parts.sender().getString());
        assertEquals("谁能给我钻石？", parts.content().getString());
    }

    @Test
    void slicePreservesSenderStylesFromDecoratedLine() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player "));
        Text line = Text.literal("[萌新]player>>谁能给我钻石？").setStyle(click);
        RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals(click, parts.sender().runs().get(0).style());
    }

    @Test
    void slicesWhenMetaCarriesDecoratedSenderName() {
        Text line = Text.literal("[萌新]player>>谁能给我钻石？");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "[萌新]player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals("[萌新]player", parts.sender().getString());
        assertEquals("谁能给我钻石？", parts.content().getString());
    }

    @Test
    void sliceKeepsBracketSuffixDecorationInSender() {
        Text line = Text.literal("[VIP]Steve[AFK] >> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve[AFK]", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceKeepsParenthesizedSuffixDecorationInSender() {
        Text line = Text.literal("Steve(VIP) : hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve(VIP)", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceContentLinkifiesBareUrls() {
        Text line = Text.literal("[萌新]player>>see https://example.com/x now");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "player", "player", "see https://example.com/x now", false))
                .orElseThrow();
        assertEquals("see https://example.com/x now", parts.content().getString());
        assertTrue(parts.content().runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL));
    }

    @Test
    void sliceAngleLineSenderDropsVanillaAngleBrackets() {
        Text line = Text.literal("<Steve> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void slicePrefixedAngleLineSenderKeepsPrefixOnly() {
        Text line = Text.literal("[VIP]<Steve> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void emptyWhenMetaHasNoUsableName() {
        Text line = Text.literal("[系统]公告: 欢迎");
        assertTrue(ChatPipeline.sliceRichText(line, new SenderMeta(null, null, null, null, true)).isEmpty());
    }
}
