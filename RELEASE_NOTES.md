# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

## v0.2.10

本版是「单分支多目标」重铸后的第一次发版，也是商店线上 0.2.9 之后的第一版。开发期内部号一度写到 0.2.91，但它从未发过版 —— 为了让商店版本号保持递增，这里回到 0.2.10：下面既包含那批修复，也包含此后落到 main 的修复。三个目标（Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1）现在由同一份源码产出。

修复：玩家卡片右键菜单的「取消屏蔽」点不到——绘制按 3 行、点击判定只按 2 行，第三行永远在判定区外；现在绘制与三个点击入口共用同一行数计算。
修复：别人 @ 你时名字前的称号颜色消失——NeoForge / Forge 的签名聊天捕获把 unsigned 富文本丢成了纯文本；现在改取 decoratedContent()，@ 提及动作改插真实玩家名，并在客户端用本地已知称号给 @ 提及重新着色。
修复：引用回复的正文样式丢失——正文此前由解析出的纯文本重建；现在从富文本行里 slice 正文，服务器样式（含 @ 提及颜色）保留，文本对不上时回退旧行为。
修复：设置页与个人资料页的列表打开时停在底部——滚动控制器一律「首帧贴底」（公屏要的正是这个），可这两页也想贴底；现在控制器带上下锚定策略，这两页改为顶部锚定，公屏行为不变。
修复：Forge 1.20.1 上点服务端下发的常用语没反应——该目标的点击分支里缺「插入服务端常用语」这一支（1.21.1 两端都有），落在空 default 上；现在补齐。

Fixed the player-card menu's unblock row being unclickable because the hit test used two rows while the drawing used three.
Fixed coloured @mentions losing their decoration: NeoForge/Forge signed-chat capture dropped unsigned rich content, so it now takes decoratedContent(), mention actions insert the real profile name, and local mentions are re-coloured from the locally known decoration.
Fixed quoted reply bodies losing their rich styles: the body is now sliced from the rich line instead of rebuilt from plain text, with the old literal fallback when the visible text does not match.
Fixed the settings and profile lists opening at the bottom: every scroll controller snapped to the newest entry on its first frame, which chat wants and those two lists do not, so the controller now takes an anchor policy and they pass top.
Fixed server-offered quick phrases doing nothing when clicked on Forge 1.20.1, whose click handling lacked the insert-from-server branch the other two targets have.
This is the first release from the single-branch multi-target line, which builds all three jars from one source; it renumbers to 0.2.10 because the internal 0.2.91 label was never released and store versions must keep increasing.

## v0.2.9

新增：**服务端下发**——客户端进服会拿到本服的表情包、常用语与服务器标识（`server-icon.png` + MOTD）。表情面板多出只读的「本服」分区、常用语面板多出只读的「本服」分组；传输走新的 `atomchat-dist` 通道，逐文件 sha256 校验、只补差量（删掉一个表情下次进服只补那一个），客户端硬上限 200 个 / 16 MB，失败会写明原因进服务端日志。
新增：**`/atomchat gui` 服务端配置屏**（OP 2 级，单人存档自动放行）——游戏内改开关、数值、面板显示名与服务端常用语；服务端重新校验值域，并用版本号拒绝过期编辑。
新增：**服务端托管数据默认 7 天清理**（`retentionDays` 由 30 改为 7，头像库新增 `maxAvatarTotalMb`），清理时机改为开服 + 每 5 分钟 + 上传后，掉线即清未完成的上传缓冲；客户端图片缓存同样 7 天未用自动清理。
更改：本地表情上限 10 → 20 且表情格可滚动；客户端新增「接收本服下发内容」隐私开关（默认开）；服务端新增 `packEnabled` / `packMaxFiles` / `packMaxMb` / `packName` / `phrases` 五个配置键。
修复：增量补差（客户端删过或改过已下发文件时）此前会因「清单里未下载的文件缺字节」整包失败；现在会先把保留的文件重新校验后补进包里。删掉一条服务端常用语保存后，面板里那条还会在（磁盘其实已存对）——常用语与服名随清单下发，面板读的是进服缓存；现在保存成功后客户端会重新握手，改配置的人立刻生效，其他人下次进服生效。细调：服务端表情格与本地一样有悬停高亮（只是没有删除按钮），服务端常用语与本地同色且同样有悬停高亮，服务器没有可下发内容时不显示任何服务端相关行；服务端配置屏改成两段小节、单位写进标签、毫秒冷却旁显示换算秒数，数值越界先在客户端拦下并指出是哪个字段。客户端常用语默认自带一条 /atomchat gui（种一次，删掉不再回来）。
修复：装了抢先初始化 AWT 的模组（如 TFC）时，图片 / 头像 / 壁纸的文件选择窗口打不开——Minecraft 的 `Main` 会把 `java.awt.headless` 写成 true，而 `GraphicsEnvironment` 的判定是一次性静态缓存，谁先碰 AWT 谁就把它锁死，之后 `new JFrame` 抛 `HeadlessException` 且被静默吞掉。现在由 Mixin 插件在所有模组构造之前先认领 AWT，仍失败会在日志与游戏内明确报错。
修复：保存服务端托管的图片必定失败（伪协议被当 HTTP url，抛 `invalid URI scheme`），建议文件名还含 Windows 非法字符；现在按 scheme 走同一条字节来源，文件名统一清洗。
修复：玩家自己的表情会被算进服务端下发包（下发源与本地表情目录原本是同一个文件夹），单机与 LAN 主机会把自己的收藏推给访客；下发源已独立为 `config/atomchat/server-emotes/`，专用服务器首次构建时自动搬运旧文件，单机 / LAN 不再外推。

