# Changelog

## Unreleased

### Added

- **Multi-line input box**: the bar grows upward by one line height once the
  draft text wraps, eased over 110ms, and caps at two lines. Longer drafts
  scroll vertically inside the fixed box, following the caret. The bar is
  bottom-anchored, so the message list gives back exactly the height the bar
  takes and stays pinned to the newest message while it grows.

### Fixed

- **Context menu could never be dismissed**: `closeContextMenu()` called itself
  instead of clearing `contextMessage`, so the menu stayed on screen forever and
  the resulting stack overflow aborted the rest of `mouseClicked` — which is why
  right-clicking another bubble could not open a new menu. Closing now plays the
  existing symmetric fade/scale-out (110ms).
- **Context menu copy appeared dead**: an exception from
  `keyboard.setClipboard` would abort before the menu closed. It is now wrapped
  and logged so a clipboard failure can't strand the menu.
- **Emoji panel: lower rows were dead and the panel closed on click**: the click
  handler used its own 12-entry array while the panel draws all 24 `EMOJIS`, so
  every cell past the first two rows fell through to "close the panel". Now it
  reads `EMOJIS` and bounds-checks the cell. The emoji button can also toggle
  the panel off again (an outside-click used to close it and the button reopened
  it in the same click).
- **Scrollbar turned blue on hover**: accent colour now requires a held left
  button; hovering only thickens the thumb.

- **Sluggish transitions**: every per-frame tween used `v += (target - v) * dt / D`,
  an asymptotic decay whose tail ran ~4x longer than the stated duration. Panel
  open/close 220ms -> 150ms, message entry 250ms -> 140ms, wheel scroll
  400ms -> 180ms, popups 140ms -> 110ms. Wheel scroll also swaps `easeOutExpo`
  (96% done at half time, then crawls) for `easeOutCubic`.
- **Stuck hover highlight**: same asymptotic decay meant the button tint was
  still ~8% lit half a second after the pointer left. Transitions now advance
  by elapsed time over a fixed duration and snap to the target, so a highlight
  always reaches exactly 0 within 90ms of the pointer leaving.
- **Oversized header**: header height 56 -> 44 and title font 23 -> 19, with the
  channel name centered on both axes inside the card (the clock stays
  right-aligned).

### Changed

- **Bigger names and avatars**: avatar 34 -> 40, name font 14 -> 16, name band
  22 -> 26 (the band doubles as the name/bubble gap), avatar/bubble gap 6 -> 8.
- **Tighter bubbles**: minimum width 36 -> 28, vertical padding 18 -> 14
  (system capsule 10 -> 8). Bubble padding is now a token shared by drawing and
  `messageHeight()`, so layout, clipping and scrolling cannot drift apart.
- **Removed the avatar bezel ring.**
- **Pure white labels** for the image button, emoji button and header clock.

### Added

- `UiMotion`: single source of truth for transition durations plus the
  `approach()` helper that guarantees a transition lands exactly on its target.

## 0.1.0 (MVP)

- Scaffold Fabric 1.21.1 project with Skija rendering.
- Replace vanilla chat screen with a phone-style AtomChat screen.
- Hide vanilla chat HUD while AtomChat is open.
- Render rounded bubbles, avatars, names, timestamps via Skia.
- Add @/poke, copy/quote context menu, emoji panel.
- Add JSON config (`config/atomchat.json`).
- Add unit tests for animator/easing.
