# AtomChat Navigation Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-panel page stack to AtomChat with conversation-list root pages, bottom tabs, a world-channel detail page, and in-memory restore.

**Architecture:** AtomChatScreen becomes a shell owning `NavigationStack<AppPage>` and a bottom tab bar. Root pages are new light classes (`ConversationListPage`, placeholders); the existing world-chat rendering stays inside AtomChatScreen and is gated by the `WORLD_CHAT` page. A new keybinding (default Y) opens the saved stack, while the vanilla chat key continues to open the world-channel detail directly.

**Tech Stack:** Java 21, Fabric 1.21.1, Skija/HumbleUI, JUnit 5.

## Global Constraints

- Minecraft 1.21.1 + Fabric Loader 0.16.0+ + Fabric API + Java 21+.
- Commit messages in English, no emoji.
- `AtomChatScreen` already has 3000+ lines; do not move world-chat logic out in this plan.
- All new UI text goes through `assets/atomchat/lang/zh_cn.json` and `en_us.json`.
- All icons are inline SVG path strings; no image assets.
- Pure logic must be JUnit-testable without launching Minecraft.
- Existing 104 tests must stay green.

---

## File Map

**Create:**
- `src/main/java/com/atom/chat/nav/AppPage.java`
- `src/main/java/com/atom/chat/nav/NavigationStack.java`
- `src/main/java/com/atom/chat/nav/AtomChatState.java`
- `src/test/java/com/atom/chat/nav/AppPageTest.java`
- `src/test/java/com/atom/chat/nav/NavigationStackTest.java`
- `src/test/java/com/atom/chat/nav/AtomChatStateTest.java`
- `src/main/java/com/atom/chat/page/ConversationListPage.java`
- `src/main/java/com/atom/chat/page/PlaceholderPage.java`
- `src/main/java/com/atom/chat/page/PageHost.java`

**Modify:**
- `src/main/java/com/atom/chat/AtomChatClient.java` — Y keybinding + tick open.
- `src/main/java/com/atom/chat/mixin/MinecraftClientMixin.java` — pass `AtomChatOpenMode.DIRECT_WORLD`.
- `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java` — shell navigation, page dispatch, back arrow, bottom tab bar, world-chat gating.
- `src/main/java/com/atom/chat/ui/UiTokens.java` — tab-bar/root-layout tokens.
- `src/main/java/com/atom/chat/ui/UiLayout.java` — root-mode layout and `tabBar`.
- `src/test/java/com/atom/chat/ui/UiLayoutTest.java` — root layout tests.
- `src/main/resources/assets/atomchat/lang/zh_cn.json`
- `src/main/resources/assets/atomchat/lang/en_us.json`

---

### Task 1: Pure navigation model

**Files:**
- Create: `src/main/java/com/atom/chat/nav/AppPage.java`
- Create: `src/main/java/com/atom/chat/nav/NavigationStack.java`
- Create: `src/main/java/com/atom/chat/nav/AtomChatState.java`
- Test: `src/test/java/com/atom/chat/nav/AppPageTest.java`
- Test: `src/test/java/com/atom/chat/nav/NavigationStackTest.java`
- Test: `src/test/java/com/atom/chat/nav/AtomChatStateTest.java`

**Interfaces:**
- Produces:
  - `enum AppPage { CHAT_LIST, PROFILE, SETTINGS, WORLD_CHAT }`
  - `AppPage.id()` -> `String`
  - `AppPage.fromId(String)` -> `AppPage`
  - `AppPage.isRoot()` -> `boolean`
  - `final class NavigationStack<T>`
    - `NavigationStack(T root)`
    - `void push(T page)`
    - `boolean pop()`
    - `T peek()`
    - `int size()`
    - `List<T> snapshot()`
    - `void replaceWithRoot(T root)`
  - `final class AtomChatState`
    - `static List<AppPage> snapshot()`
    - `static void save(List<AppPage> pages)`
    - `static void reset()` (package-private for tests)

- [ ] **Step 1: Write the failing tests**

Create `AppPageTest`:

```java
package com.atom.chat.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppPageTest {
    @Test
    void rootClassification() {
        assertTrue(AppPage.CHAT_LIST.isRoot());
        assertTrue(AppPage.PROFILE.isRoot());
        assertTrue(AppPage.SETTINGS.isRoot());
        assertFalse(AppPage.WORLD_CHAT.isRoot());
    }

    @Test
    void idRoundTrip() {
        for (AppPage page : AppPage.values()) {
            assertEquals(page, AppPage.fromId(page.id()));
        }
    }

    @Test
    void unknownIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> AppPage.fromId("nope"));
    }
}
```

