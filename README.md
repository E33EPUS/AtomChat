# AtomChat

A Fabric 1.21.1 Minecraft mod that replaces the vanilla chat screen with a phone-app-style chat UI, powered by [Skija](https://github.com/HumbleUI/skija) (Java bindings for Skia).

> Status: MVP / work in progress. The project is intentionally a clean rewrite rather than an E33Chat fork.

## Features (MVP)

- Press the chat key to open a phone-sized chat panel; the vanilla chat HUD is hidden while open.
- Rounded chat bubbles, avatars, player names and timestamps.
- Click an avatar to insert `@玩家 `; double-click to trigger a QQ-style poke shake animation.
- Right-click a message to copy or quote it.
- Built-in emoji panel.
- JSON config file at `config/atomchat.json`.

## Roadmap / not yet implemented

- Real player-name parsing and server-side avatar upload/sync.
- uguu image upload + CICode image send/preview.
- Background blur shader.
- More animations and theme presets.

## Building

```bash
./gradlew.bat build
```

The built jar is at `build/libs/atomchat-Fabric-1.21.1-<version>.jar`.

## Dependencies

- Fabric API
- Skija `0.116.8` (bundled into the jar, including Windows x64 native)
