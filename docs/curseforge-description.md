[中文](#中文) | [English](#english)

# AtomChat

**给你的 Minecraft 原版聊天框换上「手机聊天 App」的体验。** 纯 Skija 矢量渲染，与 E33Chat / ChatImage 的 `[[CICode]]` 图片协议互通。

- 源码与文档：https://github.com/E33EPUS/AtomChat
- 问题反馈：https://github.com/E33EPUS/AtomChat/issues
- 完整更新日志：https://github.com/E33EPUS/AtomChat/blob/Fabric-1.21.1/CHANGELOG.md

---

## 中文

### 这是什么

AtomChat 把原版的聊天屏改造成一个独立的「手机 App」风格聊天面板：圆角气泡、真实玩家名与皮肤头像、表情 / 颜文字 / 表情包、图片与 **GIF 动图**消息、服务端媒体托管、引用回复、多行输入、通知横幅与音效，以及手机式的页面推入 / 弹出转场。整个界面由 [Skija](https://github.com/HumbleUI/skija) 矢量自绘，不依赖原版聊天纹理。

它不是 E33Chat 的 fork，而是同一理念下的干净重写；图片沿用 `[[CICode]]` 协议，与 E33Chat / ChatImage 系互通。

### 支持平台

- **Fabric 1.21.1** —— 需要 Fabric API，Java 21+
- **NeoForge 1.21.1** —— Java 21+
- **Forge 1.20.1** —— Java 17+
- 客户端模组，单人世界与多人服务器都能用；服务端也装一份可额外启用 **服务端媒体托管** 与 **自定义头像同步**
- ⚠️ 目前只打包了 **Windows x64** 的 Skija 原生库，Linux / macOS 暂时无法运行，后续版本再补

### 功能

**聊天与消息**

- 自己的消息靠右、他人靠左，圆角气泡 + 真实玩家名 + 圆形皮肤头像；皮肤解析支持正版与离线
- 真实身份从三个消息通道捕获，支持花名 / 昵称服；未知格式保守降级为灰字而不是猜错
- 复读自动合并计数、同一发送者连续消息紧凑分组、跨消息拖选 Ctrl+C 复制
- 引用回复、右键复制 / 保存、裸 URL 自动转链接，玩家名支持 `/tell`、坐标、FTB 按钮等点击交互
- 私聊走 `/msg`，会话列表按「公屏 → 在线玩家 → 最近离线」排序，带未读红点与分会话草稿
- 可选聊天记录持久化（默认关闭），按服务器 / 世界分开保存，重进自动恢复

**图片与动图**

- 原生渲染 `[[CICode]]` 图片协议，按原图比例显示；**GIF 动图在气泡内自动循环播放**，透明底 PNG / GIF 直接透出面板背景
- 本地图片：文件选择器 / 拖放进窗口 / `Ctrl+V` 粘贴，上传后自动插入草稿；右键图片可保存原图
- 表情包支持 png / jpg / jpeg / gif（面板里显示首帧保证流畅，发送后正常播放）

**服务端媒体托管（0.2.7 新增）**

- 服务端也装了 AtomChat 时，图片与 GIF 直接上传到服务器并由服务器分发给其他玩家，**不再依赖第三方图床**
- 内容寻址（sha256）去重，相同的图只存一份；接收方按需分块拉取；全部走游戏连接，**不开 HTTP 端口、不暴露公网**
- 服务端具备大小上限、图片魔数校验、按玩家上传限速，并按总容量与时效自动修剪
- 服务端未安装 / 关闭托管 / 文件超限 / 上传失败时，自动回退第三方图床 uguu.se，行为与旧版一致
- 同一个总开关 `hostingEnabled` 也管自定义头像同步

**界面与体验**

- 手机面板：底部三标签页、页面级推入 / 弹出转场、面板模糊背景与毛玻璃输入栏
- 通知：被 @、被引用、收到私聊时顶部横幅（点击跳转并高亮）+ 原创合成提示音，可分别开关与调音量
- 设置页 Win11 风格磁贴主页，外观 / 聊天 / 隐私屏蔽 / 关于四组，全部选项即时生效并立即写盘
- 主题预设与全量配色自定义、自定义壁纸、界面缩放、时间戳分隔、公屏消息分类过滤
- 中英双语，跟随游戏语言切换

### 安装

1. 按你的平台下载对应 jar：Fabric / NeoForge 用 1.21.1，Forge 用 1.20.1
2. 放进 `.minecraft/mods/`（Fabric 版还需要 Fabric API）
3. 启动游戏，按聊天键（默认 `T` / `/`）打开面板

想让图片走服务器而不是图床，就把同一个 jar 也放进服务端的 `mods/`（单人 / 局域网主机同样有效）；服务端首次启动会生成 `config/atomchat/atomchat-server.json`。

### 服务端配置

服务端配置文件：`<服务端游戏目录>/config/atomchat/atomchat-server.json`

- `hostingEnabled`（默认 `true`）—— 总开关：是否托管聊天图片 / GIF 与玩家头像
- `maxFileKb`（默认 `2048`）—— 单个托管文件大小上限，超出则回退图床
- `maxTotalMb`（默认 `512`）—— 媒体库总容量上限，超出后按最旧优先删除
- `retentionDays`（默认 `30`）—— 托管文件保留天数，`0` 表示永久
- `uploadCooldownMs`（默认 `3000`）—— 同一玩家两次上传的最小间隔

客户端配置在游戏内的「设置」页调整，全部即时生效。

### 常见问题

**需要装服务端吗？** 不需要，AtomChat 是纯客户端模组。服务端也装一份，才会额外启用服务端媒体托管与头像同步。

**图片到底走服务器还是图床？** 服务端装了 AtomChat 且托管开启时优先走服务器（消息里是 `atomchat-media:` 短链）；否则自动回退 uguu.se。服务端日志里托管成功打 `Stored hosted media`，回退图床打 `Uploaded chat image to the external host`。

**图床链接会过期吗？** 图床（uguu.se）约 3 小时过期；服务端托管的内容不过期，由服务端按容量与时效自动清理。

**为什么有消息显示成灰色？** 客户端无法确定它是不是玩家发言时会保守归为系统消息（例如昵称插件的特殊格式），宁可不误判。

**能放进整合包吗？** 可以，代码为 MIT，无需额外授权；分发 jar 时请保留第三方许可声明（见 `THIRD_PARTY_NOTICES.md`）。

### 许可与第三方

AtomChat 自身代码以 **MIT** 发布。分发的 jar 内嵌了 Skija（Apache-2.0）、HumbleUI types（Apache-2.0）、FlatLaf（Apache-2.0）与 Skia 原生库（BSD-3-Clause），各自的版权与许可证文本见仓库中的 `THIRD_PARTY_NOTICES.md`。

---

## English

### What is this

AtomChat turns the vanilla chat screen into a standalone phone-app style chat panel: rounded bubbles, real player names and skin avatars, emoji / kaomoji / sticker packs, image and **animated GIF** messages, server-side media hosting, quoting, multi-line input, notification banners and cues, and phone-style push/pop page transitions. The whole UI is vector-drawn with [Skija](https://github.com/HumbleUI/skija) instead of vanilla chat textures.

It is a clean rewrite in the spirit of E33Chat, not a fork, and it keeps the `[[CICode]]` image protocol, so it interoperates with the E33Chat / ChatImage family.

### Supported platforms

- **Fabric 1.21.1** — Fabric API required, Java 21+
- **NeoForge 1.21.1** — Java 21+
- **Forge 1.20.1** — Java 17+
- Client mod: works in single player and on any server. Installing it on the server additionally enables **server-side media hosting** and **custom-avatar sync**
- ⚠️ Only the **Windows x64** Skija native is bundled today; Linux and macOS cannot run it yet

### Features

**Chat**

- Own messages on the right, others on the left, with rounded bubbles, real player names and round skin avatars (online and offline profiles)
- Player identity captured from all three vanilla message channels, with nickname-server support; unknown formats degrade to gray system text instead of being guessed wrong
- Duplicate-message merging with a counter, compact same-sender groups, cross-message drag selection with Ctrl+C
- Quote replies, right-click copy / save, automatic link detection, clickable player names (`/tell`, coordinates, FTB buttons)
- Private chat over `/msg` with a conversation list (public → online → recent offline), unread badges and per-conversation drafts
- Optional chat-history persistence (off by default), stored per server/world and restored on rejoin

**Images and GIFs**

- Native `[[CICode]]` rendering at the source aspect ratio; **animated GIFs loop inside the bubble**; transparent PNG / GIFs blend with the panel instead of showing a grey plate
- Send local images from the file picker, by dragging into the window or with `Ctrl+V`; right-click an image bubble to save the original
- Sticker packs support png / jpg / jpeg / gif (the grid shows the first frame to stay smooth; sent stickers animate)

**Server-side media hosting (new in 0.2.7)**

- When the server also runs AtomChat, images and GIFs upload to the server and are distributed from there — **no third-party image host needed**
- Content-addressed (sha256) deduplication, chunked on-demand delivery, everything over the game connection with **no HTTP port opened**
- The server enforces a size cap, image magic-byte checks and a per-player upload rate limit, and prunes the store by total size and age
- If the server lacks the mod, hosting is off, the file is oversized or the upload fails, it automatically falls back to the external host uguu.se
- The same `hostingEnabled` master switch also governs custom-avatar sync

**Interface**

- Phone panel with three bottom tabs, page-level push/pop transitions, blurred panel and a frosted-glass composer
- Notification banners with an original synthesized cue for @mentions, quotes and whispers, individually switchable with a volume slider
- Windows 11 style settings page (Appearance / Chat / Privacy & blocking / About); every option applies and persists instantly
- Theme presets and full colour control, custom wallpaper, UI scale, time dividers, public-feed filter
- Simplified Chinese and English, following the game language

### Installation

1. Download the jar for your platform: Fabric / NeoForge 1.21.1 or Forge 1.20.1
2. Drop it into `.minecraft/mods/` (the Fabric build also needs Fabric API)
3. Launch the game and press the chat key (default `T` / `/`)

To host media on the server instead of the image host, put the same jar into the server's `mods/` too (this also works for a single-player / LAN host). The server creates `config/atomchat/atomchat-server.json` on first launch.

### Server configuration

`<server game dir>/config/atomchat/atomchat-server.json`

- `hostingEnabled` (default `true`) — master switch for hosting chat images / GIFs and player avatars
- `maxFileKb` (default `2048`) — largest single hosted file; anything bigger falls back to the image host
- `maxTotalMb` (default `512`) — total media store budget; the oldest files are deleted first
- `retentionDays` (default `30`) — how long hosted files are kept; `0` keeps them forever
- `uploadCooldownMs` (default `3000`) — minimum interval between two uploads from one player

Client options live in the in-game Settings page and apply instantly.

### FAQ

**Do I need the mod on the server?** No. AtomChat is client-only; a server install only adds media hosting and avatar sync.

**Does an image go to the server or to the image host?** With AtomChat on the server and hosting enabled the server wins (messages carry an `atomchat-media:` link); otherwise it falls back to uguu.se automatically. The server log prints `Stored hosted media` for hosting and `Uploaded chat image to the external host` for the fallback.

**Do the links expire?** uguu.se links expire after roughly 3 hours; server-hosted media does not expire and is pruned by the server's size and age settings.

**Can I include this in a modpack?** Yes — the code is MIT and needs no extra permission; keep the third-party notices (`THIRD_PARTY_NOTICES.md`) when redistributing the jar.

### License

AtomChat's own code is **MIT**. The distributed jar bundles Skija (Apache-2.0), HumbleUI types (Apache-2.0), FlatLaf (Apache-2.0) and the Skia native library (BSD-3-Clause); see `THIRD_PARTY_NOTICES.md` in the repository for the full texts.
