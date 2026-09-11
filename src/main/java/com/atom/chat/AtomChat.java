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
        // Config seam for anti-spam: wire the pure logic to the real config
        // only in a real launch (unit tests keep the safe no-merge default).
        com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier =
                () -> com.atom.chat.config.AtomChatConfig.get().antiSpamEnabled;
        // Payload codecs and avatar-companion receivers are registered on the
        // NeoForge payload bus; this event fires for both logical sides.
        modEventBus.addListener(AtomChat::onRegisterPayloads);
        version = modContainer.getModInfo().getVersion().toString();
        LOGGER.info("AtomChat initialized");
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        com.atom.chat.net.AvatarPayloads.register(event);
        // Media companion: server-hosted chat images / GIFs, same dual
        // entrypoint pattern and master hosting switch as the avatar side.
        com.atom.chat.net.MediaPayloads.register(event);
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
