package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportCommandsTest {
    @Test
    void fixedModesBypassEverything() {
        assertEquals("/tp", TeleportCommands.commandFor(null, "tp"));
        assertEquals("/tpa", TeleportCommands.commandFor(null, "tpa"));
    }

    @Test
    void unknownModeFallsBackToAuto() {
        // In a unit test the probe cannot reach a dispatcher and returns
        // false, so auto resolves to /tp.
        assertEquals("/tp", TeleportCommands.commandFor(null, "something-else"));
    }

    @Test
    void failureKeywordsAreRecognized() {
        assertTrue(TeleportCommands.isFailureMessage("Unknown or incomplete command, see below for error"));
        assertTrue(TeleportCommands.isFailureMessage("你没有权限使用该命令"));
        assertTrue(TeleportCommands.isFailureMessage("未知命令。请输入 /help 获取帮助"));
        assertTrue(TeleportCommands.isFailureMessage("You do not have permission to use this command"));
        assertFalse(TeleportCommands.isFailureMessage("Teleported Steve to Alice"));
        assertFalse(TeleportCommands.isFailureMessage(null));
    }
}
