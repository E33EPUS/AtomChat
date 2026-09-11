package com.atom.chat;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AtomChat.MOD_ID)
public class AtomChat {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Resolved lazily from the Forge mod list (see {@link #version()}). */
    private static volatile String version;

    public AtomChat() {
        // Config seam for anti-spam: wire the pure logic to the real config
        // only in a real launch (unit tests keep the safe no-merge default).
        com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier =
                () -> com.atom.chat.config.AtomChatConfig.get().antiSpamEnabled;

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // The avatar channel is common: a dedicated server and the integrated
        // server of a double-open client both need it (this event fires for
        // both logical sides).
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            com.atom.chat.net.AvatarPayloads.register();
            // Media companion: server-hosted chat images / GIFs, same dual
            // entrypoint pattern and master hosting switch as the avatar side.
            com.atom.chat.net.MediaPayloads.register();
        }));

        if (FMLEnvironment.dist.isClient()) {
            // Client-only listeners, key mappings, shaders and the AWT setup.
            // Reached only on a client, so a dedicated server never resolves
            // the client class.
            AtomChatClient.init(modEventBus);
        }

        LOGGER.info("AtomChat initialized");
    }

    /**
     * Friendly mod version for the settings about page. Read from the loader
     * metadata rather than a hardcoded constant so it can never drift away
     * from gradle.properties. Falls back to "unknown" outside a real launch
     * (unit tests), where no mod list exists.
     */
    public static String version() {
        String resolved = version;
        if (resolved == null) {
            resolved = "unknown";
            try {
                resolved = net.minecraftforge.fml.ModList.get()
                        .getModContainerById(MOD_ID)
                        .map(container -> container.getModInfo().getVersion().toString())
                        .orElse("unknown");
            } catch (Throwable ignored) {
            }
            version = resolved;
        }
        return resolved;
    }
}
