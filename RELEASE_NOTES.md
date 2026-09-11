# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

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
