package com.atom.chat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomChat implements ModInitializer {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Config seam for anti-spam: wire the pure logic to the real config
        // only in a real launch (unit tests keep the safe no-merge default).
        com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier =
                () -> com.atom.chat.config.AtomChatConfig.get().antiSpamEnabled;
        // Avatar companion: codecs on both logical sides, receivers on the
        // game-server side (dedicated server or an integrated double-open).
        com.atom.chat.net.AvatarPayloads.register();
        com.atom.chat.net.AvatarCompanionServer.register();
        LOGGER.info("AtomChat initialized");
    }

    /**
     * Friendly mod version for the settings about page. Read from the loader
     * metadata rather than a hardcoded constant so it can never drift away
     * from gradle.properties. Guarded because the container is absent outside
     * a real Fabric launch.
     */
    public static String version() {
        try {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
