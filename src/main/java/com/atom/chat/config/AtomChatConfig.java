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
    public int bubbleTextColor = 0xFFFFFFFF;
    public int ownBubbleColor = 0xFF1E90FF;
    public int otherBubbleColor = 0xFF2C3E50;
    public int panelBgColor = 0xEE16191F;
    public int textPrimaryColor = 0xFFFFFFFF;
    public int textSecondaryColor = 0xDCAAAABA;
    /**
     * Card/chrome surface colour. The card-tint slider sets this colour's
     * alpha (60 at 0% → 255 at 100%); white keeps the shipped frosted look.
     */
    public int cardColor = 0xFFFFFFFF;
    /** Global blocked-player real names, persisted locally. */
    public java.util.List<String> blockedPlayers = new java.util.ArrayList<>();

    /**
     * Client-side chat templates (e33chat parity), e.g. {@code "<{name}> {content}"}.
     * Empty = disabled; the guards keep their current behaviour. Placeholders:
     * {name} {display_name} {prefix} {suffix} {sep} {content} (exactly one
     * {content}, any position). Hand-edit this file; templates are re-read when
     * a chat screen opens.
     */
    public java.util.List<String> chatTemplates = new java.util.ArrayList<>();
    /**
     * Same syntax as {@link #chatTemplates} but claims incoming private-message
     * lines (plugin-reformatted {@code /msg}) into the private panel.
     */
    public java.util.List<String> whisperTemplates = new java.util.ArrayList<>();
    /**
     * Teleport command for the avatar/player menu: {@code auto} (probe the
     * server command tree for a tpa-family command, fall back on failure),
     * {@code tp} or {@code tpa}.
     */
    public String teleportCommandMode = "auto";
    /**
     * Corner style for the surrounding chrome (panel, cards, pills, popups —
     * chat bubbles excluded on purpose): {@code large} (shipped default),
     * {@code medium} or {@code small} (modern flat). No settings UI yet —
     * presets write it and the file can be hand-edited.
     */
    public String cornerStyle = "large";
    /**
     * Card/chrome surface tint, 0..1. At 0 the surfaces are the frosted
     * translucent white washes; at 1 they are opaque tints lifted from the
     * panel colour. In between they slide through a semi-transparent grey —
     * one axis, three visual stops.
     */
    public float cardTint = 0.235F;
    /** Panel outline (bezel ring) colour. */
    public int panelOutlineColor = 0xFFFFFFFF;
    /**
     * White phone-style bezel ring around the panel. Part of the frosted look;
     * the opaque modern preset turns it off.
     */
    public boolean panelOutline = true;
    /**
     * Body/quote text colour inside <em>other</em> players' bubbles. Own
     * bubbles keep {@link #bubbleTextColor}.
     */
    public int otherBubbleTextColor = 0xFFFFFFFF;
    /**
     * Last applied built-in theme ({@code frosted} / {@code modern}); empty
     * until the user picks one. Display only — the single knobs stay editable
     * after a preset lands, so this never gates any behaviour.
     */
    public String themeName = "";
    /**
     * When true only an explicit {@code @Name} counts as a mention; when
     * false the bare name as a standalone token counts too (e33chat default).
     */
    public boolean mentionRequireAt = false;
    /**
     * Whether incoming image messages are fetched and rendered. Off: every
     * image message shows the green [图片] placeholder instead — nothing is
     * downloaded or cached (saving one by hand still fetches on demand).
     */
    public boolean imageMessagesEnabled = true;
    /**
     * Time divider between messages, in minutes; 0 disables the divider. A
     * pill with the clock time is drawn above a message when this many minutes
     * have passed since the previous one.
     */
    public int timestampIntervalMinutes = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AtomChatConfig instance;

    public static AtomChatConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Re-reads the config file. Called when a chat screen opens so hand-edited
     * templates take effect without a game restart.
     */
    public static void reload() {
        instance = load();
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
