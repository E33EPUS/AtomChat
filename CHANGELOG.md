# Changelog

## Unreleased

### Added

- **Multi-line input box**: the bar grows upward by one line height once the
  draft text wraps, eased over 110ms, and caps at two lines. Longer drafts
  scroll vertically inside the fixed box, following the caret. The bar is
  bottom-anchored, so the message list gives back exactly the height the bar
  takes and stays pinned to the newest message while it grows.
- **Up/Down caret navigation**: once the draft wraps onto a second line,
  Up/Down move the caret between lines (Up = end of target line, Down = start
  of target line). Pressing Up on the first line or Down on the last line
  falls back to vanilla chat-history cycling; a single-line draft is
  unchanged.

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
- **Player names are pure white** in bubbles and image messages.

### Fixed

- **Panel background blur no longer hangs the GPU**: the old Skia-side
  framebuffer snapshot was incompatible with `context.resetAll()` and has been
  removed. The blur is now a raw-GL pre-pass that runs before Skia paints:
  `glBlitFramebuffer` copies the panel region into an offscreen buffer, five
  Kawase passes smooth it, and an AtomChat-owned rounded core shader draws only
  the phone panel's rounded area back. The blur is refreshed every 2 frames.
  `blurEnabled` now defaults to `true`; shader resources reload cleanly with
  F3+T.
- **White/jagged avatar rim**: the avatar no longer bakes a second circular
  alpha mask into the skin image. The face is kept as an opaque square and
  `drawRoundedImage`'s rounded clip is the single source of the circle, drawn
  with `SamplingMode.LINEAR`; the gray placeholder is only painted when no
  skin is available, so it cannot bleed through the avatar's edge.
- **Right-click outside the bubble no longer opens the bubble menu**: context
  menu hits are now limited to the actual bubble rectangle, so right-clicking
  a player name or avatar does not open the message menu.
- **Up/Down on multi-line input now move the caret straight up/down at the
  same visual column** instead of jumping to the target line's end/start.
  Vanilla chat-history Up/Down only applies while the draft is single-line.
- **Reply banner is drawn above the input as an overlay strip**: it is painted
  after the message list so bubbles cannot cover it, and the message list no
  longer moves when the composer grows.
- **Input selection highlight**: the hidden EditBox's selected range is now
  drawn as a Skia selection block over wrapped input lines (Shift+arrows /
  Ctrl+A selections are visible).
- **Latest message no longer drifts upward as the list grows**: two causes
  were fixed. The “was at bottom” check now snapshots before new messages grow
  `maxScroll`, and the draw loop now advances by the same `LIST_GAP` token used
  by the scroll-height calculation — it previously hard-coded `10.0` while
  `LIST_GAP` is `s(10) = 12.5`, so every message silently added 2.5px of
  phantom bottom space and the newest bubble looked farther from the lower bar
  as history accumulated.
- **Reply banner @ prefix**: the floating reply banner now shows `@玩家`.
- **Message text drag selection**: drag across message text highlights the
  selected range in Skia; Ctrl+C copies the selected message text. Works for
  normal text bubbles and system capsules (same-message multi-line selection).
- **Quote pill is larger**: quote capsule height/font/padding increased.
- **Panel blur tint is more opaque** and the default panel width is narrower
  (480 → 420) for a more phone-like aspect.
- **Slightly more compact bubbles**: horizontal bubble padding 14 → 12,
  vertical bubble padding 14 → 11, system capsule padding 8 → 6, and the
  minimum bubble width 28 → 24.
- **Quote pills now use the same light gray-white fill as the header/input
  bars** (translucent white) with white quote text, instead of the old dark
  blue-gray capsule.
- **QQ-style message entrance**: new bubbles slide in horizontally while
  fading — own messages from the right toward the left, others from the left
  toward the right. Centered system capsules fade in place.

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