Create `NavigationStackTest`:

```java
package com.atom.chat.nav;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NavigationStackTest {
    @Test
    void startsAtRootAndPushes() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        assertEquals(AppPage.CHAT_LIST, stack.peek());
        assertEquals(1, stack.size());

        stack.push(AppPage.WORLD_CHAT);
        assertEquals(AppPage.WORLD_CHAT, stack.peek());
        assertEquals(2, stack.size());
    }

    @Test
    void popReturnsFalseAtSingleRoot() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        assertFalse(stack.pop());
        assertEquals(AppPage.CHAT_LIST, stack.peek());
    }

    @Test
    void popReturnsTrueAndRestoresPreviousPage() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        stack.push(AppPage.WORLD_CHAT);
        assertTrue(stack.pop());
        assertEquals(AppPage.CHAT_LIST, stack.peek());
        assertEquals(1, stack.size());
    }

    @Test
    void snapshotIsUnmodifiableCopy() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        stack.push(AppPage.WORLD_CHAT);
        List<AppPage> snap = stack.snapshot();
        assertEquals(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT), snap);
        assertThrows(UnsupportedOperationException.class, () -> snap.add(AppPage.PROFILE));
    }

    @Test
    void replaceWithRootClearsStack() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        stack.push(AppPage.WORLD_CHAT);
        stack.replaceWithRoot(AppPage.SETTINGS);
        assertEquals(1, stack.size());
        assertEquals(AppPage.SETTINGS, stack.peek());
    }
}
```

Create `AtomChatStateTest`:

```java
package com.atom.chat.nav;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtomChatStateTest {
    @BeforeEach
    void reset() {
        AtomChatState.reset();
    }

    @Test
    void defaultIsChatList() {
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());
    }

    @Test
    void saveRestoreRoundTrip() {
        AtomChatState.save(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT));
        assertEquals(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT), AtomChatState.snapshot());
    }

    @Test
    void invalidSaveFallsBackToChatList() {
        AtomChatState.save(List.of(AppPage.WORLD_CHAT));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());

        AtomChatState.save(List.of());
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew.bat test --tests "com.atom.chat.nav.*" --console=plain`
Expected: compilation fails because `com.atom.chat.nav` does not exist.

- [ ] **Step 3: Implement AppPage**

```java
package com.atom.chat.nav;

public enum AppPage {
    CHAT_LIST("chat_list", true),
    PROFILE("profile", true),
    SETTINGS("settings", true),
    WORLD_CHAT("world_chat", false);

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
```

- [ ] **Step 4: Implement NavigationStack**

```java
package com.atom.chat.nav;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NavigationStack<T> {
    private final List<T> pages = new ArrayList<>();

    public NavigationStack(T root) {
        pages.add(Objects.requireNonNull(root, "root"));
    }

    public void push(T page) {
        pages.add(Objects.requireNonNull(page, "page"));
    }

    public boolean pop() {
        if (pages.size() <= 1) {
            return false;
        }
        pages.remove(pages.size() - 1);
        return true;
    }

    public T peek() {
        return pages.get(pages.size() - 1);
    }

    public int size() {
        return pages.size();
    }

    public List<T> snapshot() {
        return List.copyOf(pages);
    }

    public void replaceWithRoot(T root) {
        pages.clear();
        pages.add(Objects.requireNonNull(root, "root"));
    }
}
```

- [ ] **Step 5: Implement AtomChatState**

```java
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
        if (pages == null || pages.isEmpty() || !pages.get(0).isRoot()) {
            stack = List.of(AppPage.CHAT_LIST);
            return;
        }
        stack = List.copyOf(pages);
    }

    static synchronized void reset() {
        stack = List.of(AppPage.CHAT_LIST);
    }
}
```

- [ ] **Step 6: Run the tests and verify they pass**

