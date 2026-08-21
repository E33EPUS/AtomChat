package com.atom.chat;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomChat implements ModInitializer {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("AtomChat initialized");
    }
}
