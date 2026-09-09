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

AtomChat is a brand-new chat beautification mod developed in the spirit of [E33Chat](https://github.com/E33EPUS/E33Chat). It turns the vanilla chat screen into a standalone "phone app" style chat panel: rounded bubbles, avatars, real player names, emoji / kaomoji / sticker packs, image messages, copy and quote, multi-line input, and QQ-style motion.

Rendering uses [Skija](https://github.com/HumbleUI/skija)

The whole UI is vector-drawn instead of using vanilla chat textures.

> Status: **v0.2.5 implemented and released (Fabric / NeoForge 1.21.1)**. Download from [Releases](https://github.com/E33EPUS/AtomChat/releases); this is an intentional clean rewrite in the spirit of E33Chat, not a fork.

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Usage](#usage)
- [Configuration](#configuration)
- [Compatibility](#compatibility)
- [Known Limitations](#known-limitations)
- [Privacy & Data](#privacy--data)
- [FAQ](#faq)
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

1. Open chat to see the phone panel: "World Channel" and the system time at the top, the message list in the middle, and the composer at the bottom
2. Type text and press Enter to send; the composer grows when the text wraps and supports Up/Down caret movement
3. Click the **image icon** to pick a local image, or **drag an image into the window / Ctrl+V** to paste it; the upload is inserted into the draft automatically
4. Click the **emoji icon** to open the panel with `Emoji` / `Kaomoji` / `Stickers` tabs
5. Right-click any message to **Copy** or **Quote**; single-click an avatar to open the player's profile page, double-click it for a QQ-style poke shake

---

## Features

- 📱 **Phone-style panel** — the vanilla chat HUD is hidden while AtomChat is open; blurred background plus a translucent glass composer
- 💬 **Bubbles & avatars** — own messages on the right, others on the left, with avatar and player name; skin faces resolve from online / offline profiles with fallbacks
- 🖼️ **Custom avatars** — crop any local image into a square avatar shown on your profile and your own bubbles; on servers also running AtomChat it syncs to every player (changes broadcast instantly), silently falling back to skins without the companion
- 🔔 **Notification banners & sounds** — being @mentioned, quoted or whispered drops a banner at the top of the panel (click to jump to the message) with an original synthesized pop cue; each banner/sound pair is individually switchable with a volume slider
- 🖼️ **Image messages** — renders `[[CICode]]` natively (interoperable with E33Chat / ChatImage), keeps the source aspect ratio; placeholder while loading; right-click an image bubble to save the original file
- 📤 **Local image sending** — the FlatLaf picker defaults to Details view with inline thumbnails; supports drag & drop and Ctrl+V paste; uploads are converted into CICode automatically
- 🔗 **Rich-text messages** — player names/bodies support colors, underlines, clicks and hover tooltips: `/tell`, coordinates, FTB accept/deny and external links are clickable; bare URLs become links automatically
- 🧹 **Compact vanilla HUD placeholders** — outside the panel, the vanilla chat shows image codes as green `[Image]` and quotes as blue `[Quote]` instead of raw long codes
- 😀 **Emoji / Kaomoji / Stickers** — three tabs with a sliding indicator and full-width push transitions; stickers persist in `<config>/atomchat/emotes/` (png/jpg/jpeg, max 10), added through the `+` cell and deleted by hovering `×`
- ⚡ **Quick phrases** — a lightning button opens a phrase list above the composer; tapping inserts into the input (never sends on its own), with add / inline-edit / delete, capped at 256 characters and 20 entries
- 📋 **Copy / Quote / Save** — right-click to copy or quote; image messages can be saved; the context menu uses 20×20 SVG line icons
- 🧭 **Phone-style navigation** — three bottom tabs (Chats / You / Settings) with page-level push/pop transitions; the same animation carries public ↔ private switches
- 👥 **Online players & private chat** — the conversation list orders Public → online players → recent offline, with real IDs, skin avatars, online/offline dots and unread badges; private chat uses `/msg`, keeps per-conversation drafts and scroll state, and is read-only for offline or blocked players
- ⚙️ **Settings page** — a Windows 11 style 2×2 tile grid (Appearance / Chat / Privacy & blocking / About) opening into sub-pages; switches use an iOS-proportioned slide and every option **applies and persists instantly, no restart**
- 🎛️ **Adjustable options** — background blur, interface animations (decorative-motion master), background opacity, panel width (400–600), interface scale (x0.75–x1.50, rescales the whole UI live), message entrance animation, double-tap avatar poke
- 🖼️ **Custom wallpaper** — pick a local image as the panel background, downsampled automatically (long edge ≤1024, decoded off-thread); mutually exclusive with blur, and the opacity slider darkens it for readability
- 🚫 **Block management** — a visual block list in settings (avatar + name + one-tap unblock); "Hide blocked players' messages" can be turned off so they stay visible in public chat while private stays blocked
- ✏️ **Multi-line input** — up to two visible lines, then internal scrolling; Up/Down move between lines, single-line drafts keep vanilla history cycling
- 🎨 **SVG icons & unified motion** — image / emoji / send buttons use inline SVG line icons
- 🌍 **Localization** — supports Simplified Chinese and English; switch the game language to apply
- 🧠 **Message capture** — captures real player UUID / profile / decorated names from MessageHandler's three channels, with nick-server support and conservative system-gray fallback
- 🎨 **Themes & full colour control** — one-tap theme presets (Frosted / Modern); the settings page exposes every interface colour (panel background / bubbles / secondary capsules / text / cards / outline / accent), with foldable colour groups and a live preview square on each row
- ⏱️ **Time dividers** — the first message of a list always shows a timestamp capsule; later ones follow a configurable interval
- ✏️ **Cross-message text selection** — drag across several messages and copy them all with Ctrl+C; dragging never misfires clicks
- 🛡️ **Anti-spam & compact groups** — consecutive identical messages merge into one with a counter; same-sender five-minute runs keep the avatar/name only on the first row and tighten the gap
- 💾 **Chat history** — Settings → Chat can keep history per server/world on disk, restore it on rejoin, clear it in one tap, and prune old files by retention days
- 🖥️ **Profile detail page** — push into a player's profile from chat or the conversation list: stat overview, copy buttons, role tag, with a full-width push transition
- 🌐 **Image receive toggle** — when off, nothing is downloaded or cached and a green `[Image]` placeholder is shown
- 🔄 **Teleport mode cycle** — one tap cycles `/tp` / `/tpa` / `auto`
- ⌨️ **IME & multiplayer touches** — IMBlocker command-mode bridge, WATUT "partner is typing" indicator
- 🛠️ **Pure Skia rendering** — rounded corners, shadows, scrolling, and text are vector-drawn; pure animation / layout / token classes ship with JUnit tests

---

## Usage

### Chat & Messages

- Own bubbles sit right, other bubbles left; names hug the bubble edge and avatars align to the bubble top
- Single-click an avatar: open the player's profile detail page (a second click within 300 ms becomes a poke)
- Double-click an avatar: trigger the QQ-style poke shake
- Right-click a bubble: `Copy` / `Quote`; image bubbles also show `Save` to download the original file
- Messages containing `[[CICode,url=...,name=...,w=...,h=...]]` render as image bubbles; older size-less codes are also supported

### Sending Images

- Click the image button → FlatLaf picker (opens `Pictures` / localized Pictures folder by default, with a thumbnail preview)
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
- After switching the game language, the title, tabs, context menu, input placeholder, and file-picker text follow automatically

---

## Configuration

**Recommended**: the in-game `Settings` page (a Windows 11 style tile grid: Appearance / Chat / Privacy & blocking / About). Every option **applies and persists instantly, no restart**; colour rows ship with preset palettes, a `+` custom picker and live previews.

Advanced: edit `.minecraft/config/atomchat/atomchat-client.json` (auto-generated on first launch; restart the game after manual edits). Common keys:

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
| `[[CICode]]` image protocol | ✅ Interoperable with E33Chat / ChatImage family |
| Nickname / display-name plugins | 🟡 Best effort (tell-click / Tab names / decorated-name structure); unknown formats fall back to gray system text |
| Server | ✅ Not required (client-only) |
| Other loaders / versions | ❌ Fabric / NeoForge 1.21.1 only for now |

---

## Known Limitations

1. Fabric / NeoForge 1.21.1 supported so far; the Skija Windows x64 native is bundled. Linux / macOS packages are not built yet
2. Images upload to the third-party host uguu.se by default (~3 hour expiry); no server-side media hosting yet
3. No E33Chat server templates, whisper sidebar, or search; per-world chat-history persistence is built in (off by default)
4. Player identity is best effort: tell-click structured capture, offline seen cache, and multi-tier ownDisplayName fallbacks; extreme unknown formats fall back to gray system text
5. Chat-history persistence is off by default; when enabled, history is stored per server/world on disk and restored on rejoin, without leaking across worlds/servers

---

## Privacy & Data

> [!WARNING]
> Local images you send are uploaded to a third-party image host (uguu.se by default). Do not send sensitive or private content.

- The mod uploads no telemetry or personal information
- Image uploads happen only when you explicitly pick, paste, or drop an image
- Local config and sticker packs stay in `.minecraft/config/atomchat/` and are never synced automatically
- Skin avatar resolution requests Minecraft skin services by player name / UUID, same as vanilla behavior

---

## FAQ

**Do I need a server mod?** No. AtomChat is client-only.

**How do I send an image?** Click the image button to choose a local file, or drag an image into the window / Ctrl+V paste. After the upload finishes it is inserted into the draft; press Enter to send.

**Why is a message shown as gray system text?** When the client cannot be confident a line came from a player, it conservatively renders it as a system message (for example, nickname plugins using unparseable formats).

**Where are sticker packs stored?** `.minecraft/config/atomchat/emotes/`, up to 10 images, png / jpg / jpeg.

**How do I change colors / sizes?** Adjust them live in `Settings → Appearance` (colour rows include presets and a custom picker); alternatively edit `config/atomchat/atomchat-client.json` and restart the game.

**Can I include this in a modpack?** Yes. AtomChat's code is MIT and needs no extra permission; if your modpack redistributes the JAR, keep the third-party notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

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

See [Fabric-1.21.1/CHANGELOG.md](https://github.com/E33EPUS/atomchat/blob/Fabric-1.21.1/CHANGELOG.md) for the full history.

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
