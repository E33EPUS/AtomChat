package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EasyBotParserTest {
    @Test
    void defaultTemplateWithQqNumber() {
        SenderMeta meta = EasyBotParser.tryParse("[群名] <昵称(123456)> 你好世界");
        assertNotNull(meta);
        assertEquals("昵称", meta.senderName());
        assertEquals("123456", meta.profileName());
        assertEquals("你好世界", meta.contentText());
    }

    @Test
    void fullWidthParensQqNumber() {
        SenderMeta meta = EasyBotParser.tryParse("[群] <昵称（123456）> hi");
        assertNotNull(meta);
        assertEquals("昵称", meta.senderName());
        assertEquals("123456", meta.profileName());
    }

    @Test
    void bareQqNumberAsName() {
        SenderMeta meta = EasyBotParser.tryParse("<123456> hello");
        assertNotNull(meta);
        assertEquals("123456", meta.senderName());
        assertEquals("123456", meta.profileName());
    }

    @Test
    void groupLabelOptional() {
        SenderMeta meta = EasyBotParser.tryParse("<小明> 早安");
        assertNotNull(meta);
        assertEquals("小明", meta.senderName());
        assertEquals("早安", meta.contentText());
    }

    @Test
    void multiLineContentIsKept() {
        SenderMeta meta = EasyBotParser.tryParse("<小明> 第一行\n第二行");
        assertNotNull(meta);
        assertEquals("第一行\n第二行", meta.contentText());
    }

    @Test
    void broadcastLabelWithoutQqStaysSystem() {
        assertNull(EasyBotParser.tryParse("[公告] <Server> restarting"));
        assertNull(EasyBotParser.tryParse("<系统> maintenance"));
    }

    @Test
    void broadcastLabelWithQqIsStillClaimed() {
        assertNotNull(EasyBotParser.tryParse("[公告] <小明(123456)> hi"));
    }

    @Test
    void oversizedOrEmptyNameRejected() {
        assertNull(EasyBotParser.tryParse("<" + "x".repeat(40) + "> hi"));
        assertNull(EasyBotParser.tryParse("<> hi"));
        assertNull(EasyBotParser.tryParse("<小明>   "));
    }

    @Test
    void nonMatchingLinesReturnNull() {
        assertNull(EasyBotParser.tryParse("Steve: hi"));
        assertNull(EasyBotParser.tryParse("[群名] 昵称 hi"));
        assertNull(EasyBotParser.tryParse(null));
    }
}
