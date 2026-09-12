package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Supplier;

/**
 * Best available self display name: the decorated variant of the local
 * player's name (titles, team prefix/colour). Port of e33chat's
 * {@code ownDisplayName()} / {@code cacheOwnDecoratedName()} (MIT, same
 * author), so own bubbles read "[称号]E33EPUS" like everyone else sees them
 * instead of a bare profile name.
 *
 * <p>Source order (e33chat parity): tab-list display name → styled name cached
 * from our own chat echoes → scoreboard team prefix/colour/suffix → bare
 * profile name. Vanilla servers send no tab-list display name and no team
 * decoration, so the chat cache is the source that usually wins.
 */
public final class OwnIdentity {
    private OwnIdentity() {
    }

    /** Seam so the cache rules stay unit-testable without a running client. */
    public static volatile Supplier<String> localNameSupplier = () -> {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null ? client.player.getName().getString() : null;
    };

    private static volatile RichText cachedDecorated;

    /**
     * Remembers a styled self name seen in chat (the signed-chat capture hands
     * us the server-decorated component on every own echo). Bare or blank
     * variants are ignored — only decorated names are worth caching.
     */
    public static void cache(RichText sender) {
        if (sender == null || sender.isEmpty()) {
            return;
        }
        String text = sender.getString();
        String bare = localNameSupplier.get();
        if (text == null || text.isBlank() || bare == null || bare.isBlank() || text.equals(bare)) {
            return;
        }
        cachedDecorated = sender;
    }

    /** Drops the cached name; called on join and disconnect. */
    public static void reset() {
        cachedDecorated = null;
    }

    /** Styled display name for own bubbles; best available source wins. */
    public static RichText displayNameRich() {
        Component tab = tabName();
        if (tab != null) {
            return RichText.of(tab);
        }
        if (cachedDecorated != null) {
            return cachedDecorated;
        }
        Component team = teamName();
        if (team != null) {
            return RichText.of(team);
        }
        String bare = localNameSupplier.get();
        return RichText.literal(bare != null ? bare : "?");
    }

    /**
     * Every rendering of our own name a server may put on the wire: the bare
     * profile name, the tab-list display name, a previously cached decorated
     * name and the locally computed scoreboard-team decoration. Echo
     * suppression compares the captured wire name against these
     * ({@link OwnNameMatcher}) so a {@code /team} prefix cannot turn our own
     * message into someone else's bubble.
     */
    public static java.util.List<String> wireNameCandidates() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String bare = localNameSupplier.get();
        if (bare != null && !bare.isBlank()) {
            out.add(bare);
        }
        Component tab = tabName();
        if (tab != null) {
            out.add(tab.getString());
        }
        RichText cached = cachedDecorated;
        if (cached != null) {
            out.add(cached.getString());
        }
        Component team = teamName();
        if (team != null) {
            out.add(team.getString());
        }
        return new java.util.ArrayList<>(out);
    }

    private static Component tabName() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) {
            return null;
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        return info != null ? info.getTabListDisplayName() : null;
    }

    private static Component teamName() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.player.getTeam() == null) {
            return null;
        }
        var team = client.player.getTeam();
        Component prefix = team.getPlayerPrefix();
        Component suffix = team.getPlayerSuffix();
        ChatFormatting color = team.getColor();
        boolean hasPrefix = prefix != null && !prefix.getString().isEmpty();
        boolean hasSuffix = suffix != null && !suffix.getString().isEmpty();
        if (!hasPrefix && !hasSuffix && color == null) {
            return null;
        }
        MutableComponent name = Component.literal(localNameSupplier.get());
        if (color != null) {
            name = name.withStyle(color);
        }
        MutableComponent out = Component.empty();
        if (hasPrefix) {
            out.append(prefix);
        }
        out.append(name);
        if (hasSuffix) {
            out.append(suffix);
        }
        return out;
    }
}
