package com.atom.chat.page;

import com.atom.chat.chat.PlayerRef;
import com.atom.chat.nav.AppPage;

/**
 * Host interface implemented by the AtomChat shell screen so page classes can
 * navigate without depending on the concrete Minecraft screen.
 */
public interface PageHost {
    void pushPage(AppPage page);

    /** Pushes the public world channel (used by the Public conversation card). */
    void openWorldChat();

    /** Pushes a private conversation page. */
    void openPrivateChat(PlayerRef target);

    void popPage();

    void switchRoot(AppPage root);
}
