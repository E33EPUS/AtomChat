package com.atom.chat.config;

import com.atom.chat.AtomChat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side AtomChat settings (0.2.7), stored next to the client file at
 * {@code config/atomchat/atomchat-server.json} in the server's game dir.
 * Loaded from the common entrypoint so a dedicated server and the integrated
 * server of a double-open client share one file.
 *
 * <p>{@link #hostingEnabled} is the single master switch for server-hosted
 * companion data: chat images / animated GIFs and player avatars. With it off
 * clients fall back to the external image host (uguu) and to plain skins.
 */
public class AtomChatServerConfig {
    public static final AtomChatServerConfig DEFAULT = new AtomChatServerConfig();

    /** Master switch: host uploaded media and avatars on this server. */
    public boolean hostingEnabled = true;
    /** Largest single hosted file, in KB. */
    public int maxFileKb = 2048;
    /** Total bytes kept in the media store, in MB. */
    public int maxTotalMb = 512;
    /** Total bytes kept in the avatar store, in MB. */
    public int maxAvatarTotalMb = 64;
    /**
     * Delete hosted media and avatars older than this many days; 0 keeps them
     * forever. The sweep runs at server start, every five minutes while the
     * server is up, and after every accepted upload, so it does not depend on
     * someone uploading to trigger it.
     */
    public int retentionDays = 7;
    /** Per-player upload cooldown, in milliseconds. */
    public int uploadCooldownMs = 3000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile AtomChatServerConfig instance;

    public static AtomChatServerConfig get() {
        AtomChatServerConfig local = instance;
        if (local == null) {
            synchronized (AtomChatServerConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Re-reads the file so hand-edits take effect without a restart. */
    public static void reload() {
        instance = load();
    }

    public int maxFileBytes() {
        return Math.max(1, maxFileKb) * 1024;
    }

    public long maxTotalBytes() {
        return Math.max(1L, maxTotalMb) * 1024L * 1024L;
    }

    public long maxAvatarTotalBytes() {
        return Math.max(1L, maxAvatarTotalMb) * 1024L * 1024L;
    }

    private static Path path() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            if (configDir != null) {
                return configDir.resolve("atomchat/atomchat-server.json");
            }
        } catch (Throwable ignored) {
            // No Forge launch to ask (unit tests, tooling): fall back to the
            // conventional location relative to the working directory.
        }
        return Path.of("config", "atomchat", "atomchat-server.json");
    }

    private static AtomChatServerConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                AtomChatServerConfig config = GSON.fromJson(
                        Files.readString(path, StandardCharsets.UTF_8), AtomChatServerConfig.class);
                if (config != null) {
                    // Write the merged instance back: options added in newer
                    // builds are absent from an older file, and Gson drops
                    // unknown keys, so without this a new option could never be
                    // switched on by editing the file. Existing values survive
                    // the round trip.
                    save(config);
                    return config;
                }
            } catch (Exception e) {
                AtomChat.LOGGER.error("Failed to load AtomChat server config, using defaults", e);
            }
        }
        AtomChatServerConfig config = new AtomChatServerConfig();
        save(config);
        return config;
    }

    public static void save(AtomChatServerConfig config) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AtomChat.LOGGER.error("Failed to save AtomChat server config", e);
        }
    }
}