Added: server-offered content - a client picks up the server's emote pack, phrases and identity (server-icon.png plus MOTD) on join. The emote panel gains a read-only "this server" section and the phrase panel a read-only group; the transfer runs over a new atomchat-dist channel with per-file SHA-256 verification and incremental fetch (deleting one emote costs one file next join), a client-side hard cap of 200 files / 16 MB, and a written reason in the server log for every failure.
Added: /atomchat gui - an in-game server settings screen (OP level 2, always allowed in single-player) for the master switches, the numeric limits, the panel name and the server phrases; the server re-validates every field and refuses stale edits by version.
Added: hosted data now defaults to a 7-day retention (retentionDays 30 to 7, plus a separate avatar store cap, maxAvatarTotalMb); the sweep runs at server start, every five minutes and after each upload, and a player leaving drops their unfinished upload buffers; the client image cache expires after seven days unused as well.
Changed: the local emote cap went from 10 to 20 with a scrollable grid; the client gained an "accept server packs" privacy switch (on by default); the server gained five config keys (packEnabled, packMaxFiles, packMaxMb, packName, phrases).
Fixed: an incremental sync (after the client deleted or edited an offered file) used to fail as a whole because the manifest files it did not download had no bytes; kept files are now re-verified and folded back in. Polished: server-emote cells highlight on hover like local ones (they just carry no delete button), server phrases use the same colour and the same hover highlight as local ones, and nothing server-related is shown when the server offers nothing. The server settings screen is grouped into two labelled sections with units spelled out and a live "3.0 s" twin for the millisecond cooldown, and out-of-range values are caught client-side and name the field. A fresh client config also starts with one quick phrase, /atomchat gui, seeded once so deleting it sticks. Saving now refreshes the editor's own panel immediately (others pick the change up on their next join).
Fixed: the image picker (send sticker / avatar / wallpaper) would not open once any mod reached AWT first - TFC does it through a java.awt.Color field on an enum. Minecraft's Main writes java.awt.headless=true and GraphicsEnvironment caches that answer, so the property write AtomChat performed afterwards was ignored and new JFrame threw HeadlessException, which the log swallowed; the player saw a button that did nothing. A mixin plugin now claims the AWT toolkit before any mod is constructed, and a failure is reported in the log and in game.
Fixed: saving a server-hosted image always failed - an atomchat-media: url was handed to the HTTP client and rejected as an invalid URI scheme, and the suggested file name carried a colon. Bytes now come from the same source the renderer uses and file names are sanitised.
Fixed: a player's own emotes leaked into the pack a server offers, because the pack source and the local emote folder were the same directory; single-player and LAN hosts pushed their own stickers to guests. The pack source is now config/atomchat/server-emotes/, with a one-time carry-over on dedicated servers and nothing moved on a client.

## v0.2.8

