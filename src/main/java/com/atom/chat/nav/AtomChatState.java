package com.atom.chat.nav;

import java.util.List;

public final class AtomChatState {
    private static List<AppPage> stack = List.of(AppPage.CHAT_LIST);

    private AtomChatState() {
    }

    public static synchronized List<AppPage> snapshot() {
        return List.copyOf(stack);
    }

    public static synchronized void save(List<AppPage> pages) {
        if (pages == null || pages.isEmpty() || pages.get(0) == null || !pages.get(0).isRoot()) {
            stack = List.of(AppPage.CHAT_LIST);
            return;
        }
        for (int i = 1; i < pages.size(); i++) {
            AppPage page = pages.get(i);
            if (page == null || page.isRoot()) {
                stack = List.of(AppPage.CHAT_LIST);
                return;
            }
        }
        stack = List.copyOf(pages);
    }

    static synchronized void reset() {
        stack = List.of(AppPage.CHAT_LIST);
    }
}