Run: `./gradlew.bat test --tests "com.atom.chat.nav.*" --console=plain`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/atom/chat/nav src/test/java/com/atom/chat/nav
git commit -m "Add pure navigation model for AtomChat shell"
```

---

### Task 2: Root-mode layout geometry

**Files:**
- Modify: `src/main/java/com/atom/chat/ui/UiTokens.java`
- Modify: `src/main/java/com/atom/chat/ui/UiLayout.java`
- Test: `src/test/java/com/atom/chat/ui/UiLayoutTest.java`

**Interfaces:**
- Consumes: existing `UiLayout.Rect`, `UiTokens.s(float)`.
- Produces:
  - `UiTokens.TAB_BAR_H` (float)
  - `UiTokens.TAB_BAR_PAD_X` (float)
  - `UiLayout.ofRoot(panelX, panelY, panelW, panelH)` -> `UiLayout`
  - `UiLayout.tabBar` -> `UiLayout.Rect`
  - Root-mode `UiLayout.list` covers header bottom -> tab bar top.

- [ ] **Step 1: Add failing root-layout tests**

Append to `UiLayoutTest`:

```java
@Test
void rootLayoutHasTabBarAndNoInputBar() {
    UiLayout l = UiLayout.ofRoot(24, 100, 525, 975);
    UiLayout.Rect panel = l.rect();

    assertTrue(panel.contains(l.header), "header inside panel");
    assertTrue(panel.contains(l.tabBar), "tab bar inside panel");
    assertTrue(panel.contains(l.list), "list inside panel");
    assertTrue(l.list.h() > 0, "root content has room");
    assertEquals(panel.bottom() - l.tabBar.bottom(), UiTokens.PANEL_BOTTOM_PAD, 0.01F,
            "bottom breathing space under tab bar");
    assertTrue(l.inputBar.w() == 0.0F, "input bar is not used on root pages");
    assertTrue(l.tabBar.h() == UiTokens.TAB_BAR_H, "tab bar height uses token");
}

@Test
void rootTabBarNeverOverlapsHeader() {
    UiLayout l = UiLayout.ofRoot(24, 100, 525, 975);
    assertTrue(l.tabBar.y() >= l.header.bottom() + 1.0F, "tab bar below header");
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew.bat test --tests "com.atom.chat.ui.UiLayoutTest" --console=plain`
Expected: FAIL — `UiLayout.ofRoot` and `UiLayout.tabBar` do not exist.

- [ ] **Step 3: Add tokens**

In `UiTokens` context-menu section (or a new "Bottom tab bar" section):

```java
// Bottom tab bar (root pages only; hidden on detail pages)
public static final float TAB_BAR_H = s(64);
public static final float TAB_BAR_PAD_X = s(12);
public static final float ROOT_CONTENT_GAP = s(10);
```

- [ ] **Step 4: Add root mode to UiLayout**

Refactor `UiLayout` so every constructor delegates to a private constructor with `boolean showInput`. Keep the existing public factories unchanged.

```java
public static UiLayout of(float panelX, float panelY, float panelW, float panelH) {
    return new UiLayout(panelX, panelY, panelW, panelH, 0.0F, 0.0F, true);
}

public static UiLayout of(float panelX, float panelY, float panelW, float panelH, float inputExtraH, float replyH) {
    return new UiLayout(panelX, panelY, panelW, panelH, inputExtraH, replyH, true);
}

public static UiLayout ofRoot(float panelX, float panelY, float panelW, float panelH) {
    return new UiLayout(panelX, panelY, panelW, panelH, 0.0F, 0.0F, false);
}
```

Inside the private constructor:

```java
this.tabBar = showInput
    ? new Rect(0, 0, 0, 0)
    : new Rect(panelX + UiTokens.LIST_PAD_X,
               panelY + panelH - UiTokens.TAB_BAR_H - UiTokens.PANEL_BOTTOM_PAD,
               panelW - UiTokens.LIST_PAD_X * 2.0F,
               UiTokens.TAB_BAR_H);
float bottomOfContent = showInput ? inputY : this.tabBar.y();
this.list = new Rect(panelX + UiTokens.LIST_PAD_X, listTop,
        panelW - UiTokens.LIST_PAD_X * 2.0F,
        Math.max(0.0F, bottomOfContent - listTop));
if (!showInput) {
    this.inputBar = new Rect(0, 0, 0, 0);
    this.replyBar = new Rect(0, 0, 0, 0);
    this.imageBtn = new Rect(0, 0, 0, 0);
    this.emojiBtn = new Rect(0, 0, 0, 0);
    this.sendBtn = new Rect(0, 0, 0, 0);
    this.inputTextCenterY = 0.0F;
}
```

Do not break the existing `inputExtraH` behavior; the existing tests assert the old geometry exactly.

- [ ] **Step 5: Run all tests and verify they pass**

Run: `./gradlew.bat test --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/atom/chat/ui/UiTokens.java src/main/java/com/atom/chat/ui/UiLayout.java src/test/java/com/atom/chat/ui/UiLayoutTest.java
git commit -m "Add root layout and bottom tab bar geometry"
```

---

### Task 3: Y keybinding and screen entry modes

**Files:**
- Modify: `src/main/java/com/atom/chat/AtomChatClient.java`
- Modify: `src/main/java/com/atom/chat/mixin/MinecraftClientMixin.java`
- Modify: `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`
- Modify: `src/main/resources/assets/atomchat/lang/zh_cn.json`
- Modify: `src/main/resources/assets/atomchat/lang/en_us.json`

**Interfaces:**
- Produces:
  - `enum AtomChatOpenMode { DIRECT_WORLD, RESTORE }` (nested in `AtomChatScreen`)
  - `AtomChatScreen(String originalChatText)`
  - `AtomChatScreen(String originalChatText, AtomChatOpenMode mode)`
  - `AtomChatClient.OPEN_ATOMCHAT_KEY` (`KeyBinding`)

- [ ] **Step 1: Add language keys**

zh_cn:

```json
"key.atomchat.open": "打开 AtomChat 继承页",
"key.atomchat.category": "AtomChat"
```

en_us:

```json
"key.atomchat.open": "Open AtomChat Inherited Page",
"key.atomchat.category": "AtomChat"
```

- [ ] **Step 2: Register the keybinding and tick handler**

In `AtomChatClient.onInitializeClient()`, add:

```java
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.AtomChatScreen;
import org.lwjgl.glfw.GLFW;

public static final KeyBinding OPEN_ATOMCHAT_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyBinding("key.atomchat.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y,
                "key.atomchat.category"));
