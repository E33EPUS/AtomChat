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
- **Real sender names in bubbles**: messages are now captured at the
  MessageHandler channel level (signed / unsigned / system) instead of only at
  `ChatHud.addMessage`, so the structured sender UUID/profile survives into
  `ChatMessage`. Other players no longer show as the hard-coded “玩家”; the
  bubble name, avatar, reply banner and quote target use the resolved sender.
  A short TTL handoff prevents stale metadata from mislabelling later lines,
  and system-channel NCR/plugin player lines fall back to a structural
  name+separator parser before being treated as system text.
- **New-message entrance starts when first visible**: messages arriving in a
  burst no longer spend most of their animation off-screen while auto-scroll
  catches up; each bubble plays its full fade/slide from the first frame it
  enters the viewport.
- **Fullscreen image picker uses the native AWT file dialog**: the Swing
  chooser stayed hidden behind Minecraft's exclusive-fullscreen window. MC's
  held keys/buttons are released while the dialog owns input. *Superseded — see
  the picker entry under Fixed.*
- **Emoji panel is more compact and scrollable**: the 24 oversized cells are
  replaced by e33chat's larger emoji set plus a kaomoji tab, both with
  scrollable grids and smaller cells.
- **Emoji panel no longer inserts the wrong emoji**: the click handler never
  clamped the column, so a click in the left gutter computed `col = -1` and
  inserted the previous row's last entry, the right gutter inserted the next
  row's first, and a click below the grid hit an item that was scrolled out of
  view. Clicks outside the content rectangle are now rejected outright and the
  column is clamped.
- **Command suggestion popup no longer overlaps the input bar**: the popup
  anchors above the whole input bar instead of above the caret text line, so
  it cannot cover the image/emoji/send buttons.
- **Message entrance animation no longer loops**: the animation deleted its
  start timestamp as soon as it finished, but the message stayed in the
  viewport, so `entranceEase()` read the missing entry as "first visible
  frame" and restarted it — forever, and visibly faster as more messages
  joined the loop. Reopening the screen only stopped it because `openStart`
  moved past those messages. A finished message is now marked as settled and
  keeps its timestamp; the state is discarded only when the message actually
  leaves the viewport (which also re-arms the entrance if it scrolls back in).
- **Native image picker now opens above the game in fullscreen**: the AWT
  file dialog was created with a `null` owner, so Windows placed it at the
  bottom of the z-order and Minecraft's borderless fullscreen window painted
  over it. It is now owned by a reused, invisible 1x1 always-on-top frame,
  which lifts the modal dialog into the same topmost z-band. Focus is handed
  back to the game window (and held keys/buttons released) once it closes.

### Changed

- **Emoji panel is larger**: cell 26 → 34, visible rows 4 → 5, tab bar 30 →
  34, panel padding 10 → 12. Kaomoji rows get their own tokens
  (`EMOJI_KAOMOJI_ROW_H`, `FONT_KAOMOJI`) instead of inline values.
- **Emoji glyphs no longer fill their cell**: the font dropped back to 22 so a
  34-wide cell keeps a visible gutter between neighbours — at 28 the glyphs
  crowded together and made it hard to tell which cell you were aiming at.
- **Message entrance is slower and fades instead of flying**: 140ms → 220ms and
  the slide distance drops from 32 to 14, because a 40px travel dominated the
  animation and the eye never read the fade at all. The fade and the slide now
  use separate curves on one timeline (`easeOutQuad` for opacity,
  `easeOutCubic` for the travel) instead of sharing `easeOutCubic`, which spent
  ~88% of the opacity ramp in the first half of the duration.
- **Input placeholder is always visible**: it no longer requires the field to be
  unfocused, which — ChatScreen focusing the field on open — meant it never
  showed. It now appears whenever the draft is empty, in secondary grey.

### Added

- **Ctrl+V pastes images**: screenshots and copied pictures are read off the
  system clipboard through AWT (Minecraft's clipboard API only exposes strings,
  so it cannot see them), written to a temp PNG and uploaded; copied image files
  are uploaded in place. Plain text still falls through to the vanilla paste.
- **Dragging an image file onto the game window uploads it**: a GLFW drop
  callback is installed while the chat screen is open and removed on close.
  Minecraft registers none — the Win32 backend already calls `DragAcceptFiles`,
  so the events were arriving and being discarded. GLFW has no drag-enter
  callback, so there is no hover feedback; the input placeholder doubles as the
  "uploading…" readout instead. Window-wide: the drop event carries no cursor
  position to hit-test with.
- `minimizeWhilePicking` config (default `false`): minimizes the game window
  while the native image picker is open. GLFW pins a fullscreen window to
  `HWND_TOPMOST`, and where no AWT z-order trick wins, this is the only
  mode-independent guarantee — off by default because it hides the game.

### Fixed

- **Image bubbles no longer stretch**: the box used to be a fixed 275x175 with
  the height clamped rather than scaled, so anything more square than 1.57:1
  was squashed into it. Uploaded codes now carry the intrinsic `w=`/`h=` so the
  receiver can lay the bubble out at the right aspect ratio before the download
  lands (no height jump on arrival). The bubble hugs the scaled image, the
  background no longer draws a letterbox frame, and codes without a size —
  older messages, or formats ImageIO cannot size — fall back to the placeholder
  box. Images are never upscaled.
- **Image bubble names sat in the wrong place**: the name was anchored to the
  message row instead of the bubble edge, and because an image bubble is always
  wider than a short text bubble the name ended up floating away from it. It
  now uses the same edge-anchoring rule as text bubbles.
- **The image picker is a FlatLaf-skinned Swing chooser**: six attempts to lift
  the native `GetOpenFileName` dialog above Minecraft all failed, including
  writing `WS_EX_TOPMOST` straight onto the dialog window from a watchdog
  thread. Windows orders the z-order in two bands — every topmost window above
  every non-topmost one — and ownership only ranks windows inside a band, so a
  native dialog is below a topmost fullscreen game no matter whose owner it is.
  The picker is now a `JFileChooser` in a plain `JFrame`, which can be made
  topmost and does float above the game, and it is skinned with FlatLaf
  (bundled, Apache 2.0) because Swing's default Metal look was the reason it
  was ugly in the first place. It opens in the Pictures folder.
- **Going fullscreen no longer minimises the game while picking**: GLFW
  iconifies a fullscreen window as soon as it loses focus
  (`GLFW_AUTO_ICONIFY` defaults to true and Minecraft leaves it there).
  The flag is suspended for exactly as long as the picker is open. The
  `minimizeWhilePicking` config is gone — it worked around the z-order bug
  rather than the cause.

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
