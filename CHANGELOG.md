# Changelog

## v0.1.4

### 中文

- **头像右键菜单框架**：右键真实玩家头像弹出菜单（@ 提及 / 私聊 / 传送 / 屏蔽）；@ 已接入，其余动作预留；左键单击头像不再插入 @，双击仍为 QQ poke。
- **导航壳**：AtomChat 升级为同面板页面栈；根页为会话列表，底部 `聊天 / 个人 / 设置` tab；默认聊天键（T）直接打开公屏，新键位（默认 Y）打开上次所在页面；世界频道详情页带 SVG 返回箭头。
- **统一壳级 Header**：所有页面右上角统一显示时间；Header/标题/返回由壳统一绘制，页面类不再重复画。
- **底部 Tab 重绘**：采用 Apple 风格紧凑公式布局；三个 tab SVG 重绘为细线 + 选中填充/高亮。
- **公屏命名**：用户可见的 “World Channel / 世界频道” 统一改为 `Public / 公屏`。
- **可复用滚动系统**：新增纯 `ScrollController`，世界频道消息列表与根页共用滚动条/滚轮/拖动逻辑；为后续长列表（私聊/设置）铺路。
- **架构拆分**：抽出 `AppIcons` / `ShellHeader` / `BottomTabBar` / `ScrollController` 等壳级组件，减少 `AtomChatScreen` 膨胀。

### English

- **Avatar context-menu framework**: right-click a real player avatar opens a menu (Mention / Whisper / Teleport / Block); Mention is wired, the rest are placeholders for upcoming features. Left single-click no longer inserts @; double-click still triggers the QQ-style poke.
- **Navigation shell**: AtomChat now has an in-panel page stack. The root is a conversation list with Chat / Profile / Settings bottom tabs; the normal chat key (T) opens Public directly, and a new key (default Y) restores the last opened page. The Public detail page has an SVG back arrow.
- **Unified shell header**: time is shown on every page's top-right; header/title/back are drawn once by the shell instead of per page.
- **Redesigned bottom tab bar**: Apple-style compact formula layout; the three tab SVGs were redrawn with line style plus selected fill/highlight.
- **Public naming**: all user-visible “World Channel / 世界频道” copy is now `Public / 公屏`.
- **Reusable scroll system**: a pure `ScrollController` now powers both the world-chat message list and root pages, sharing scrollbar/wheel/drag behavior for future long lists.
- **Architecture cleanup**: extracted shell-level `AppIcons`, `ShellHeader`, `BottomTabBar`, and `ScrollController` components to keep `AtomChatScreen` from growing further.

## v0.1.3

### 中文

- **富文本聊天渲染**：玩家名/正文支持颜色、下划线、点击与悬停；可点击 `/tell`、Xaero 坐标、FTB 接受/拒绝、外部链接；裸 `http(s)` 自动识别为可点击链接并在悬停时显示 URL。
- **原版 HUD 占位**：`[[CICode,...]]` 图片代码在原版聊天栏显示为绿色 `[图片]`，不再刷一长串 URL；引用消息显示为蓝色 `[引用]`，保留发送者前缀。
- **图片消息右键保存**：右键图片气泡新增“保存”，通过 FlatLaf 另存为对话框选择位置，后台下载原始 URL 字节（GIF/WebP/PNG 原样保留）。
- **右键菜单图标**：复制 / 引用 / 保存均绘制 20×20 白色线性 SVG 图标。
- **文件选择器改进**：默认“详细信息”视图，图片文件在列表中直接显示内联缩略图，不再依赖右侧预览区。
- **气泡/UI 修复**：多行文本气泡宽度按最长行计算；引用胶囊与气泡外缘对齐；系统消息胶囊颜色与图片加载占位一致并保留半透明；普通网页链接不再被误当成图片消息。
- **指令不再本地弹气泡**：输入 `/` 指令不再制造自己的聊天气泡，与原版行为一致。
- **稳定性修复**：修复 AWT headless 导致图片选择器打不开；修复 0.1.2 中 HUD 重写未声明 cancellable 导致发送图片后被踢出单人游戏的问题。
- **消息不被误吞**：Xaero waypoint/路径分析等机器协议消息强制走系统通道；无频道身份、仅文本像“自己”的消息不再被 own-echo 误杀，遵循 e33chat“宁可不杀不可错杀”原则。

