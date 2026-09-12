[简体中文](README.md) | [English](README_EN.md)

<h1 align="center"><img width="256" height="256" alt="logo" src="https://github.com/user-attachments/assets/e449c62e-644b-4c19-9fbc-431d7a899781" /></h1>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>A phone-app style chat experience for Minecraft, powered by Skia.</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1%20%7C%201.20.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-NeoForge-blue">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge-red">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20%7C%20Server-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Fabric Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=Fabric-1.21.1"></a>
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="NeoForge Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=NeoForge-1.21.1"></a>
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Forge Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=Forge-1.20.1"></a>
</p>

AtomChat is a brand-new chat beautification mod developed in the spirit of [E33Chat](https://github.com/E33EPUS/E33Chat). It turns the vanilla chat screen into a standalone "phone app" style chat panel: rounded bubbles, custom avatars, real player names, emoji / kaomoji / sticker packs, image and animated-GIF messages, server-side media hosting, copy and quote, multi-line input, QQ-style motion, and more screens beyond chat.

Rendering uses [Skija](https://github.com/HumbleUI/skija). The whole UI is vector-drawn instead of using vanilla chat textures.

> Status: **v0.2.9 released (Fabric / NeoForge 1.21.1, Forge 1.20.1)**. Download from [Releases](https://github.com/E33EPUS/AtomChat/releases); this is an intentional clean rewrite in the spirit of E33Chat, not a fork.

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
- [Feedback](#feedback)
- [Development & Building](#development--building)
- [Changelog](#changelog)
- [Third-Party Licenses](#third-party-licenses)
- [License](#license)

---

## Installation

| Dependency | Type | Notes |
|---|---|---|
| Minecraft | Required | 1.21.1 (Fabric / NeoForge) or 1.20.1 (Forge) |
| Fabric Loader | Required (Fabric build) | 0.16.0+ |
| Fabric API | Required (Fabric build) | any 1.21.1 compatible version |
| NeoForge | Required (NeoForge build) | 21.1.235 |
| Forge | Required (Forge build) | 47.x (1.20.1) |
| Java | Required | 21+ for the Fabric / NeoForge builds, 17+ for the Forge 1.20.1 build |

1. Download the JAR for your platform from [Releases](https://github.com/E33EPUS/AtomChat/releases) (`atomchat-Fabric-1.21.1-*.jar` / `atomchat-NeoForge-1.21.1-*.jar` / `atomchat-Forge-1.20.1-*.jar`), or build it yourself under [Development & Building](#development--building)
2. Put the JAR in `.minecraft/mods/`; drop the same jar into the server's `mods/` too if you want server-side media hosting and avatar sync
3. Launch the game and press the chat key (default `T` / `/`) to open AtomChat

---

## Quick Start

1. Open chat to see the phone panel: "Public" and the system time at the top, the message list in the middle, and the composer at the bottom
2. Type text and press Enter to send; the composer grows when the text wraps and still supports Up/Down caret movement
3. Click the **image icon** to pick a local image, or **drag an image into the window / Ctrl+V** to paste it; the upload is inserted into the draft automatically
4. Click the **emoji icon** to open the panel with `Emoji` / `Kaomoji` / `Stickers` tabs
5. Right-click any message to **Copy** or **Quote**; single-click an avatar to open the player's profile page, double-click it for a QQ-style poke shake, and right-click it for the menu with @ mention, whisper, teleport and block

---

## Features

### Vanilla Improvements

- 💬 **Chat bubbles & avatars** — your messages sit on the right, everyone else's on the left, with real player names and round skin avatars; skin faces resolve from online / offline profiles with fallbacks
- 🧹 **Compact vanilla HUD placeholders** — outside the panel, image codes show as green `[Image]` and quotes as blue `[Quote]` instead of raw long codes
- 📝 **Multi-line input** — the composer shows up to two lines, then scrolls internally; Up/Down move between lines while single-line drafts keep the vanilla chat history
- 🧠 **Message capture** — real player identity is captured from MessageHandler's three channels, with nick-server support; unknown messages degrade to system text
- 📑 **Chat history** — when enabled, history is saved per server/world on disk, restored on rejoin, and pruned automatically
- 🔃 **Public feed filter** — a button beside the header back arrow cycles all / system-only / players-only; the icon changes per state and tints with the accent colour while filtering. View-only: unread badges and previews still count every message

### Highlights

- 📱 **Phone-style panel** — the vanilla chat HUD is hidden while AtomChat is open; blurred background plus a translucent glass composer
- 🖼️ **Custom avatars** — crop any local image into a square avatar shown on your profile and your own bubbles; on servers also running AtomChat it syncs to every player (changes broadcast instantly), silently falling back to skins without the companion
- 🔔 **Notification banners & sounds** — being @mentioned, quoted or whispered drops a banner at the top of the panel (click to jump to the message) with an original synthesized pop cue; each banner/sound pair is individually switchable with a volume slider
- 🖼️ **Image messages** — renders `[[CICode]]` natively (interoperable with E33Chat / ChatImage), keeps the source aspect ratio and **plays animated GIFs on a loop inside the bubble**; transparent PNG / GIFs show the panel behind them instead of a grey plate; placeholder while loading; right-click an image bubble to save the original file
- 📤 **Local image sending** — the FlatLaf picker defaults to Details view with inline thumbnails; supports drag & drop and Ctrl+V paste; uploads are converted into CICode automatically, falling back to the external host when the server cannot take them
- 📦 **Server-side media hosting** — when the server also runs AtomChat, images and GIFs upload there and are distributed from there: content-addressed deduplication, chunked on-demand delivery, everything over the game connection and **no HTTP port opened**. Falls back to the external host when the server lacks the mod, hosting is off, the file is oversized or the upload fails. The same `hostingEnabled` master switch also governs custom-avatar sync
- 🎁 **Server-offered packs** — joining a server that runs AtomChat syncs its content: the emotes its admin dropped into `config/atomchat/emotes/`, the phrases in its config, and its identity (`server-icon.png` plus MOTD). The emote panel gains a read-only "this server" section (name and sync state) and the phrase panel a read-only group; the transfer verifies every file by SHA-256 and only fetches the difference (one deleted emote costs one file next join), the client refuses anything past 200 files / 16 MB, and every failure lands in the server log with a reason. Operators edit all of it in game with `/atomchat gui` (OP level 2; single-player needs no permission)
- 😀 **Emoji / Kaomoji / Stickers** — three tabs with a sliding indicator and full-width push transitions; stickers persist in `<config>/atomchat/emotes/` (png/jpg/jpeg/gif, max 20 and the grid scrolls; server-offered emotes sit in their own read-only section below and do not use these slots), added through the `+` cell and deleted by hovering `×`; GIF stickers show their first frame in the grid (keeping the grid smooth) and animate once sent
- ⚡ **Quick phrases** — a lightning button opens a phrase list above the composer; tapping inserts into the input (never sends on its own), with add / inline-edit / delete, capped at 256 characters and 20 entries
- 🔗 **Rich-text messages** — player names/bodies support colors, underlines, clicks and hover tooltips: `/tell`, coordinates, FTB accept/deny and external links are clickable; bare URLs become links automatically
- 📋 **Copy / Quote / Save** — right-click to copy or quote; image messages can be saved; the context menu uses 20×20 SVG line icons
- 🧭 **Avatar menu** — right-click an avatar for @ mention, whisper, quick teleport and block; a single click opens the player's profile and a double click triggers the QQ-style poke
- 🪢 **Private chat** — the chat list shows currently online players; click one to open the conversation, right-click for its menu (profile / teleport / block)
- 👥 **Online players & conversation list** — the conversation list orders Public → online players → recent offline, with real IDs, skin avatars, online/offline dots and unread badges; private chat uses `/msg`, keeps per-conversation drafts and scroll state, and is read-only for offline or blocked players
- 🖥️ **Profile detail page** — push into a player's profile from chat or the conversation list: stat overview, copy buttons, role tag, with a full-width push transition
- 🧭 **Phone-style navigation** — three bottom tabs (Chats / You / Settings) with page-level push/pop transitions; the same animation carries public ↔ private switches
- ⚙️ **Settings page** — a Windows 11 style 2×2 tile grid (Appearance / Chat / Privacy & blocking / About) opening into sub-pages; switches use an iOS-proportioned slide and every option **applies and persists instantly, no restart**
- 🎛️ **Adjustable options** — background blur, interface animations (decorative-motion master), background opacity, panel width (400–600), interface scale (x0.75–x1.50, rescales the whole UI live), message entrance animation, double-tap avatar poke
- 🖼️ **Custom wallpaper** — pick a local image as the panel background, downsampled automatically (long edge ≤1024, decoded off-thread); mutually exclusive with blur, and the opacity slider darkens it for readability
- 🚫 **Block management** — a visual block list in settings (avatar + name + one-tap unblock); "Hide blocked players' messages" can be turned off so they stay visible in public chat while private stays blocked
- 🎨 **Themes & full colour control** — one-tap theme presets (Frosted / Modern); the settings page exposes every interface colour (panel background / bubbles / secondary capsules / text / cards / outline / accent), with foldable colour groups and a live preview square on each row
- ⏱️ **Time dividers** — the first message of a list always shows a timestamp capsule; later ones follow a configurable interval
- ✏️ **Cross-message text selection** — drag across several messages and copy them all with Ctrl+C; dragging never misfires clicks
- 🛡️ **Anti-spam & compact groups** — consecutive identical messages merge into one with a counter; same-sender five-minute runs keep the avatar/name only on the first row and tighten the gap
- 🌐 **Image receive toggle** — when off, nothing is downloaded or cached and a green `[Image]` placeholder is shown
- 🔄 **Teleport mode cycle** — one tap cycles `/tp` / `/tpa` / `auto`
- 🌍 **Localization** — supports Simplified Chinese and English; switch the game language to apply
- ⌨️ **IME & multiplayer touches** — IMBlocker command-mode bridge, WATUT "partner is typing" indicator
- 🎨 **SVG icons & unified motion** — image / emoji / send buttons use inline SVG line icons
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
- When the server runs AtomChat with `hostingEnabled`, images and GIFs upload there first (content-addressed, so an identical image is stored once) and the message carries an `atomchat-media:<id>` link that other players fetch on demand; if the server lacks the mod, hosting is off, the file exceeds `maxFileKb` or the upload fails, it falls back to the external host
- The default external host is uguu.se and its links expire after about 3 hours; server-hosted media (images / GIFs / forwarded emotes) and avatars are kept for 7 days by default and pruned by the server's `maxTotalMb`, `maxAvatarTotalMb` and `retentionDays`

### Sticker Packs

- Folder: `.minecraft/config/atomchat/emotes/`
- Supports png / jpg / jpeg / gif, sorted by file name, up to 10 images (a GIF shows its first frame in the grid and animates once sent)
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

### Storage Paths

`.minecraft/config/atomchat/` holds `atomchat-client.json` plus the data folders:

| Path | Contents |
|---|---|
| `atomchat-client.json` | Main config file (generated on first launch) |
| `avatar/` | Custom avatars (256px PNG) |
| `emotes/` | Sticker packs (png / jpg / jpeg / gif, up to 10) |
| `wallpaper/` | Custom wallpaper |
| `history/` | Chat history (per world / server) |
| `debug/` | Avatar sampling PNGs written in debug mode |

Auto-downloaded chat images and companion avatar data live under `<gameDir>/atomchat-data/` (capped at 500 files / 100 MB and dropped once unused for 7 days, viewable and clearable from Settings → About). Media and avatars hosted by a server live under that server's own `<server game dir>/atomchat-data/`.

### Server Configuration

When the server (or the server side of a single-player / LAN host) runs AtomChat, it creates `<server game dir>/config/atomchat/atomchat-server.json` on first launch; hand edits take effect the next time a client joins:

| Key | Default | Description |
|---|---|---|
| `hostingEnabled` | `true` | Master switch: does this server host chat images / GIFs and player avatars. When off, clients fall back to the external image host and to skins |
| `maxFileKb` | `2048` | Largest single hosted file in KB; anything bigger falls back to the external host |
| `maxTotalMb` | `512` | Total media store budget in MB; the oldest files are deleted first |
| `maxAvatarTotalMb` | `64` | Total avatar store budget in MB; the oldest files are deleted first |
| `retentionDays` | `7` | How long hosted media and avatars are kept; `0` keeps them forever |
| `uploadCooldownMs` | `3000` | Minimum interval between two uploads from the same player, in milliseconds |
| `packEnabled` | `true` | Master switch: offer this server's emotes, phrases and identity to clients |
| `packMaxFiles` | `32` | Largest number of emote files offered |
| `packMaxMb` | `8` | Total emote bytes offered, in MB |
| `packName` | `""` | Server name shown in the client panel; blank uses the cleaned first line of the MOTD |
| `phrases` | `[]` | Phrases offered to clients (at most 20, each at most 200 characters) |

All of these can also be edited in game with `/atomchat gui` (OP level 2; no permission needed in single-player): the screen is drawn by the **editing player's own client**, so a rented server, a panel-hosted server, a single-player world and a LAN host all work; a console is told there is no screen to draw on.

Hosted media is stored in `<server game dir>/atomchat-data/media/`, named by content hash and therefore deduplicated by content.

---

## Compatibility

| Item | Status |
|---|---|
| Fabric 1.21.1 | ✅ Supported |
| NeoForge 1.21.1 | ✅ Supported |
| Forge 1.20.1 | ✅ Supported |
| Java | ✅ 21+ for the Fabric / NeoForge builds, 17+ for the Forge 1.20.1 build |
| Server | ✅ Not required (client-only); installing it enables **server-side media hosting** and custom-avatar sync |
| `[[CICode]]` image protocol | ✅ Interoperable with E33Chat / ChatImage family |
| Nickname / display-name plugins | 🟡 Best effort (tell-click / Tab names / decorated-name structure); unknown formats fall back to gray system text |
| ChatHeads | ✅ Compatible |
| Chat Animation / similar animation mods | ✅ Compatible |
| EasyBot | 🟡 Best effort (QQ message type parsing); unparseable lines fall back to gray text |
| Quark | 🚫 Emote menu is not shown; compatibility work in progress |
| Other loaders / versions | ❌ Fabric / NeoForge 1.21.1 and Forge 1.20.1 only for now |

---

## Known Limitations

1. Fabric / NeoForge 1.21.1 and Forge 1.20.1 are supported; the Skija Windows x64 native is bundled. Linux / macOS packages are not built yet (the mod cannot run there)
2. Server-side media hosting needs AtomChat on the server too. Without it (or with hosting off) images keep going to the third-party host uguu.se (~3 hour expiry), and files above `maxFileKb` (2 MB by default) fall back to it instead of being recompressed client-side
3. No E33Chat whisper sidebar or search yet; server-format templates are supported (hand-edit `chatTemplates` / `whisperTemplates`), and per-world chat-history persistence is built in (off by default)
4. Player identity is best effort: tell-click structured capture, offline seen cache, and multi-tier ownDisplayName fallbacks; extreme unknown formats fall back to gray system text
5. Chat-history persistence is off by default; when enabled, history is stored per server/world on disk and restored on rejoin, without leaking across worlds/servers
6. Server-side media hosting has only been exercised end to end on a single-player / LAN host server; a dedicated server (especially on Linux, where the bundled Skija native does not apply) and two clients pulling media from each other are not tested yet

---

## Privacy & Data

> [!WARNING]
> Local images you send are uploaded to a third-party image host (uguu.se by default) or, when the server has hosting enabled, stored on that server's disk; either way others can save or forward them. Your messages can also be saved by players who enabled chat history. Do not send sensitive or private content.

- The mod uploads no telemetry or personal information
- Image uploads happen only when you explicitly pick, paste, or drop an image
- Server-side hosting moves bytes only over the game connection: no HTTP port is opened and nothing is exposed to players who are not connected
- A hosting server keeps a copy of your image under `<server game dir>/atomchat-data/media/` and of your avatar under `avatars/`; both are pruned by its `maxTotalMb` / `maxAvatarTotalMb` and `retentionDays` (7 days by default) settings, or deleted by the admin at any time
- Local config and sticker packs stay in `.minecraft/config/atomchat/` and are never synced automatically
- Skin avatar resolution requests Minecraft skin services by player name / UUID, same as vanilla behavior

---

## FAQ

**Do I need a server mod?** No. AtomChat is client-only. Running it on the server (or on the server side of a single-player / LAN host) additionally enables **server-side media hosting** (images and GIFs travel through the server instead of an image host) and **custom-avatar sync**.

**How do I send an image?** Click the image button to choose a local file, or drag an image into the window / Ctrl+V paste. After the upload finishes it is inserted into the draft; press Enter to send.

**Do images go to the image host or the server?** With AtomChat on the server and `hostingEnabled=true` the server wins and the message carries an `atomchat-media:<id>` link; if the server lacks the mod, hosting is off, the file exceeds `maxFileKb` or the upload fails, it falls back to uguu.se. Hosted content is pruned by the server's size and age settings. The log tells you which path ran: `Stored hosted media` for hosting, `Uploaded chat image to the external host` for the fallback.

**Why is a message shown as gray system text?** When the client cannot be confident a line came from a player, it conservatively renders it as a system message (for example, nickname plugins using unparseable formats).

**Where are sticker packs stored?** `.minecraft/config/atomchat/emotes/`, up to 10 images, png / jpg / jpeg / gif (a GIF shows its first frame in the panel and animates once sent).

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

Artifacts land in `build/libs/`:

- Fabric: `atomchat-Fabric-1.21.1-<version>.jar`
- NeoForge: `atomchat-NeoForge-1.21.1-<version>.jar`
- Forge: `atomchat-Forge-1.20.1-<version>.jar` (a `-slim` jar without the embedded dependencies is also produced; ship the one without `-slim`)

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
