package com.atom.chat.config;

import com.atom.chat.AtomChat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AtomChatConfig {
    public static final AtomChatConfig DEFAULT = new AtomChatConfig();

    public float panelWidth = 420.0F;
    public float panelHeight = 780.0F;
    /** Rounded panel background blur (raw GL + core shader, outside Skia). */
    public boolean blurEnabled = true;
    public boolean animationEnabled = true;
    /** Dumps avatar sampling PNGs to {@code <config>/atomchat/debug/} for color debugging. */
    public boolean debug = false;
    public int accentColor = 0xFF4A90E2;
    public int ownBubbleColor = 0xFF4A90E2;
    public int otherBubbleColor = 0xFF343A44;
    public int panelBgColor = 0xEE16191F;
    public int textPrimaryColor = 0xFFFFFFFF;
    public int textSecondaryColor = 0xDCAAAABA;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AtomChatConfig instance;

    public static AtomChatConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AtomChatConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("atomchat/atomchat-client.json");
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                AtomChatConfig config = GSON.fromJson(json, AtomChatConfig.class);
                if (config != null) {
                    // Write the merged instance straight back: options added in
                    // newer builds are absent from an older file, and Gson drops
                    // unknown keys — so without this a new option could never be
                    // switched on by editing the file, it simply would not be
                    // there. Existing values are preserved by the round trip.
                    save(config);
                    return config;
                }
            } catch (Exception e) {
                AtomChat.LOGGER.error("Failed to load AtomChat config, using defaults", e);
            }
        }
        AtomChatConfig config = new AtomChatConfig();
        save(config);
        return config;
    }

    public static void save(AtomChatConfig config) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("atomchat/atomchat-client.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            AtomChat.LOGGER.error("Failed to save AtomChat config", e);
        }
    }
}