```

And at the end of `onInitializeClient`:

```java
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    while (OPEN_ATOMCHAT_KEY.wasPressed()) {
        if (client.currentScreen == null) {
            client.setScreen(new AtomChatScreen("", AtomChatOpenMode.RESTORE));
        }
    }
});
```

- [ ] **Step 3: Change the chat-screen mixin to pass DIRECT_WORLD**

```java
client.setScreen(new AtomChatScreen(text, AtomChatOpenMode.DIRECT_WORLD));
```

- [ ] **Step 4: Add open mode and navigation field to AtomChatScreen**

Near the existing fields:

```java
public enum AtomChatOpenMode { DIRECT_WORLD, RESTORE }

private final NavigationStack<AppPage> navigation;
```

Add constructors:

```java
public AtomChatScreen(String originalChatText) {
    this(originalChatText, AtomChatOpenMode.DIRECT_WORLD);
}

public AtomChatScreen(String originalChatText, AtomChatOpenMode mode) {
    super(originalChatText);
    this.originalChatText = originalChatText;
    this.navigation = new NavigationStack<>(AppPage.CHAT_LIST);
    if (mode == AtomChatOpenMode.DIRECT_WORLD) {
        navigation.replaceWithRoot(AppPage.CHAT_LIST);
        navigation.push(AppPage.WORLD_CHAT);
    } else {
        List<AppPage> saved = AtomChatState.snapshot();
        navigation.replaceWithRoot(saved.get(0));
        for (int i = 1; i < saved.size(); i++) {
            navigation.push(saved.get(i));
        }
    }
}
```

- [ ] **Step 5: Save state on screen close**

In `removed()` (or at the point where the screen is closing), add:

```java
AtomChatState.save(navigation.snapshot());
```

- [ ] **Step 6: Compile and run the pure tests**

Run: `./gradlew.bat compileJava --console=plain` then `./gradlew.bat test --console=plain`
Expected: compile passes; tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/atom/chat/AtomChatClient.java src/main/java/com/atom/chat/mixin/MinecraftClientMixin.java src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java src/main/resources/assets/atomchat/lang
git commit -m "Add AtomChat open key and navigation entry modes"
```

---

### Task 4: Shell dispatch, bottom tabs, and placeholder pages

**Files:**
- Create: `src/main/java/com/atom/chat/page/PageHost.java`
- Create: `src/main/java/com/atom/chat/page/PlaceholderPage.java`
- Modify: `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`
- Modify: `src/main/resources/assets/atomchat/lang/zh_cn.json`
- Modify: `src/main/resources/assets/atomchat/lang/en_us.json`

**Interfaces:**
- Produces:
  - `interface PageHost { void pushPage(AppPage page); void popPage(); void switchRoot(AppPage root); }`
  - `class PlaceholderPage { PlaceholderPage(AppPage page); void render(Canvas canvas, UiLayout layout); void mouseClicked(float vmx, float vmy); }`
  - SVG icons in `AtomChatScreen`: `ICON_TAB_CHAT_PATH`, `ICON_TAB_PROFILE_PATH`, `ICON_TAB_SETTINGS_PATH`, `ICON_BACK_PATH`.

