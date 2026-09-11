# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

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