### English

- **Rich-text chat rendering**: player names/bodies support colors, underlines, click actions and hover tooltips; clickable `/tell`, Xaero coordinates, FTB accept/deny and external links work; bare `http(s)` URLs become clickable links with a hover URL tooltip.
- **Compact vanilla HUD placeholders**: `[[CICode,...]]` image codes now show as green `[Image]` instead of a long URL, and quote replies show as blue `[Quote]` while keeping the sender prefix.
- **Save images from the context menu**: right-click an image bubble → Save, pick a destination in the FlatLaf save dialog, and the original URL bytes are downloaded in the background (GIF/WebP/PNG preserved).
- **Context menu icons**: Copy / Quote / Save now use 20×20 white line-style SVG icons.
- **File chooser improvements**: defaults to Details view and shows inline thumbnails directly in the file list.
- **Bubble/UI fixes**: multi-line bubble width hugs the longest line; quote capsule aligns with the bubble edge; system capsule uses the image-loading placeholder colour while staying translucent; ordinary web links are no longer mistaken for image messages.
- **No local bubble for commands**: slash commands no longer manufacture a local chat bubble, matching vanilla behaviour.
- **Stability fixes**: fixed the AWT headless issue that prevented the image picker from opening; fixed 0.1.2 being kicked from single-player when sending an image because the HUD-rewrite mixin was not declared cancellable.
- **Messages are never wrongly swallowed**: Xaero waypoint/path-analysis machine protocols are forced to the system channel; meta-less messages that merely look like your own echo are no longer dropped, following e33chat's "rather show than kill" principle.

## Unreleased

### Added

- **SVG toolbar icons**: the image / emoji / send buttons now draw inline SVG
  path icons instead of Chinese text labels. The icons are line-style at a
  constant 1.5px stroke, centered in each button and recoloured with the theme,
  so they stay crisp at every UI scale with no image assets.
- **Localized UI copy**: all AtomChat surface text now goes through Minecraft's
  language files. New keys in `en_us.json` / `zh_cn.json` cover the world
  channel title, reply banner, input placeholder/upload status, image loading
  text, emoji tab labels, context menu, sender fallbacks, and the Swing image
  picker title/filter/preview strings.
- **Message capture hardening**: structured chat identity is captured right
  before the real `ChatHud.addMessage` call (inside MessageHandler) instead of
  at the public channel method's HEAD. This keeps the single-slot handoff
  correct when vanilla queues messages via accessibility chat delay, and
  prevents filtered/blocked messages from leaking their identity onto the next
  HUD line. MessageCapture timestamps now travel with each per-thread entry,
  nil UUIDs are normalized to null in `SenderMeta`, and the profileless fallback
  parses the decorated line rather than the raw body.
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
- **Emote pack tab**: a third "表情包" tab beside 表情 / 颜文字. Tapping an
  emote uploads the local image and drops its CICode into the draft, then
  closes the panel (one sticker per tap). The trailing "+" cell opens the
  FlatLaf picker to add images; hovered cells show a × to delete. Persisted as
  copied files in `<config>/atomchat/emotes/` (png/jpg/jpeg, name-sorted, cap
  of 10; the add cell greys out when full). Emotes render fitted, never
  upscaled, in a 6-column grid that never scrolls.
- **Unified hover feedback**: emoji / kaomoji / emote cells and the context
  menu's 复制/引用 rows now share the button language — a translucent white
  highlight that fades in and out over 90ms. Emote cells draw the image first
  and the hover wash + × remove button on top, so the delete control can never
  be buried under a picture.
- **Emoji tab transition**: switching between 表情 / 颜文字 / 表情包 is an
  opaque full-width push, like moving from one screen to the next — the
  outgoing tab is pushed out as the incoming tab slides in from the same
  direction, and the active pill glides to the new tab. Both run at 200ms with
  easeInOutCubic, via `UiMotion.TAB_MS`.