- [ ] **Step 1: Add page-related language keys**

zh_cn:

```json
"atomchat.tab.chat": "聊天",
"atomchat.tab.profile": "个人",
"atomchat.tab.settings": "设置",
"atomchat.page.chat.placeholder": "会话列表（开发中）",
"atomchat.page.profile.placeholder": "个人档案（开发中）",
"atomchat.page.settings.placeholder": "设置（开发中）"
```

en_us:

```json
"atomchat.tab.chat": "Chat",
"atomchat.tab.profile": "Profile",
"atomchat.tab.settings": "Settings",
"atomchat.page.chat.placeholder": "Chat list (coming soon)",
"atomchat.page.profile.placeholder": "Profile (coming soon)",
"atomchat.page.settings.placeholder": "Settings (coming soon)"
```

- [ ] **Step 2: Add the page host and placeholder page**

`PageHost.java`:

```java
package com.atom.chat.page;

import com.atom.chat.nav.AppPage;

public interface PageHost {
    void pushPage(AppPage page);
    void popPage();
    void switchRoot(AppPage root);
}
```

`PlaceholderPage.java`:

```java
package com.atom.chat.page;

import com.atom.chat.font.FontManager;
import com.atom.chat.nav.AppPage;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import net.minecraft.text.Text;

public final class PlaceholderPage {
    private final AppPage page;

    public PlaceholderPage(AppPage page) {
        this.page = page;
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    public void render(Canvas canvas, UiLayout layout) {
        SkiaDraw.drawRoundedRect(canvas, layout.header.x(), layout.header.y(),
                layout.header.w(), layout.header.h(), UiTokens.HEADER_RADIUS,
                Color.makeARGB(60, 255, 255, 255));
        String title = switch (page) {
            case CHAT_LIST -> tr("atomchat.tab.chat");
            case PROFILE -> tr("atomchat.tab.profile");
            case SETTINGS -> tr("atomchat.tab.settings");
            case WORLD_CHAT -> tr("atomchat.tab.chat");
        };
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, title,
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.y() + layout.header.h() / 2.0F,
                Color.makeARGB(255, 255, 255, 255));
        String placeholder = switch (page) {
            case CHAT_LIST -> tr("atomchat.page.chat.placeholder");
            case PROFILE -> tr("atomchat.page.profile.placeholder");
            case SETTINGS -> tr("atomchat.page.settings.placeholder");
            case WORLD_CHAT -> tr("atomchat.page.chat.placeholder");
        };
        Font bodyFont = FontManager.font(UiTokens.FONT_BODY);
        SkiaFontRenderer.drawTextCentered(canvas, bodyFont, placeholder,
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.bottom() + layout.list.h() / 2.0F,
                Color.makeARGB(220, 170, 170, 186));
    }

    public void mouseClicked(float vmx, float vmy) {
        // Placeholder pages have no interactive rows yet.
    }
}
```

- [ ] **Step 3: Add the SVG icon constants to AtomChatScreen**

Use 20x20 line-icon paths:

```java
private static final String ICON_TAB_CHAT_SVG = "M4 3 L16 3 L16 13 L10 13 L6 17 L7 13 L4 13 Z";
private static final String ICON_TAB_PROFILE_SVG = "M10 3 a3.5 3.5 0 1 1 0 7 a3.5 3.5 0 1 1 0 -7"
        + " M4 17 C4 13.5 6.5 11.5 10 11.5 C13.5 11.5 16 13.5 16 17";
private static final String ICON_TAB_SETTINGS_SVG = "M10 6.5 a3.5 3.5 0 1 0 0 7 a3.5 3.5 0 1 0 0 -7"
        + " M10 2.5 v2 M10 15.5 v2 M3.5 10 h2 M14.5 10 h2"
        + " M5.3 5.3 l1.4 1.4 M13.3 13.3 l1.4 1.4 M14.7 5.3 l-1.4 1.4 M6.7 13.3 l-1.4 1.4";
private static final String ICON_BACK_SVG = "M4 10 L10 4 M4 10 L10 16 M4 10 L18 10";
```

Then create the matching `Path` constants with `Path.makeFromSVGString`.

- [ ] **Step 4: Add shell dispatch methods to AtomChatScreen**

Add a `topPage()` helper:

```java
private AppPage topPage() {
    return navigation.peek();
}

private boolean isWorldChatPage() {
    return topPage() == AppPage.WORLD_CHAT;
}

private void pushPage(AppPage page) {
    navigation.push(page);
}

private void popPage() {
    navigation.pop();
}

private void switchRoot(AppPage root) {
    navigation.replaceWithRoot(root);
}
```

