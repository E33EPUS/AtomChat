package com.atom.chat.nav;

public enum AppPage {
    CHAT_LIST("chat_list", true),
    PROFILE("profile", true),
    SETTINGS("settings", true),
    WORLD_CHAT("world_chat", false),
    PRIVATE_CHAT("private_chat", false),
    /**
     * One settings sub-page (外观 / 聊天 / 隐私 / 关于). Which one is carried by
     * {@link NavPage#param()}, so adding a section never grows this enum.
     */
    SETTINGS_SECTION("settings_section", false);

    private final String id;
    private final boolean root;

    AppPage(String id, boolean root) {
        this.id = id;
        this.root = root;
    }

    public String id() {
        return id;
    }

    public boolean isRoot() {
        return root;
    }

    public static AppPage fromId(String id) {
        for (AppPage page : values()) {
            if (page.id.equals(id)) {
                return page;
            }
        }
        throw new IllegalArgumentException("Unknown AppPage id: " + id);
    }
}
