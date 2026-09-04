package com.atom.chat.page;

import com.atom.chat.nav.AppPage;

/**
 * Host interface implemented by the AtomChat shell screen so page classes can
 * navigate without depending on the concrete Minecraft screen.
 */
public interface PageHost {
    void pushPage(AppPage page);

    void popPage();

    void switchRoot(AppPage root);
}
