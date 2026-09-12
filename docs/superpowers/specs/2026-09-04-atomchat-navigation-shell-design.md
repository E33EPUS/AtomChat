# AtomChat Navigation Shell Design

Date: 2026-09-04
Status: Approved for spec review
Branch target: `Fabric-1.21.1`

## Goal

Turn AtomChat from a single world-chat screen into a phone-app style shell with
an in-panel page stack. This first subproject builds the shell and the
conversation-list root page only; private chat, profile data and real settings
are intentionally out of scope.

## User Decisions (captured)

- Use an in-panel page stack, not a separate Minecraft Screen.
- Root page is the conversation list under the Chat tab (QQ-style).
- Bottom tab bar: `Chat` / `Profile` / `Settings`, SVG icons.
- Bottom tab bar is visible only on root pages; detail pages hide it.
- Page restoration is in-memory only for this round (game-session restore, no disk).
- Dual entry model:
  - Chat key (default `T`) opens the world-channel chat directly.
  - New AtomChat key (default `Y`) opens the saved/inherited page stack.
- Architecture route: navigation shell + new page classes; existing world-chat
  rendering stays inside `AtomChatScreen` for now, wrapped as the `WorldChat` page.

## Page & Navigation Model

```
AtomChatScreen (app shell)
├─ NavigationStack<Page>
├─ bottom tabs: Chat / Profile / Settings (root pages only, SVG icons)
└─ pages in v1
   ├─ ChatList      — conversation-list root page
   ├─ Profile       — placeholder root page
   ├─ Settings      — placeholder root page
   └─ WorldChat     — world-channel detail page (existing chat UI)
```

Rules:

- `T`: if the shell is not open, initialize as `[ChatList, WorldChat]`; if the
  shell is already open, navigate to `WorldChat`.
- `Y`: restore the last saved stack; default when no history is `[ChatList]`.
- Conversation list has a `World Channel` row; clicking it pushes `WorldChat`.
- `WorldChat` shows a left back-arrow (SVG); clicking it pops back to the
  conversation list.
- Bottom tab bar is drawn only when `stack.size() == 1`.
- Closing the screen saves the complete stack in memory.
- A full Minecraft restart clears the saved state back to `[ChatList]`.

## Page State Model

- `Page` is pure data:
  - root pages: `ChatList`, `Profile`, `Settings`
  - detail page: `WorldChat`
  - future pages (not now): `PrivateChat(playerId, name)`, etc.
- `NavigationStack<T>` is a pure Java class:
  - `push(page)`, `pop()`, `peek()`, `size()`, `snapshot()`
  - never allows an empty stack or popping the last root page.
- `AtomChatState` is a static in-memory holder:
  - stores `List<Page> stack`
  - written on screen close / `removed()`
  - read by the `Y` entry path.
- The bottom tab is derived from the stack root, so no separate `selectedTab`
  field is needed.

## Rendering & Input Dispatch

- Background/panel/bezel rendering stays unchanged.
- After the background is drawn, dispatch on the top page:
  - `ChatList` -> `ConversationListPage`
  - `Profile` -> placeholder page
  - `Settings` -> placeholder page
  - `WorldChat` -> existing world-chat drawing (messages, input, emoji panel,
    context menu, poke, etc.)
- When `stack.size() == 1`, draw the bottom tab bar last (before bezel).
- The world-chat header becomes: left back arrow SVG, centered title
  `World Channel`, right time.
- Click handling:
  1. If top page is `WorldChat`, run existing input/message/emoji/context-menu
     hit testing first.
  2. Then let the current root page handle clicks (e.g. conversation row push).
  3. Back arrow on `WorldChat` takes priority over message hit testing.
  4. Tab clicks only work on root pages and replace the stack with the chosen
     root page.

## Engineering Boundaries

- Add independent page classes:
  - `ConversationListPage`
  - `ProfilePlaceholderPage`
  - `SettingsPlaceholderPage`
  - A small host callback interface so pages can request `push` / `pop`.
- Keep the existing world-chat code inside `AtomChatScreen` for this round.
  Gate it by `topPage() == WorldChat`.
- Extend `UiLayout` with geometry for:
  - root content area (when no input bar exists)
  - bottom tab bar
- Page transitions: v1 may use simple fade/slide; polish after the stack is stable.

## Out of Scope for This Subproject

- Real private chat message capture / whisper sidebar / conversation list rows.
- Profile page content beyond placeholder.
- Settings page content beyond placeholder.
- Disk persistence of page state.
- Full extraction of world-chat logic into a standalone page class.

## Testing

Pure unit tests:

- `NavigationStackTest`
  - push / pop / peek / size / snapshot
  - pop on a single root page is a no-op or throws a domain rule violation
- `AppPageTest`
  - root vs detail classification
  - page serialization/deserialization used by in-memory restore
- `AtomChatStateTest`
  - default stack is `[ChatList]`
  - save/restore round-trip preserves stack

Manual in-game verification:

1. Press `T` -> world channel detail opens directly with a back arrow.
2. Press back arrow -> conversation list root appears; bottom tab bar appears.
3. Click the `World Channel` row -> world channel detail reopens.
4. Press `Y` -> restores the last stack.
5. Switch bottom tabs on root pages to Profile / Settings placeholders.
6. Detail pages hide the bottom tab bar.
7. Existing world-chat features do not regress (send message, images, emoji,
   context menu, avatar poke, scroll).

## Implementation Order

1. Pure model: `AppPage` + `NavigationStack` + `AtomChatState`, with JUnit tests.
2. Register the new keybinding (default `Y`) and implement `T` / `Y` entry logic.
3. Add the shell dispatch in `AtomChatScreen`, bottom tab bar, and Profile /
   Settings placeholder pages.
4. Wrap the existing world-chat drawing/interaction as the `WorldChat` page and
   add the SVG back arrow.
5. Add `ConversationListPage` with the `World Channel` row.
6. Run `./gradlew test && build`, deploy the dev jar, and hand over for in-game
   verification.