Implement `PageHost` on `AtomChatScreen`:

```java
public final class AtomChatScreen extends ChatScreen implements PageHost {
    // existing code...
    @Override
    public void pushPage(AppPage page) { navigation.push(page); }
    @Override
    public void popPage() { navigation.pop(); }
    @Override
    public void switchRoot(AppPage root) { navigation.replaceWithRoot(root); }
}
```

Add fields for the placeholder pages (Task 5 replaces the chat placeholder
with the real conversation list):

```java
private final PlaceholderPage chatPlaceholderPage = new PlaceholderPage(AppPage.CHAT_LIST);
private final PlaceholderPage profilePage = new PlaceholderPage(AppPage.PROFILE);
private final PlaceholderPage settingsPage = new PlaceholderPage(AppPage.SETTINGS);
```

- [ ] **Step 5: Branch in drawPanel**

At the top of `drawPanel`, after drawing the panel background and before the
existing world-chat header block:

```java
if (!isWorldChatPage()) {
    UiLayout root = rootLayout();
    drawRootPage(canvas, root);
    drawBottomTabBar(canvas, root);
    drawBezel(canvas, layout);
    return;
}
```

Refactor the existing white bezel at the end of `drawPanel` into
`drawBezel(Canvas canvas, UiLayout layout)` so root and world pages share it.
The world-chat path then continues exactly as today.

- [ ] **Step 6: Implement root page drawing and bottom tab bar**

Add methods that delegate root-page drawing to the page classes (each page
draws its own root header) and draw the three SVG tab icons with labels at the
bottom. Use `UiLayout.ofRoot(...)` when building root layouts:

```java
private UiLayout rootLayout() {
    return UiLayout.ofRoot(panelX(), panelY(), panelWidth(), panelHeight());
}

private void drawRootPage(Canvas canvas, UiLayout layout) {
    if (topPage() == AppPage.CHAT_LIST) {
        chatPlaceholderPage.render(canvas, layout);
    } else if (topPage() == AppPage.PROFILE) {
        profilePage.render(canvas, layout);
    } else if (topPage() == AppPage.SETTINGS) {
        settingsPage.render(canvas, layout);
    }
}

private void drawBottomTabBar(Canvas canvas, UiLayout layout) {
    UiLayout.Rect bar = layout.tabBar;
    if (bar.w() <= 0.0F) {
        return;
    }
    SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), s(18),
            Color.makeARGB(60, 255, 255, 255));
    Font tabFont = FontManager.font(UiTokens.FONT_QUOTE);
    AppPage[] roots = {AppPage.CHAT_LIST, AppPage.PROFILE, AppPage.SETTINGS};
    io.github.humbleui.skija.Path[] icons = {
            ICON_TAB_CHAT_PATH, ICON_TAB_PROFILE_PATH, ICON_TAB_SETTINGS_PATH
    };
    String[] labels = {
            tr("atomchat.tab.chat"), tr("atomchat.tab.profile"), tr("atomchat.tab.settings")
    };
    for (int i = 0; i < 3; i++) {
        float cellCenterX = bar.x() + bar.w() * (i + 0.5F) / 3.0F;
        drawIconCentered(canvas, icons[i], cellCenterX, bar.y() + s(18), s(20), textPrimary());
        SkiaFontRenderer.drawTextCentered(canvas, tabFont, labels[i], cellCenterX,
                bar.y() + bar.h() - s(12), textPrimary());
    }
}
```

- [ ] **Step 7: Route mouse clicks for root pages**

In `mouseClicked`, after existing world-chat-only blocks (which must be guarded
by `isWorldChatPage()`), add:

```java
if (!isWorldChatPage()) {
    if (handleBottomTabClick(mx, my)) {
        return true;
    }
    if (topPage() == AppPage.PROFILE || topPage() == AppPage.SETTINGS) {
        // Placeholder pages consume clicks inside the panel.
        return true;
    }
}
return super.mouseClicked(mouseX, mouseY, button);
```

Add `handleBottomTabClick` to detect the three equal-width tab cells inside
`UiLayout.ofRoot(...).tabBar`, call `switchRoot(...)`, and return true.

- [ ] **Step 8: Compile and test**

