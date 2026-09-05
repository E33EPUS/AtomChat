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

    public float panelWidth = 440.0F;
    public float panelHeight = 780.0F;
    /** Rounded panel background blur (raw GL + core shader, outside Skia). */
    public boolean blurEnabled = true;
    /**
     * Master switch for decorative motion: message entrance, page/tab push,
     * the panel open slide, the avatar poke shake and scroll snapping.
     * Functional feedback (hover tint, wheel glide) is deliberately kept even
     * when this is off, so the UI never stops responding to the pointer.
     */
    public boolean animationEnabled = true;
    /** QQ-style horizontal slide + fade when a message first enters the viewport. */
    public boolean messageEntryAnimation = true;
    /** Double-clicking another player's avatar performs the QQ-style poke shake. */
    public boolean avatarPokeEnabled = true;
    /**
     * When true, public messages from blocked players are dropped at capture
     * time. When false they are shown normally — the conversation card stays
     * greyed out and private chat stays read-only either way, so a block
     * always silences direct contact.
     */
    public boolean hideBlockedMessages = true;
    /**
     * Panel background opacity, 0..1. Applied to the solid fallback colour and
     * to the blur tint alike, so one slider governs how much world shows
     * through regardless of whether blur is on.
     */
    public float panelOpacity = 0.93F;
    /**
     * AtomChat-only UI scale multiplier on top of the vanilla GUI scale. It is
     * applied at the Skia canvas and in every coordinate conversion, never to
     * {@link com.atom.chat.ui.UiTokens} — those constants are class-init
     * statics and could not change at runtime.
     */
    public float uiScale = 1.0F;
    /** Dumps avatar sampling PNGs to {@code <config>/atomchat/debug/} for color debugging. */
    public boolean debug = false;
    public int accentColor = 0xFF4A90E2;
    public int ownBubbleColor = 0xFF4A90E2;
    public int otherBubbleColor = 0xFF343A44;
    public int panelBgColor = 0xEE16191F;
    public int textPrimaryColor = 0xFFFFFFFF;
    public int textSecondaryColor = 0xDCAAAABA;
    /** Global blocked-player real names, persisted locally. */
    public java.util.List<String> blockedPlayers = new java.util.ArrayList<>();

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
                    if (config.blockedPlayers == null) {
                        config.blockedPlayers = new java.util.ArrayList<>();
                    }
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