修复：Forge 1.20.1 打开聊天面板后画面整片变黑（面板短短一瞬间还能看到，随后黑屏；游戏不崩溃，仍能打字、发图）——模糊 pre-pass 用裸 GL 写状态，绕过了 Blaze3D「缓存相等就不碰驱动」的门控 setter，于是驱动与游戏对当前纹理单元的认知长期不一致，之后原版与 Embeddium 的纹理绑定全部落到错误的单元；现在状态改为「先写驱动、再镜像缓存」，并把帧缓冲、视口与 unpack 像素状态一并保存还原。
修复：模糊「被请求但这一帧没落地」时面板改用不透明底色兜底，不再用 93% 半透明的近黑底色（面板在窗口里几乎铺满时看起来就是整屏黑）；模糊失败会打一条节流 GL 错误警告并自动回退。
修复：开面板滑入动画期间，面板淡入图层会裁掉自己的左边缘（图层余量 32px、滑入起点 36px），现在按滑动距离取并集。
加固：头像取色读取皮肤纹理的裸纹理绑定同样镜像 Blaze3D 缓存；以上修复三端同源，Fabric 1.21.1 与 NeoForge 1.21.1 一并生效。

Fixed: opening the chat panel on Forge 1.20.1 turned the whole screen black (the panel was visible for an instant, then the screen went black; the game did not crash and typing/image uploads still worked). The blur pre-pass wrote GL state raw, bypassing Blaze3D's cache-gated setters ("equal cache means do not touch the driver"), so the driver and the game disagreed about the active texture unit and every later vanilla/Embeddium bind landed on the wrong one. State is now written to the driver and mirrored back into the cache, and the framebuffer, viewport and unpack pixel state are saved and restored too.
Fixed: when the blur is requested but does not land this frame the panel now falls back to an opaque background instead of a 93%-opaque near-black tint that read as a full-screen black panel; a failed blur logs one throttled GL error warning and falls back.
Fixed: the panel's fade layer clipped its own left edge while the open animation slid it in (32px of layer slack against a 36px slide); the layer now spans the slide.
Hardened: the avatar skin read-back mirrors the texture cache too. All of the above are shared across the three builds, so Fabric and NeoForge 1.21.1 get them as well.

## v0.2.7

新增：服务端媒体托管 —— 服务端 `config/atomchat/atomchat-server.json` 的 `hostingEnabled`（默认开）决定聊天图片与 GIF 是托管在服务器上还是继续走外部图床；托管时客户端分块上传、服务端按 sha256 去重存进 `atomchat-data/media/`、消息里写 `atomchat-media:<id>` 短链、接收方按需分块拉取，全程走游戏连接、不开 HTTP 端口，上传有大小上限 / 魔数校验 / 限速 / 总量与时效修剪，任何失败都自动回退图床。
新增：GIF 动图 —— 聊天图片与表情包支持 `.gif`，视口内循环播放（384px / 120 帧 / 800 万像素预算，超出截断或降级首帧）。
修复：引用自己的消息时气泡里多出「引用 @名: 文本」前缀；透明底图片 / 动图背后透出灰底板；点击面板下半区会碰到看不见的原版聊天（触发点击事件、吞掉点击刷新未读、滚轮滚隐藏历史）。三条都只影响显示与输入，不改消息内容。
更改：表情面板降采样到 128px 且只显示首帧，格子不再逐帧播放动图（发送出去仍播放）。

