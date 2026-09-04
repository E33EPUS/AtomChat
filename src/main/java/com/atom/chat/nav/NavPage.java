package com.atom.chat.nav;

import com.atom.chat.chat.PlayerRef;

import java.util.Objects;

/**
 * A navigation entry: the concrete page plus an optional conversation target.
 * Root pages have no target; WORLD_CHAT is the public channel; PRIVATE_CHAT
 * carries the partner (uuid + real name) it belongs to.
 */
public record NavPage(AppPage page, PlayerRef target) {
    public NavPage {
        Objects.requireNonNull(page, "page");
        if (page == AppPage.PRIVATE_CHAT && target == null) {
            throw new IllegalArgumentException("PRIVATE_CHAT requires a PlayerRef target");
        }
        if (page != AppPage.PRIVATE_CHAT) {
            target = null;
        }
    }

    public static NavPage of(AppPage page) {
        return new NavPage(page, null);
    }

    public static NavPage privateChat(PlayerRef target) {
        return new NavPage(AppPage.PRIVATE_CHAT, target);
    }

    public boolean isRoot() {
        return page.isRoot();
    }
}