Run: `./gradlew.bat compileJava --console=plain` then `./gradlew.bat test --console=plain`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/atom/chat/page src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java src/main/resources/assets/atomchat/lang
git commit -m "Add shell page dispatch, placeholder pages and bottom tab bar"
```

---

### Task 5: Conversation list page

**Files:**
- Create: `src/main/java/com/atom/chat/page/ConversationListPage.java`
- Modify: `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`
- Modify: `src/main/resources/assets/atomchat/lang/zh_cn.json`
- Modify: `src/main/resources/assets/atomchat/lang/en_us.json`

**Interfaces:**
- Consumes: `PageHost`, `UiLayout`, `AppPage.WORLD_CHAT`.
- Produces:
  - `class ConversationListPage`
    - `ConversationListPage(PageHost host)`
    - `void render(Canvas canvas, UiLayout layout)`
    - `boolean mouseClicked(float vmx, float vmy, UiLayout layout)`

- [ ] **Step 1: Add language keys**

zh_cn:

```json
"atomchat.conversation.world": "世界频道",
"atomchat.conversation.world.subtitle": "点击进入公共聊天"
```

en_us:

```json
"atomchat.conversation.world": "World Channel",
"atomchat.conversation.world.subtitle": "Tap to open the public chat"
```

- [ ] **Step 2: Implement ConversationListPage**

`ConversationListPage.java`:

```java
package com.atom.chat.page;

import com.atom.chat.font.FontManager;
import com.atom.chat.nav.AppPage;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import net.minecraft.text.Text;

public final class ConversationListPage {
    private static final float ROW_H = UiTokens.s(64);
    private static final float ROW_GAP = UiTokens.s(10);

    private final PageHost host;

    public ConversationListPage(PageHost host) {
        this.host = host;
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    private UiLayout.Rect rowRect(UiLayout layout) {
        float rowX = layout.list.x();
        float rowY = layout.header.bottom() + UiTokens.PANEL_TOP_GAP + ROW_GAP;
        return new UiLayout.Rect(rowX, rowY, layout.list.w(), ROW_H);
    }

    public void render(Canvas canvas, UiLayout layout) {
        SkiaDraw.drawRoundedRect(canvas, layout.header.x(), layout.header.y(),
                layout.header.w(), layout.header.h(), UiTokens.HEADER_RADIUS,
                Color.makeARGB(60, 255, 255, 255));
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, tr("atomchat.tab.chat"),
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.y() + layout.header.h() / 2.0F,
                Color.makeARGB(255, 255, 255, 255));

        UiLayout.Rect row = rowRect(layout);
        SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                s(12), Color.makeARGB(60, 255, 255, 255));

        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        Font subFont = FontManager.font(UiTokens.FONT_QUOTE);
        float textX = row.x() + s(18);
        float nameCenterY = row.y() + row.h() / 2.0F - s(8);
        float subCenterY = row.y() + row.h() / 2.0F + s(12);
        SkiaFontRenderer.drawText(canvas, nameFont, tr("atomchat.conversation.world"), textX,
                SkiaFontRenderer.centerBaselineY(nameFont, nameCenterY),
                Color.makeARGB(255, 255, 255, 255));
        SkiaFontRenderer.drawText(canvas, subFont, tr("atomchat.conversation.world.subtitle"), textX,
                SkiaFontRenderer.centerBaselineY(subFont, subCenterY),
                Color.makeARGB(220, 170, 170, 186));
    }

    public boolean mouseClicked(float vmx, float vmy, UiLayout layout) {
        if (rowRect(layout).contains(vmx, vmy)) {
            host.pushPage(AppPage.WORLD_CHAT);
            return true;
        }
        return false;
    }
}
```

The shell calls:

```java
if (topPage() == AppPage.CHAT_LIST && conversationListPage.mouseClicked(mx, my, rootLayout())) {
    return true;
}
```

- [ ] **Step 3: Wire ConversationListPage into the screen**

Remove the Task 4 chat placeholder field and replace it with the real page:

```java
private final ConversationListPage conversationListPage = new ConversationListPage(this);
```

In `drawRootPage`, change the `CHAT_LIST` branch to call
`conversationListPage.render(canvas, layout)` instead of
`chatPlaceholderPage.render(...)`.

In the root click path, call:

```java
if (topPage() == AppPage.CHAT_LIST && conversationListPage.mouseClicked(mx, my, rootLayout())) {
    return true;
}
```

- [ ] **Step 4: Compile and test**

Run: `./gradlew.bat compileJava --console=plain` then `./gradlew.bat test --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/atom/chat/page/ConversationListPage.java src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java src/main/resources/assets/atomchat/lang
git commit -m "Add conversation list root page with world channel entry"
```

---

### Task 6: World-chat detail page wrapper and SVG back arrow

**Files:**
- Modify: `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`
- Modify: `src/main/resources/assets/atomchat/lang/zh_cn.json`
- Modify: `src/main/resources/assets/atomchat/lang/en_us.json`

**Interfaces:**
- Consumes: `AppPage.WORLD_CHAT`, `navigation`, `ICON_BACK_PATH`.
- Produces:
  - `float backButtonX() / backButtonY() / backButtonSize()` (or one `UiLayout.Rect backButton`)
  - `boolean isBackButtonHit(float vmx, float vmy)`

- [ ] **Step 1: Add accessibility/localized back label if needed**

zh_cn:

```json
"atomchat.back": "返回"
```

en_us:

```json
"atomchat.back": "Back"
```

- [ ] **Step 2: Add the back button geometry**

Add a fixed hit rect in the world-chat header, aligned to the left edge:

```java
private boolean isBackButtonHit(float vmx, float vmy) {
    float size = s(36);
    UiLayout.Rect header = layout().header;
    return vmx >= header.x() + s(4) && vmx <= header.x() + s(4) + size
            && vmy >= header.y() + (header.h() - size) / 2.0F
            && vmy <= header.y() + (header.h() - size) / 2.0F + size;
}
```

- [ ] **Step 3: Draw the back arrow in the world-chat header**

In the world-chat header block, before the centered title:

```java
if (isWorldChatPage()) {
    drawIconCentered(canvas, ICON_BACK_PATH,
            header.x() + s(4) + s(18),
            header.y() + header.h() / 2.0F,
            s(18), textPrimary());
}
```

The centered title still says `World Channel` / `世界频道`. The right time
stays unchanged.

- [ ] **Step 4: Handle back clicks before other world-chat hits**

In `mouseClicked`, inside the world-chat branch, before message/emoji/context
handling:

```java
if (isWorldChatPage() && button == 0 && isBackButtonHit(mx, my)) {
    popPage();
    return true;
}
```

- [ ] **Step 5: Ensure world-chat-only UI is gated**

Review `mouseClicked`, `mouseDragged`, `mouseReleased`, `mouseScrolled`, and
`charTyped`/`keyPressed` paths. Any handler that manipulates input, emoji,
reply bar, context menu, or the message list must early-return or skip when
`!isWorldChatPage()`.

- [ ] **Step 6: Compile and test**

Run: `./gradlew.bat compileJava --console=plain` then `./gradlew.bat test --console=plain`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java src/main/resources/assets/atomchat/lang
git commit -m "Add world chat detail page mode with SVG back button"
```