Added: server-side media hosting. The server config `config/atomchat/atomchat-server.json` (`hostingEnabled`, on by default) decides whether chat images and GIFs are hosted on the server or keep going to the external image host. When hosting, the client uploads in chunks, the server deduplicates by sha256 into `atomchat-data/media/`, messages carry an `atomchat-media:<id>` link, and receivers fetch the bytes in chunks over the game connection - no HTTP port is ever opened. Uploads are size-capped, magic-checked, rate-limited and pruned by total size and age, and any failure falls back to the external host automatically.
Added: animated GIFs for chat images and emotes, looping in the viewport (384px / 120 frames / 8M pixel budget, trimmed or degraded to the first frame beyond that).
Fixed: the quote prefix ("quote @name: text") leaking into your own bubble, transparent PNG/GIFs rendering on a grey plate, and clicks in the lower half of the panel reaching the hidden vanilla chat (firing a hidden line's click event, swallowing the click to flush unread messages, or scrolling the hidden history with the wheel).
Changed: the emote grid now decodes at 128px and shows the first frame only instead of animating every cell (sent emotes still animate).

## v0.2.6.1

新增：Forge 1.20.1 构建 —— 与 Fabric / NeoForge 1.21.1 功能对齐的第三个平台版本，Skija 与 FlatLaf 以 JarJar 内嵌，安装方式同样是往 mods 里丢一个 jar。
修复：服务器给玩家名加前缀（队伍 / 称号）、消息又走无身份中继（NCR 类）时，自己发的消息会回显成两条 —— 三端现在都在发送时记下实际内容，收到回显后按内容加 10 秒时效判定并吞掉自己那一条；他人消息与 /team 一类命令反馈不受影响。

Added: a Forge 1.20.1 build, the third platform target, feature-matched with the Fabric and NeoForge 1.21.1 builds; Skija and FlatLaf ride along as JarJar-embedded jars, so it installs the same way, one jar into mods.
Fixed: own messages were echoed back twice when the server decorates player names (team or title prefix) and the relay strips the channel identity (NoChatReports-style); all three builds now record what they sent and swallow the matching line within a 10-second window, leaving other players' messages and command feedback alone.

## v0.2.6-hotfix

修复：Fabric 端打开聊天面板后，只要列表里出现自己发的消息（文字或图片）就会崩溃退出（本次仅涉及 Fabric 构建；NeoForge 版功能与 0.2.6 完全一致，版本号同步仅为让两个 jar 在同一 Release 中对齐）。原因是面板的匿名内部类直接读取父类 Screen 的 protected client 字段——开发环境里两者同包所以合法，Fabric 把 Minecraft 重映射到另一个包后这条读取不再被允许，运行时抛 IllegalAccessError。Fabric 端现在持有自己的 client 句柄，与 NeoForge 端写法一致。

Fixed: a Fabric-only crash when the chat panel rendered one of your own messages, text or image (the NeoForge build is functionally identical to 0.2.6 and only carries the matching version number so both jars ship in one release) — an anonymous inner class read the inherited protected client field of Screen, which is legal only while both share a package in dev; Fabric remaps Minecraft into a different package at runtime, so the JVM rejects the read with IllegalAccessError. The Fabric build now keeps its own client handle, matching the NeoForge build.

## v0.2.6

新增：公屏消息分类过滤 —— 标题栏返回键旁的按钮在「全部 / 仅系统 / 仅玩家」间循环，图标随状态切换、过滤生效时呈强调色；纯视图过滤，未读角标与会话预览仍统计全部消息。
修复：NCR 类中继服务器上自己气泡显示裸名；发送者名字行继承服务器的下划线与点击事件；复读合并没有时间窗、且合并会让文本选中与跳转高亮失效；拖选中滚动后 Ctrl+C 复制为空；emoji 被代理对劈半产生乱码。
更改：消息折行加入布局缓存，长列表滚动与打开面板明显更流畅。

Added: a public-feed filter button next to the header back arrow, cycling all / system-only / players-only (icon changes per state, accented while filtering). View-only — unread badges and previews still count every message.
Fixed: bare own names on relay servers (NCR-style), sender name rows inheriting server underlines and click events, anti-spam merging with no time window (and merging dropping text selections and jump highlights), empty Ctrl+C after scrolling during a selection, and emoji split in half by surrogate pairs.
Changed: layout cache for message wrapping, making long-list scrolling and panel opening noticeably smoother.

## v0.2.5-hotfix

修复：部分 NeoForge 整合包注册表冻结导致整个 mod 加载失败（音效注册改走 RegisterEvent，失败仅损失提示音）。

Fixed: a registry-freeze crash that broke the whole mod in some NeoForge modpacks — sound registration now goes through RegisterEvent and degrades to a lost cue sound only.

## v0.2.5

新增：消息通知横幅、提示音、通知设置分组。
修复：头像同步误判停用、发送者名字丢色、面板背景色失效、装饰名泄漏尖括号、横幅把界面推出屏幕。

Added: message notification banners, cue sounds, notification settings group.
Fixed: avatar-sync misdetection, sender-name color loss, ineffective panel background color, decorated-name bracket leaks, banner pushing the UI off-screen.