- **Calculated highlight spacing**: the emoji tab strip is now inset by
  `EMOJI_PANEL_PAD` so it aligns with the content grid; the active pill keeps
  s(4) side margins, s(6) above and s(2) below — the extra bottom length
  centres the label inside the pill. It no longer crowds the panel's rounded
  border. The context menu row capsule keeps a uniform s(4), and the
  emoji/kaomoji cell capsules keep a uniform s(2) outer margin. Emoji glyphs
  are centred in their capsule; kaomoji rows keep s(6) of internal left padding
  so text never touches the capsule edge.

### Fixed

- **Chat identity could be attached to the wrong HUD line**: the channel-level
  capture previously set a single pending meta at the start of
  `MessageHandler.onChatMessage` / `onProfilelessMessage` / `onGameMessage`.
  With vanilla accessibility chat delay, two queued messages overwrote each
  other; with a filtered/blocked message that never reached the HUD, the stale
  meta could leak onto the next line. Capture now fires immediately before the
  real `ChatHud.addMessage` call, after vanilla's delay and skip/filter paths.
- **Captured body text lost a literal `<name> ` prefix**: `ChatMessage` stripped
  the vanilla sender prefix even when the body had already been captured before
  decoration, so a message that really started with `<Alice> hi` was shown as
  `hi`. The prefix stripper now only runs on the raw-HUD fallback.
- **Sender-name parsing accepted mid-word matches**: a candidate `Steve` could
  match `Steve-Master` or the `tch` inside `<Notch>`. MessagePresentation now
  rejects letter/hyphen continuations and suffix matches inside angle brackets.
- **List lagged behind the growing input bar**: when the draft wrapped to a
  second line the input bar grew upward and the list shrank with it, but the
  bottom-pinned scroll chased the moving `maxScroll` with an eased animation
  that restarted every frame — so growing looked desynced while shrinking (a
  plain clamp) felt fine. When the list viewport height changes and the view is
  pinned to the bottom, `scrollY` is now locked straight to `maxScroll` in
  lockstep with the bar; new-message arrivals still use the smooth eased
  follow.
- **Images ghosted through the grown input bar**: the root cause was not a
  z-order issue — the message list painted content down to the one-line bar
  top, and the translucent grown bar sat on top of it, so list images showed
  through. The list's visible area now ends at the current input bar top (it
  yields exactly the height the bar gains), so nothing is ever painted
  underneath the translucent composer and its transparency is preserved.
- **Message entrance replayed when scrolling through history**: a bubble that
  had finished its entrance animation was unmarked as soon as it left the
  viewport, so scrolling back up replayed it. Once an entrance settles it now
  never replays while the screen is open; the settled set is bounded by a 5s
  time guard (older messages are settled by time alone), so scrolling through
  history is silent and memory stays bounded.
- **Kaomoji rendered as boxes**: the bundled GB2312 font subset lacks most
  kaomoji characters, and the Skia fallback only searched a narrow set of
  system families. The fallback list now includes DengXian, Segoe UI Symbol,
  MS Gothic / Yu Gothic UI, Malgun Gothic, Leelawadee UI, Cambria and Calibri;
  emoji-range codepoints also verify that Segoe UI Emoji actually contains the
  glyph before using it, otherwise they fall through to the symbol-font search
  (fixes tofu on ✧ U+2727 / ✪ U+272A, which share the emoji block but are not
  in Segoe UI Emoji).
- **Emote remove button was hidden under the picture**: the grid painted the ×
  before the image, so a sticker filling its cell covered the delete control.
  The image now draws first and the hover wash + × render on top.
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
- **Config file moved under the AtomChat data folder**: JSON settings now live
  at `<config>/atomchat/atomchat-client.json` (next to `emotes/`); debug avatar
  PNGs move to `<config>/atomchat/debug/`. No migration is performed — there
  are no released users yet, so an old `atomchat.json` is simply ignored.

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
