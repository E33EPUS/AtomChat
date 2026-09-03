[简体中文](README.md) | [English](README_EN.md)

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>A phone-app chat experience for Minecraft's vanilla chat box</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-21%2B-yellow">
  <img alt="Version" src="https://img.shields.io/badge/Version-0.1.0%20MVP-informational">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

AtomChat is a brand-new chat beautification mod developed in the spirit of [E33Chat](https://github.com/E33EPUS/E33Chat). It turns the vanilla chat screen into a standalone "phone app" style chat panel: rounded bubbles, avatars, real player names, emoji / kaomoji / sticker packs, image messages, copy and quote, multi-line input, and QQ-style motion.

Rendering uses [Skija](https://github.com/HumbleUI/skija)

The whole UI is vector-drawn instead of using vanilla chat textures.

> Status: **0.1.0-MVP / no formal release yet**. Current builds require self-building (see [Development & Building](#development--building)); this is an intentional clean rewrite in the spirit of E33Chat, not a fork.

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
| Fabric Loader | Required | 0.16.0+ |
| Fabric API | Required | any 1.21.1 compatible version |
| Java | Required | 21+ |

1. This is an MVP development build: build the JAR yourself under [Development & Building](#development--building) (once a formal Release exists, download it from [Releases](https://github.com/E33EPUS/atomchat/releases) instead)
2. Put the JAR in `.minecraft/mods/`
3. Launch the game and press the chat key (default `T` / `/`) to open AtomChat

---

## Quick Start

1. Open chat to see the phone panel: "World Channel" and the system time at the top, the message list in the middle, and the composer at the bottom
2. Type text and press Enter to send; the composer grows when the text wraps and supports Up/Down caret movement
3. Click the **image icon** to pick a local image, or **drag an image into the window / Ctrl+V** to paste it; the upload is inserted into the draft automatically
4. Click the **emoji icon** to open the panel with `Emoji` / `Kaomoji` / `Stickers` tabs
5. Right-click any message to **Copy** or **Quote**; click an avatar to `@`, double-click it for a QQ-style poke shake

---

## Features

- 📱 **Phone-style panel** — the vanilla chat HUD is hidden while AtomChat is open; blurred background plus a translucent glass composer
- 💬 **Bubbles & avatars** — own messages on the right, others on the left, with avatar and player name; skin faces resolve from online / offline profiles with fallbacks
- 🖼️ **Image messages** — renders `[[CICode]]` natively (interoperable with E33Chat / ChatImage), keeps the source aspect ratio; placeholder while loading
- 📤 **Local image sending** — FlatLaf-styled modern file picker, drag & drop, and Ctrl+V paste; uploads are converted into CICode automatically
- 😀 **Emoji / Kaomoji / Stickers** — three tabs with a sliding indicator and full-width push transitions; stickers persist in `<config>/atomchat/emotes/` (png/jpg/jpeg, max 10), added through the `+` cell and deleted by hovering `×`
- 📋 **Copy & quote reply** — right-click a message to copy or quote; quotes travel as `「引用 @name: snippet」` and render as a quote bar on the receiver
- ✏️ **Multi-line input** — up to two visible lines, then internal scrolling; Up/Down move between lines, single-line drafts keep vanilla history cycling
- 🎨 **SVG icons & unified motion** — image / emoji / send buttons use inline SVG line icons
- 🌍 **Localization** — supports Simplified Chinese and English; switch the game language to apply
- 🧠 **Message capture** — captures real player UUID / profile / decorated names from MessageHandler's three channels, with nick-server support and conservative system-gray fallback
- 🛠️ **Pure Skia rendering** — rounded corners, shadows, scrolling, and text are vector-drawn; pure animation / layout / token classes ship with JUnit tests

---

## Usage

### Chat & Messages

- Own bubbles sit right, other bubbles left; names hug the bubble edge and avatars align to the bubble top
- Click an avatar: insert `@name `
- Double-click an avatar: trigger the QQ-style poke shake
- Right-click a bubble: `Copy` / `Quote`
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

Config file: `.minecraft/config/atomchat/atomchat-client.json` (auto-generated on first launch; restart the game after editing)

| Key | Default | Description |
|---|---|---|
| `panelWidth` | `420.0` | Panel width in design pixels (further scaled by the UI scale) |
| `panelHeight` | `780.0` | Panel height |
| `blurEnabled` | `true` | Rounded background blur (raw GL + core shader) |
| `animationEnabled` | `true` | Master animation switch |
| `debug` | `false` | Debug logging / avatar sampling PNGs (written to `config/atomchat/debug/`) |
| `accentColor` | `0xFF4A90E2` | Accent color (send button, quote bar, etc.) |
| `ownBubbleColor` | `0xFF4A90E2` | Own bubble color |
| `otherBubbleColor` | `0xFF343A44` | Other bubble color |
| `panelBgColor` | `0xEE16191F` | Panel background color |
| `textPrimaryColor` | `0xFFFFFFFF` | Primary text color |
| `textSecondaryColor` | `0xDCAAAABA` | Secondary text color |

---

## Compatibility

| Item | Status |
|---|---|
| Fabric 1.21.1 | ✅ Supported |
| Java 21+ | ✅ Required |
| `[[CICode]]` image protocol | ✅ Interoperable with E33Chat / ChatImage family |
| Nickname / display-name plugins | 🟡 Best effort (tell-click / Tab names / decorated-name structure); unknown formats fall back to gray system text |
| Server | ✅ Not required (client-only) |
| Other loaders / versions | ❌ Fabric 1.21.1 only for now |

---

## Known Limitations

1. Fabric 1.21.1 only; the Skija Windows x64 native is bundled. Linux / macOS packages are not built yet
2. No GUI config screen yet; edit `config/atomchat/atomchat-client.json` manually
3. Images upload to the third-party host uguu.se by default (~3 hour expiry); no server-side media hosting yet
4. No E33Chat server templates, whisper sidebar, search, notification banners, or persistent chat history
5. Player identity is best effort: tell-click structured capture, offline seen cache, multi-tier ownDisplayName, and whisper classification are not implemented yet
6. Chat history is in-memory only (cap 500) and is not persisted across restarts; it is not cleared automatically when changing worlds / servers

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

**How do I change colors / sizes?** Edit `.minecraft/config/atomchat/atomchat-client.json` and restart the game.

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

See [master/CHANGELOG.md](https://github.com/E33EPUS/atomchat/blob/master/CHANGELOG.md) for the full history.

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
