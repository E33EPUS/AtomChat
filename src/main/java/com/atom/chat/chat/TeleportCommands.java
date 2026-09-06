package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * tp/tpa command resolution (0.1.11). The avatar menu's teleport action used
 * a hardcoded {@code /tp}, which is useless on servers running a tpa plugin
 * (vanilla /tp is often permission-blocked there, and ordinary players do not
 * know they must switch). Three complementary strategies:
 *
 * <ol>
 *   <li><b>Command-tree probe</b> — the server pushes its command tree to the
 *       client on join ({@code ClientPlayNetworkHandler#getCommandDispatcher()},
 *       yarn {@code method_2886}); if the root carries {@code tpa}/
 *       {@code tpaccept}/{@code tpahere}, use {@code /tpa}. Caveat: some
 *       servers hide plugin commands from the tree (Spigot/Paper
 *       {@code commands.tab-complete}), so a miss is not proof.</li>
 *   <li><b>Failure fallback</b> — if a teleport command was sent and a
 *       "unknown command / no permission" system reply arrives within a short
 *       window, switch for the rest of the session. Result-based, so it also
 *       covers the hidden-tree case.</li>
 *   <li><b>Manual override</b> — {@code teleportCommandMode} = auto|tp|tpa in
 *       the settings; auto is the default.</li>
 * </ol>
 */
public final class TeleportCommands {
    private TeleportCommands() {
    }

    private static final long FAILURE_WINDOW_MS = 2_000L;

    private static final String[] FAILURE_KEYWORDS = {
            "unknown command", "unknown or incomplete command",
            "未知命令", "未知的命令", "未知或不完整的命令", "命令不正确",
            "no permission", "not have permission",
            "没有权限", "权限不足", "无权限"
    };

    private static volatile String active = "/tp";
    private static volatile boolean probed;
    private static volatile boolean failureSwitched;
    private static volatile long teleportSentAt;

    /** Resets the session state (called on server join). */
    public static void reset() {
        probed = false;
        failureSwitched = false;
        teleportSentAt = 0;
        active = "/tp";
    }

    /**
     * Resolves the teleport command for the configured mode. The probe runs
     * once per session; a failure switch overrides it for the session.
     */
    public static String commandFor(MinecraftClient client, String mode) {
        if ("tp".equals(mode)) {
            return "/tp";
        }
        if ("tpa".equals(mode)) {
            return "/tpa";
        }
        if (!failureSwitched && !probed) {
            probed = true;
            if (probeSupportsTpa(client)) {
                active = "/tpa";
            }
        }
        return active;
    }

    /** True when the server's command tree advertises a tpa-family command. */
    static boolean probeSupportsTpa(MinecraftClient client) {
        try {
            if (client == null || client.player == null || client.player.networkHandler == null) {
                return false;
            }
            var root = client.player.networkHandler.getCommandDispatcher().getRoot();
            return root.getChild("tpa") != null
                    || root.getChild("tpaccept") != null
                    || root.getChild("tpahere") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Marks that a teleport command just went out (arms the failure window). */
    public static void noteTeleportSent() {
        teleportSentAt = System.currentTimeMillis();
    }

    /**
     * Failure-watch hook: call for every system line. When a failure-shaped
     * reply arrives inside the window after a teleport send, the session
     * switches to the other command.
     */
    public static void checkFailure(String raw) {
        if (teleportSentAt == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - teleportSentAt > FAILURE_WINDOW_MS) {
            return;
        }
        if (isFailureMessage(raw)) {
            active = active.equals("/tp") ? "/tpa" : "/tp";
            failureSwitched = true;
            teleportSentAt = 0;
        }
    }

    /** Test seam: inspect the session-active command. */
    static String activeCommand() {
        return active;
    }

    static boolean isFailureMessage(String raw) {
        if (raw == null) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String keyword : FAILURE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