---

### Task 7: Full build, deploy, and manual verification

**Files:** none new (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew.bat test --console=plain`
Expected: all tests green.

- [ ] **Step 2: Build the jar**

Run: `./gradlew.bat build --console=plain`
Expected: `build/libs/atomchat-Fabric-1.21.1-0.1.3.jar` exists.

- [ ] **Step 3: Deploy the dev jar**

```bash
cp build/libs/atomchat-Fabric-1.21.1-0.1.3.jar /d/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.1.3.jar
md5sum /d/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.1.3.jar
```

- [ ] **Step 4: Manual in-game checklist**

1. Press `T` -> world-channel detail opens directly with a left SVG back arrow.
2. Press back arrow -> conversation-list root page; bottom tab bar appears.
3. Click the `World Channel` row -> world-channel detail opens again.
4. Press `Y` -> restores the last stack from the previous AtomChat close.
5. On a root page, switch between Chat / Profile / Settings; details hide the
   bottom tab bar.
6. Verify no regression: send text, send/paste an image, emoji picker,
   right-click bubble menu, avatar poke, scroll.

- [ ] **Step 5: Report results and commit any fixes**

If verification passes, summarize. If fixes are needed, create small follow-up
commits with English messages and re-run the checklist.

---

## Self-Review

- Spec coverage: all four design sections map to Task 1-7; dual T/Y entry is in
  Task 3; bottom tabs/placeholders in Task 4; conversation list in Task 5;
  world-chat wrapper/back in Task 6; tests/order in Task 7.
- Placeholder scan: the only intentionally scaffolded class is
  `PlaceholderPage`, whose actual drawing is explicitly tied to existing
  AtomChatScreen helpers; no vague TODO steps remain in later tasks.
- Type consistency: `NavigationStack<T>`, `AppPage`, `AtomChatState`,
  `PageHost.pushPage/popPage/switchRoot` are used with the same names across
  tasks.
