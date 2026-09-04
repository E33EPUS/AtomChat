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
    void emptyWhenMetaHasNoUsableName() {
        Text line = Text.literal("[系统]公告: 欢迎");
        assertTrue(ChatPipeline.sliceRichText(line, new SenderMeta(null, null, null, null, true)).isEmpty());
    }
}
