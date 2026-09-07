package com.atom.chat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AtomChat.MOD_ID)
public class AtomChat {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Captured from the NeoForge mod container at construction time. */
    private static volatile String version = "unknown";

    public AtomChat(IEventBus modEventBus, ModContainer modContainer) {
        // Payload codecs and avatar-companion receivers are registered on the
        // NeoForge payload bus; this event fires for both logical sides.
        modEventBus.addListener(AtomChat::onRegisterPayloads);
        version = modContainer.getModInfo().getVersion().toString();
        LOGGER.info("AtomChat initialized");
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        com.atom.chat.net.AvatarPayloads.register(event);
    }

    /**
     * Friendly mod version for the settings about page. Read from the loader
     * metadata rather than a hardcoded constant so it can never drift away
     * from gradle.properties.
     */
    public static String version() {
        return version;
    }
}
