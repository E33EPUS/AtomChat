package com.atom.chat;

import com.atom.chat.config.AtomChatConfig;
import net.fabricmc.api.ClientModInitializer;

public class AtomChatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AtomChatConfig.get();
        AtomChat.LOGGER.info("AtomChat client initialized");
    }
}
