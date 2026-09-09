package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        MinecraftClient client = MinecraftClient.getInstance();
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
        Text tab = tabName();
        if (tab != null) {
            return RichText.of(tab);
        }
        if (cachedDecorated != null) {
            return cachedDecorated;
        }
        Text team = teamName();
        if (team != null) {
            return RichText.of(team);
        }
        String bare = localNameSupplier.get();
        return RichText.literal(bare != null ? bare : "?");
    }

    private static Text tabName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return null;
        }
        PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return info != null ? info.getDisplayName() : null;
    }

    private static Text teamName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getScoreboardTeam() == null) {
            return null;
        }
        var team = client.player.getScoreboardTeam();
        Text prefix = team.getPrefix();
        Text suffix = team.getSuffix();
        Formatting color = team.getColor();
        boolean hasPrefix = prefix != null && !prefix.getString().isEmpty();
        boolean hasSuffix = suffix != null && !suffix.getString().isEmpty();
        if (!hasPrefix && !hasSuffix && color == null) {
            return null;
        }
        MutableText name = Text.literal(localNameSupplier.get());
        if (color != null) {
            name = name.formatted(color);
        }
        MutableText out = Text.empty();
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
