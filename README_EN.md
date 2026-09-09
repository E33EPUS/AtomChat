[简体中文](README.md) | [English](README_EN.md)

<h1 align="center"><img width="256" height="256" alt="logo" src="https://github.com/user-attachments/assets/e449c62e-644b-4c19-9fbc-431d7a899781" /></h1>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>A phone-app style chat experience for Minecraft, powered by Skia.</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-NeoForge-blue">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-21%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Fabric Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=Fabric-1.21.1"></a>
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="NeoForge Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=NeoForge-1.21.1"></a>
</p>

AtomChat is a brand-new chat beautification mod developed in the spirit of [E33Chat](https://github.com/E33EPUS/E33Chat). It turns the vanilla chat screen into a standalone "phone app" style chat panel: rounded bubbles, custom avatars, real player names, emoji / kaomoji / sticker packs, image messages, copy and quote, multi-line input, QQ-style motion, and more screens beyond chat.

Rendering uses [Skija](https://github.com/HumbleUI/skija). The whole UI is vector-drawn instead of using vanilla chat textures.

> Status: **v0.2.5 implemented and released (Fabric / NeoForge 1.21.1)**. Download from [Releases](https://github.com/E33EPUS/AtomChat/releases); this is an intentional clean rewrite in the spirit of E33Chat, not a fork.

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Vanilla Improvements](#vanilla-improvements)
- [Features](#features)
- [Usage](#usage)
- [Configuration & Storage](#configuration--storage)
- [Compatibility](#compatibility)
- [Known Limitations](#known-limitations)
- [Privacy & Data](#privacy--data)
- [FAQ](#faq)
- [Feedback](#feedback)
- [Development & Building](#development--building)
- [Changelog](#changelog)
- [Third-Party Licenses](#third-party-licenses)
- [License](#license)

---

## Installation

| Dependency | Type | Notes |
|---|---|---|
| Minecraft | Required | 1.21.1 |
| Fabric Loader | Required (Fabric build) | 0.16.0+ |
| Fabric API | Required (Fabric build) | any 1.21.1 compatible version |
| NeoForge | Required (NeoForge build) | 21.1.235 |
| Java | Required | 21+ |

1. Download the latest JAR from [Releases](https://github.com/E33EPUS/AtomChat/releases), or build it yourself under [Development & Building](#development--building)
2. Put the JAR in `.minecraft/mods/`
3. Launch the game and press the chat key (default `T` / `/`) to open AtomChat

---

## Quick Start

1. Open chat to see the phone panel: "Public" and the system time at the top, the message list in the middle, and the composer at the bottom
2. Type text and press Enter to send; the composer grows when the text wraps and still supports Up/Down caret movement
3. Click the **image icon** to pick a local image, or **drag an image into the window / Ctrl+V** to paste it; the upload is inserted into the draft automatically
4. Click the **emoji icon** to open the panel with `Emoji` / `Kaomoji` / `Stickers` tabs
5. Right-click any message to **Copy** or **Quote**; single-click an avatar to open the player's profile page, double-click it for a QQ-style poke shake, and right-click it for the menu (@ mention / whisper / teleport / block)

---

## Vanilla Improvements

- 💬 **Chat bubbles & avatars** — your messages sit on the right, everyone else's on the left, with real player names and round skin avatars
- 🧹 **Compact vanilla HUD placeholders** — outside the panel, image codes show as green `[Image]` and quotes as blue `[Quote]`
- 📝 **Multi-line input** — the composer shows up to two lines, then scrolls internally; Up/Down move between lines while single-line drafts keep the vanilla chat history
- 🧠 **Message capture** — real player identity is captured from MessageHandler's three channels; unknown messages degrade to system text
- 📑 **Chat history** — when enabled, history is saved per server/world on disk, restored on rejoin, and pruned automatically

---

## Features

- 🔔 **Notification banners & sounds** — being @mentioned, quoted or whispered drops a banner at the top of the panel (sender avatar + type + name + content preview); clicking it jumps to that message and highlights it. An original synthesized pop cue plays alongside, and Settings → Chat → Notifications switches each banner/sound pair independently with a volume slider
- 🖼️ **Image messages** — renders `[[CICode]]` natively (interoperable with the E33Chat / ChatImage family), keeps the source aspect ratio, and supports right-click saving
- 📤 **Local image sending** — pick an image with the image button, drag one into the window, or press Ctrl+V; the picker opens in Details view with inline thumbnails
- 😊 **Emoji / Kaomoji / Stickers** — three tabs with a sliding indicator and full-width push transitions; stickers persist locally (png/jpg/jpeg, up to 10)
- ⚡ **Quick phrases** — a lightning button opens a phrase list above the composer; tapping inserts into the input (never sends on its own), with add / inline-edit / delete, capped at 256 characters and 20 entries
- 🔗 **Rich-text messages** — colours, underlines, clicks and hover tooltips: `/tell`, coordinates, FTB accept/deny and external links are all clickable; bare URLs become links automatically
- 📋 **Copy / Quote / Save** — right-click a bubble for the menu; image bubbles additionally offer Save
- 🧭 **@ / whisper / teleport / block** — right-click an avatar for @ mention, whisper, quick teleport and block; a single click opens the player's profile
- 🪢 **Private chat** — the chat list shows currently online players; click one to open the conversation, right-click for its menu (profile / teleport / block)
- 👥 **Online players & conversation list** — ordered Public → online players → recent offline, with real IDs, skin avatars, online/offline dots and unread badges; private chat uses `/msg`, keeps per-conversation drafts and scroll state, and is read-only for offline or blocked players
- 🖥️ **Player profile page** — push into a player's profile from chat or the conversation list: stat overview, copy buttons, role tag, with a full-width push transition
- 🎨 **Skia vector UI & motion** — rounded bubbles, blurred backgrounds, scrolling and QQ-style slide-in animations are all vector-drawn
- 🧭 **Phone-style navigation** — three bottom tabs (Chats / You / Settings) with page-level push/pop transitions; the same animation carries public ↔ private switches
- 📱 **Custom wallpaper / avatar** — upload a local image, crop it, and use it as the panel background or your avatar; on servers also running AtomChat your avatar syncs to every player automatically
- 🚫 **Block management** — a visual block list in settings (avatar + name + one-tap unblock); "Hide blocked players' messages" can be turned off so they stay visible in public chat while private stays blocked
- ⚙️ **Highly configurable** — the in-game settings page (tile home: Appearance / Chat / Privacy & blocking / About) covers appearance, colours and preferences, all applying and persisting instantly
- 🎛️ **Adjustable options** — background blur, interface animations (decorative-motion master), background opacity, panel width (400–600), interface scale (x0.75–x1.50, rescales the whole UI live), message entrance animation, double-tap avatar poke
- 🎨 **Themes & full colour control** — one-tap theme presets (Frosted / Modern); the settings page exposes every interface colour (panel background / bubbles / secondary capsules / text / cards / outline / accent), with foldable colour groups and a live preview square on each row
- 🌍 **Localization** — supports Simplified Chinese and English; switch the game language to apply
- 🛡️ **Anti-spam & compact groups** — consecutive identical messages merge into one with a counter; same-sender five-minute runs keep the avatar/name only on the first row and tighten the gap
- ⏱️ **Time dividers & cross-message selection** — the first message of a list always shows a timestamp capsule, later ones follow a configurable interval; drag across several messages to copy them all with Ctrl+C
- 🌐 **Other toggles** — image receive toggle (off shows a green `[Image]` placeholder), teleport mode cycle (`/tp` / `/tpa` / `auto`), IMBlocker command-mode bridge and WATUT "partner is typing" indicator
- 🛠️ **Pure Skia rendering** — rounded corners, shadows, scrolling and text are vector-drawn; pure animation / layout / token classes ship with JUnit tests

---

## Usage

### Chat & Messages

- Own bubbles sit right, other bubbles left; names hug the bubble edge and avatars align to the bubble top
- Single-click an avatar: open the player's profile detail page (a second click within 300 ms becomes a poke)
- Double-click an avatar: trigger the QQ-style poke shake
- Right-click a bubble: `Copy` / `Quote`; image bubbles also show `Save` to download the original file
- Messages containing `[[CICode,url=...,name=...,w=...,h=...]]` render as image bubbles; older size-less codes are also supported

### Sending Images

- Click the image button → FlatLaf picker (opens the `Pictures` folder by default, with a thumbnail preview)
- Drag an image file into the game window, or copy an image and press `Ctrl+V`: it uploads and inserts into the draft
- While uploading, the composer placeholder reads "Uploading image…"; press Enter to send after it finishes
- The default image host is uguu.se; links expire after about 3 hours. There is no server-side media hosting yet

### Sticker Packs

- Folder: `.minecraft/config/atomchat/emotes/`
- Supports png / jpg / jpeg, sorted by file name, up to 10 images
- In the `Stickers` tab, click the trailing `+` to add; hover a thumbnail and click `×` to remove
- Clicking a sticker uploads it, inserts the code into the draft, and closes the panel (one per tap)

### Language

- AtomChat uses Minecraft language files: `assets/atomchat/lang/zh_cn.json` and `en_us.json`
- After switching the game language, the title, tabs, context menu, input placeholder and file-picker text follow automatically

---

## Configuration & Storage

**Path** — `.minecraft/config/atomchat/` holds `atomchat-client.json` plus the data folders:

| Path | Contents |
|---|---|
| `atomchat-client.json` | Main config file (generated on first launch) |
| `avatar/` | Custom avatars (256px PNG) |
| `emotes/` | Sticker packs (png / jpg / jpeg, up to 10) |
| `wallpaper/` | Custom wallpaper |
| `history/` | Chat history (per world / server) |
| `debug/` | Avatar sampling PNGs written in debug mode |

Auto-downloaded chat images and companion avatar data live under `<gameDir>/atomchat-data/` (capped at 500 files / 100 MB, viewable and clearable from Settings → About).

**Recommended**: the in-game `Settings` page (a Windows 11 style tile grid: Appearance / Chat / Privacy & blocking / About). Every option **applies and persists instantly, no restart**; colour rows ship with preset palettes, a `+` custom picker and live previews.

**Advanced**: edit `atomchat-client.json` directly (restart the game after manual edits). Common keys:

| Key | Default | Description |
|---|---|---|
| `panelWidth` / `panelHeight` | `440.0` / `780.0` | Panel size in design pixels (further scaled by the UI scale) |
| `panelOpacity` | `0.93` | Panel background opacity |
| `uiScale` | `1.0` | Interface scale (x0.75–x1.50) |
| `blurEnabled` | `true` | Background blur (mutually exclusive with wallpaper) |
| `animationEnabled` / `messageEntryAnimation` / `avatarPokeEnabled` | `true` | Master animation switch / message entrances / avatar poke |
| `themeName` | — | Theme preset (frosted / modern) |
| `accentColor` | `0xFF4A90E2` | Accent color (send button, quote bar, scrollbar, etc.) |
| `ownBubbleColor` / `bubbleTextColor` | `0xFF1E90FF` / `0xFFFFFFFF` | Own bubble background / text |
| `otherBubbleColor` / `otherBubbleTextColor` | `0xFF2C3E50` / `0xFFFFFFFF` | Other bubble background / text |
| `secondaryCapsuleBg` / `secondaryCapsuleText` | `0x962C3E50` / `0xDCAAAABA` | Secondary capsule background / text (system messages, time dividers, quote pills) |
| `textPrimaryColor` / `textSecondaryColor` | `0xFFFFFFFF` / `0xDCAAAABA` | Primary / secondary interface text |
| `panelBgColor` / `panelOutlineColor` / `panelOutline` | `0xEE16191F` / `0xFFFFFFFF` / `true` | Panel background / outline color / outline toggle |
| `timestampIntervalMinutes` | `5` | Time divider interval (0 = off) |
| `imageMessagesEnabled` | `true` | Image message receive toggle |
| `antiSpamEnabled` / `compactMessagesEnabled` | `true` / `false` | Anti-spam merging / compact message groups (off by default) |
| `chatHistoryEnabled` / `historyRetentionDays` | `false` / `7` | Keep chat history / retention days (0 = forever, defaults to 7 days) |
| `teleportCommandMode` | `"auto"` | Teleport command mode (auto / tp / tpa) |
| `debug` | `false` | Debug logging / avatar sampling PNGs (written to `config/atomchat/debug/`) |

---

## Compatibility

| Item | Status |
|---|---|
| Fabric 1.21.1 | ✅ Supported |
| NeoForge 1.21.1 | ✅ Supported |
| Java 21+ | ✅ Required |
| Server | ✅ Not required (client-only); installing it enables custom-avatar sync |
| `[[CICode]]` image protocol | ✅ Interoperable with E33Chat / ChatImage family |
| Nickname / display-name plugins | 🟡 Best effort (tell-click / Tab names / decorated-name structure); unknown formats fall back to gray system text |
| ChatHeads | ✅ Compatible |
| Chat Animation / similar animation mods | ✅ Compatible |
| EasyBot | 🟡 Best effort (QQ message type parsing); unparseable lines fall back to gray text |
| Quark | 🚫 Emote menu is not shown; compatibility work in progress |
| Other loaders / versions | ❌ Fabric / NeoForge 1.21.1 only for now |

---

## Known Limitations

1. Fabric / NeoForge 1.21.1 supported so far; the Skija Windows x64 native is bundled. Linux / macOS packages are not built yet (the mod cannot run there)
2. Images upload to the third-party host uguu.se by default (~3 hour expiry); no server-side media hosting yet (planned)
3. No E33Chat whisper sidebar or search yet; server-format templates are supported (hand-edit `chatTemplates` / `whisperTemplates`), and per-world chat-history persistence is built in (off by default)
4. Player identity is best effort: tell-click structured capture, offline seen cache, and multi-tier ownDisplayName fallbacks; extreme unknown formats fall back to gray system text
5. Chat-history persistence is off by default; when enabled, history is stored per server/world on disk and restored on rejoin, without leaking across worlds/servers

---

## Privacy & Data

> [!WARNING]
> Local images you send are uploaded to a third-party image host (uguu.se by default) and can be saved by others. Your messages can also be saved by players who enabled chat history. Do not send sensitive or private content.

- The mod uploads no telemetry or personal information
- Image uploads happen only when you explicitly pick, paste, or drop an image
- Local config and sticker packs stay in `.minecraft/config/atomchat/` and are never synced automatically
- Skin avatar resolution requests Minecraft skin services by player name / UUID, same as vanilla behavior

---

## FAQ

**Do I need a server mod?** No. AtomChat is client-only, but installing it server-side enables custom avatar syncing.

**How do I send an image?** Click the image button to choose a local file, or drag an image into the window / Ctrl+V paste. After the upload finishes it is inserted into the draft; press Enter to send.

**Why is a message shown as gray system text?** When the client cannot be confident a line came from a player, it conservatively renders it as a system message (for example, nickname plugins using unparseable formats).

**Where are sticker packs stored?** `.minecraft/config/atomchat/emotes/`, up to 10 images, png / jpg / jpeg.

**How do I change colors / sizes?** Adjust them live in `Settings → Appearance` (colour rows include presets and a custom picker); alternatively edit `config/atomchat/atomchat-client.json` and restart the game.

**Can I include this in a modpack?** Yes. AtomChat's code is MIT and needs no extra permission; if your modpack redistributes the JAR, keep the third-party notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## Feedback

Found a bug or have a suggestion? Open an [issue](https://github.com/E33EPUS/AtomChat/issues), or leave a comment on the mod listing pages.

---

## Development & Building

```bash
./gradlew.bat build
```

The artifact is at `build/libs/atomchat-Fabric-1.21.1-<version>.jar`.

```bash
./gradlew.bat test
```

Runs JUnit tests for the pure logic layers (animation, layout, message parsing, sticker storage).

Main modules:

- `AtomChatScreen` — Skia-drawn chat panel (orchestration layer)
- `UiLayout` / `UiTokens` / `UiMotion` — layout math, size tokens, animation durations
- `chat/` — E33Chat-inspired message capture / classification / presentation pipeline
- `emote/` — sticker persistence and Skia image cache
- `mixin/` — vanilla chat capture, IME / suggestion integration

---

## Changelog

See [Fabric-1.21.1/CHANGELOG.md](https://github.com/E33EPUS/AtomChat/blob/Fabric-1.21.1/CHANGELOG.md) for the full history.

---

## Third-Party Licenses

AtomChat's own code is released under [MIT](LICENSE), but the distributed JAR bundles third-party components that keep their own licenses:

| Component | License |
|---|---|
| Skija (Java bindings) | Apache License 2.0 |
| HumbleUI types | Apache License 2.0 |
| FlatLaf | Apache License 2.0 |
| Skia (native library) | BSD 3-Clause |

Full copyright and license texts live in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## License

[MIT](LICENSE)

Copyright © 2026 E33EPUS
