[简体中文](README.md) | [English](README_EN.md)

<h1 align="center"><img width="256" height="256" alt="logo" src="https://github.com/user-attachments/assets/e449c62e-644b-4c19-9fbc-431d7a899781" /></h1>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>为MC原版聊天框带来手机聊天APP版的体验</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1%20%7C%201.20.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-NeoForge-blue">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge-red">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20%7C%20Server-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Fabric Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=Fabric-1.21.1"></a>
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="NeoForge Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=NeoForge-1.21.1"></a>
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Forge Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=Forge-1.20.1"></a>
</p>

原子聊天（AtomChat）是基于 [E33Chat](https://github.com/E33EPUS/E33Chat) 理念而开发的全新聊天美化 mod，旨在把原版聊天屏改造成「手机 App」风格的独立聊天面板，元素包括：圆角气泡、自定义头像、真实玩家名、表情 / 颜文字 / 表情包、图片与 GIF 动图消息、服务端媒体托管、复制引用、多行输入与 QQ 式动效，以及除聊天界面外的更多界面。

渲染层使用 [Skija](https://github.com/HumbleUI/skija)，所有界面由矢量绘制，不依赖原版聊天纹理。

> 状态：**v0.2.9 已发布（Fabric / NeoForge 1.21.1，Forge 1.20.1）**。可从 [Releases](https://github.com/E33EPUS/AtomChat/releases) 下载；项目是有意做成 E33Chat 思路的干净重写，不是 E33Chat 的 fork。

---

## 目录

- [安装](#安装)
- [快速开始](#快速开始)
- [功能](#功能)
- [使用说明](#使用说明)
- [配置](#配置)
- [兼容性](#兼容性)
- [已知限制](#已知限制)
- [隐私与数据](#隐私与数据)
- [常见问题](#常见问题)
- [问题反馈](#问题反馈)
- [开发与构建](#开发与构建)
- [更新日志](#更新日志)
- [第三方许可证](#第三方许可证)
- [许可证](#许可证)

---

## 安装

| 依赖 | 类型 | 说明 |
|---|---|---|
| Minecraft | 必需 | 1.21.1（Fabric / NeoForge 版）或 1.20.1（Forge 版） |
| Fabric Loader | 必需（Fabric 版） | 0.16.0+ |
| Fabric API | 必需（Fabric 版） | 任意兼容 1.21.1 的版本 |
| NeoForge | 必需（NeoForge 版） | 21.1.235 |
| Forge | 必需（Forge 版） | 47.x（1.20.1） |
| Java | 必需 | Fabric / NeoForge 版需 21+，Forge 1.20.1 版需 17+ |

1. 从 [Releases](https://github.com/E33EPUS/AtomChat/releases) 下载对应平台的 JAR（`atomchat-Fabric-1.21.1-*.jar` / `atomchat-NeoForge-1.21.1-*.jar` / `atomchat-Forge-1.20.1-*.jar`），或按 [开发与构建](#开发与构建) 自行构建
2. 将 JAR 放入 `.minecraft/mods/`；若想让服务端托管图片 / GIF 并同步头像，把同一个 JAR 也放进服务端的 `mods/`
3. 启动游戏，按聊天键（默认 `T` / `/`）打开 AtomChat

---

## 快速开始

1. 打开聊天即可看到手机面板：顶部为「公屏」与系统时间，中部为消息列表，底部为输入栏
2. 输入文字回车发送；文字超过一行时输入栏会自动长高，仍可上下移动光标
3. 点 **图片图标** 选择本地图片，或直接 **拖图片进窗口 / Ctrl+V 粘贴**，上传后自动插入草稿
4. 点 **表情图标** 打开面板：`表情` / `颜文字` / `表情包` 三个标签页
5. 右键任意消息可 **复制** 或 **引用回复**；单击头像打开玩家档案页，双击头像触发 QQ 式戳一戳动画，右键头像则呼出菜单，选择 @ 提及，私聊，传送与屏蔽

---

## 功能

### 原版优化

- 💬 **聊天气泡与头像** — 你的消息显示在右侧，其他人的在左侧，附带真实玩家名称和圆形皮肤头像；皮肤头像自动从正版 / 离线皮肤解析降级
- 🧹 **简洁原版 HUD 占位符** — 在面板外部，图片代码显示为绿色 `[图片]`，引用显示为蓝色 `[引用]`，不再刷长串代码
- 📝 **多行输入** — 输入框最多显示两行，内容更长时内部滚动；按上/下箭头可在行间移动，而单行草稿则保留原版聊天历史记录
- 🧠 **消息捕获** — 真实玩家身份从 MessageHandler 的三个通道中捕获，支持花名服；未知消息会降级为系统文本
- 📑 **保留聊天记录** — 开启后按服务器 / 世界保存在磁盘，重进同一服务器恢复，可设置自动清理
- 🔃 **公屏消息分类** — 标题栏返回键旁的按钮在「全部 / 仅系统 / 仅玩家」间循环，图标随状态切换、过滤生效时呈强调色；纯视图过滤，未读角标与会话预览仍统计全部消息

### 特点

- 📱 **手机面板界面** — 原版聊天 HUD 打开期间隐藏，AtomChat 以独立面板呈现；面板模糊背景 + 半透明毛玻璃输入栏
- 🖼️ **自定义头像** — 本地选图裁剪成方形头像，档案页与自己的气泡即时显示；同一服务器装了 AtomChat 时自动同步给其他玩家（换头像即时广播刷新），无伴侣环境静默降级为皮肤
- 🔔 **通知横幅与音效** — 被 @、被引用、收到私聊时面板顶部弹出横幅（点击跳转原消息并高亮），配套原创合成的气泡提示音；设置 → 聊天 →「通知」可分别开关并调音量
- 🖼️ **图片消息** — 原生渲染 `[[CICode]]` 图片协议（与 E33Chat / ChatImage 互通），按原图比例显示，**GIF 动图在气泡内自动循环播放**；透明底 PNG / GIF 直接透出面板背景、不再垫灰底板；加载中显示占位文案；右键图片可保存原图到本地
- 📤 **本地图片发送** — FlatLaf 文件选择器默认“详细信息”视图并直接显示缩略图；支持拖放 / Ctrl+V 粘贴；上传后自动生成 CICode，服务端不可用时自动回退第三方图床
- 📦 **服务端媒体托管** — 服务端装了 AtomChat 时，图片与 GIF 直接上传到服务器并由服务器分发给其他玩家，不再依赖第三方图床：内容寻址去重、按需分块下发、只走游戏连接、**不开 HTTP 端口**；服务端未装 / 关闭托管 / 超限 / 上传失败时自动回退图床。同一个 `hostingEnabled` 总开关也管自定义头像同步
- 🎁 **服务端下发** — 同服务端时装了 AtomChat 的客户端进服会自动同步「本服内容」：管理员放在服务端 `config/atomchat/emotes/` 的表情包、写进服务端配置的常用语，以及自动读取的 `server-icon.png` 与 MOTD。表情面板多出只读的「本服」分区（含服名与同步状态），常用语面板多出只读的「本服」分组；传输逐文件 sha256 校验、只补差量（删掉一个表情下次进服只补那一个），客户端硬上限 200 个 / 16 MB，失败会写明原因进服务端日志。服务端配置可用 `/atomchat gui` 在游戏内直接改（OP 2 级，单人存档直接可用）
- 😀 **表情 / 颜文字 / 表情包** — 三个标签页带滑动指示器与全宽 push 切换动画；表情包从 `<config>/atomchat/emotes/` 持久化（png/jpg/jpeg/gif，最多 10 个），`+` 号选择图片，悬停 `×` 删除；gif 表情在面板里只显示首帧（保证格子流畅），发送出去后正常播放
- ⚡ **常用语** — 输入栏闪电按钮打开常用语列表；点选插入输入框（不直接发送），支持新增 / 行内编辑 / 删除，单条 ≤256 字符、最多 20 条
- 🔗 **富文本消息** — 玩家名/正文支持颜色、下划线、点击与悬停：`/tell`、坐标、FTB 接受/拒绝、外部链接均可点；裸 URL 自动转链接
- 📋 **复制 / 引用 / 保存** — 右键消息复制、引用；图片消息可保存原图；菜单带 20×20 SVG 线性图标
- 🧭 **头像菜单** — 右键头像可 @ 提及、跳转私聊、快速传送与屏蔽；单击头像进入个人档案，双击触发 QQ 式戳一戳
- 🪢 **私聊** — 聊天列表列出当前在线玩家，单击进入聊天界面，右键呼出菜单（个人档案 / 传送 / 屏蔽）
- 👥 **在线玩家与会话列表** — 会话列表按「公屏 → 在线玩家 → 最近离线」排序，玩家卡带真实 ID、皮肤头像、在线/离线状态点与未读红点；私聊走 `/msg`，分会话存储草稿与滚动位置，离线/屏蔽只读
- 🖥️ **档案详情页** — 从聊天或会话列表推入玩家档案：数据总览、复制按钮、角色标识；聊天与档案之间整页推入转场
- 🧭 **手机式导航** — 底部三个标签页（聊天 / 个人 / 设置）与页面级推入/弹出转场，公屏与私聊之间同款切换动画
- ⚙️ **设置页** — Win11 风格 2×2 磁贴主页（外观 / 聊天 / 隐私与屏蔽 / 关于），点击进入子页；开关为 iOS 比例滑块动画，全部选项**即时生效并立即写盘，无需重启**
- 🎛️ **可调项** — 背景模糊、界面动画（装饰动效总闸）、背景不透明度、面板宽度（400–600）、界面缩放（x0.75–x1.50，即时缩放整套 UI）、消息入场动画、双击头像戳一戳
- 🖼️ **自定义壁纸** — 从本地选一张图片作为面板背景，自动降采样（长边 ≤1024，后台解码不卡帧）；与背景模糊互斥，不透明度滑块用于压暗壁纸保证文字可读
- 🚫 **屏蔽管理** — 设置内可视化屏蔽名单（头像 + 名字 + 一键取消）；「隐藏被屏蔽玩家的消息」可关，关掉后公屏仍可见但私聊保持屏蔽
- 🎨 **主题与全量配色** — 主题预设（毛玻璃 / 现代）一键切换；设置页提供全部界面配色（面板背景 / 气泡 / 次要胶囊 / 文字 / 卡片 / 描边 / 强调色），颜色分组可折叠，每项带实时预览色块
- ⏱️ **时间戳分隔** — 消息列表首条消息始终显示时间戳胶囊，之后按可配置的时间间隔显示
- ✏️ **跨消息文字选择** — 在消息列表上跨多条消息拖选文字，Ctrl+C 一次复制多段；拖动不会误触点击
- 🛡️ **防刷屏与紧凑分组** — 连续相同消息合并为一条并显示次数；同一发送者 5 分钟内的连续消息只保留第一条头像与名字，后续间距收紧
- 🌐 **图片接收开关** — 关闭后不下载不缓存，显示绿色 `[图片]` 占位
- 🔄 **传送模式循环** — `/tp` / `/tpa` / `auto` 三档一键循环
- 🌍 **本地化** — 支持中英双语，切换MC系统语言即可生效
- ⌨️ **输入法与联机体验** — IMBlocker 命令态桥接、WATUT 输入中指示（对方正在输入）
- 🎨 **SVG 图标与统一动效** — 图片 / 表情 / 发送按钮为内嵌 SVG 线性图标
- 🛠️ **纯 Skia 渲染** — 圆角、阴影、滚动、文字全部矢量绘制；提供动画 / 布局 / Token 纯类与 JUnit 测试

---

## 使用说明

### 聊天与消息

- 自己的气泡靠右、他人靠左；名称贴在气泡边缘，头像与气泡顶对齐
- 单击头像：打开玩家档案详情页（300ms 内再次点击则为戳一戳）
- 双击头像：触发头像抖动（QQ 式 poke）
- 右键气泡：`复制` / `引用`；图片气泡额外显示 `保存`，可下载原图到本地
- 收到含 `[[CICode,url=...,name=...,w=...,h=...]]` 的消息会渲染为图片气泡；兼容旧版无尺寸代码

### 图片发送

- 点击图片按钮 → FlatLaf 选择器（默认打开 `Pictures` / `图片` 文件夹，带缩略图预览）
- 拖拽图片文件到游戏窗口，或复制图片后 `Ctrl+V`：自动上传并插入草稿
- 上传过程输入框占位显示「图片上传中…」；完成后按回车发送
- 服务端装了 AtomChat 且开启 `hostingEnabled` 时，图片与 GIF 会先上传到服务器（内容寻址，重复图片只存一份），消息里写入 `atomchat-media:<id>` 短链，其他玩家按需拉取；服务端未安装 / 关闭托管 / 超过 `maxFileKb` / 上传失败时自动回退图床
- 默认第三方图床为 uguu.se，链接约 3 小时过期；服务端托管的媒体（图片 / GIF / 转发的表情包）与头像默认保留 7 天，由服务端按 `maxTotalMb` / `maxAvatarTotalMb` 与 `retentionDays` 自动修剪

### 表情包

- 目录：`.minecraft/config/atomchat/emotes/`
- 支持 png / jpg / jpeg / gif，按文件名排序，**最多 20 个且面板可滚动**（gif 在面板里显示首帧，发送后播放）；本服下发的表情在下方独立的只读分区，不占这 20 个名额
- 在「表情包」标签页点末尾 `+` 添加；悬停缩略图显示 `×` 可删除
- 点击表情包会自动上传并插入草稿，然后关闭面板（一次一个）

### 语言切换

- AtomChat 使用 Minecraft 的语言文件：`assets/atomchat/lang/zh_cn.json` 与 `en_us.json`
- 在游戏设置切换语言后，标题、标签页、右键菜单、输入占位符、文件选择器文案会随之变化

---

## 配置

**推荐方式**：游戏内 `设置` 页（Win11 风格磁贴主页：外观 / 聊天 / 隐私与屏蔽 / 关于）。所有选项**即时生效并立即写盘，无需重启**，颜色项带预设色板、`+` 号自定义拾色器与实时预览。

高级方式：直接编辑 `.minecraft/config/atomchat/atomchat-client.json`（首次启动自动生成，手动修改后需重启游戏）。常用键：

| 键 | 默认值 | 说明 |
|---|---|---|
| `panelWidth` / `panelHeight` | `440.0` / `780.0` | 面板尺寸（设计像素，内部再乘 UI 缩放） |
| `panelOpacity` | `0.93` | 面板背景不透明度 |
| `uiScale` | `1.0` | 界面缩放（x0.75–x1.50） |
| `blurEnabled` | `true` | 面板背景高斯模糊（与壁纸互斥） |
| `animationEnabled` / `messageEntryAnimation` / `avatarPokeEnabled` | `true` | 动画总开关 / 消息入场 / 头像戳一戳 |
| `themeName` | — | 主题预设（frosted / modern） |
| `accentColor` | `0xFF4A90E2` | 强调色（发送按钮 / 引用条 / 滚动条等） |
| `ownBubbleColor` / `bubbleTextColor` | `0xFF1E90FF` / `0xFFFFFFFF` | 自己气泡底色 / 文字色 |
| `otherBubbleColor` / `otherBubbleTextColor` | `0xFF2C3E50` / `0xFFFFFFFF` | 他人气泡底色 / 文字色 |
| `secondaryCapsuleBg` / `secondaryCapsuleText` | `0x962C3E50` / `0xDCAAAABA` | 次要胶囊底色 / 文字色（系统消息、时间戳、引用胶囊共用） |
| `textPrimaryColor` / `textSecondaryColor` | `0xFFFFFFFF` / `0xDCAAAABA` | 界面主文字 / 次要文字 |
| `panelBgColor` / `panelOutlineColor` / `panelOutline` | `0xEE16191F` / `0xFFFFFFFF` / `true` | 面板背景 / 描边颜色 / 描边开关 |
| `timestampIntervalMinutes` | `5` | 时间戳分隔间隔（0 = 关闭） |
| `imageMessagesEnabled` | `true` | 图片消息接收开关 |
| `antiSpamEnabled` / `compactMessagesEnabled` | `true` / `false` | 防刷屏合并 / 紧凑消息分组（默认关） |
| `chatHistoryEnabled` / `historyRetentionDays` | `false` / `7` | 保留聊天记录 / 历史保留天数（0 = 永久，默认 7 天） |
| `teleportCommandMode` | `"auto"` | 传送命令模式（auto / tp / tpa） |
| `debug` | `false` | 调试输出 / 头像采样 PNG（写入 `config/atomchat/debug/`） |

### 存储路径

`.minecraft/config/atomchat/` 下存放 `atomchat-client.json` 与各数据子目录：

| 路径 | 内容 |
|---|---|
| `atomchat-client.json` | 主配置文件（首次启动自动生成） |
| `avatar/` | 自定义头像（256px PNG） |
| `emotes/` | 表情包（png / jpg / jpeg / gif，最多 10 个） |
| `wallpaper/` | 自定义壁纸 |
| `history/` | 聊天记录（各存档 / 服务器独立） |
| `debug/` | 调试模式下导出的头像采样 PNG |

此外，自动下载的聊天图片缓存与他人头像数据位于 `<游戏目录>/atomchat-data/`（上限 500 文件 / 100 MB，超过 7 天未使用也会自动清理，可在 设置 → 关于 中查看并清空）。服务端托管下来的媒体与头像则放在服务端自己的 `<服务端游戏目录>/atomchat-data/` 下。

### 服务端配置

服务端（或单人 / 局域网主机的服务端侧）装了 AtomChat 时，会在 `<服务端游戏目录>/config/atomchat/atomchat-server.json` 生成一份服务端配置（首次启动自动创建，手改后下次有客户端进服时生效）：

| 键 | 默认值 | 说明 |
|---|---|---|
| `hostingEnabled` | `true` | 总开关：本服务端是否托管聊天图片 / GIF 与玩家头像。关闭后客户端自动回退第三方图床、头像回退皮肤 |
| `maxFileKb` | `2048` | 单个托管文件的大小上限（KB），超出则回退图床 |
| `maxTotalMb` | `512` | 媒体库总容量上限（MB），超出后按最旧优先删除 |
| `maxAvatarTotalMb` | `64` | 头像库总容量上限（MB），超出后按最旧优先删除 |
| `retentionDays` | `7` | 托管媒体与头像的保留天数，`0` 表示永久保留 |
| `uploadCooldownMs` | `3000` | 同一玩家两次上传之间的最小间隔（毫秒） |
| `packEnabled` | `true` | 总开关：是否把本服的表情包 / 常用语 / 服务器标识下发给客户端 |
| `packMaxFiles` | `32` | 下发的表情包文件数上限 |
| `packMaxMb` | `8` | 下发的表情包总体积上限（MB） |
| `packName` | `""` | 客户端面板显示的服名；留空则用服务器 MOTD 清洗后的首行 |
| `phrases` | `[]` | 下发给客户端的常用语数组（最多 20 条，每条 ≤200 字） |

这些值也可以在游戏内用 `/atomchat gui`（OP 2 级；单人存档无需权限）直接修改：那个配置屏由**执行命令的玩家的客户端**绘制，所以专用服务器、面板服、单人、局域网都能用；控制台没有可绘制的界面，会提示需在游戏内执行。

服务端媒体存放在 `<服务端游戏目录>/atomchat-data/media/`，文件名即内容哈希、天然按内容去重。

---

## 兼容性

| 项 | 状态 |
|---|---|
| Fabric 1.21.1 | ✅ 支持 |
| NeoForge 1.21.1 | ✅ 支持 |
| Forge 1.20.1 | ✅ 支持 |
| Java | ✅ Fabric / NeoForge 版需 21+；Forge 1.20.1 版需 17+ |
| 服务端 | ✅ 无需安装（纯客户端）；服务端安装后激活**服务端媒体托管**与自定义头像同步 |
| `[[CICode]]` 图片协议 | ✅ 与 E33Chat / ChatImage 系互通 |
| 花名 / 昵称插件 | 🟡 尽力识别（点击私聊 / Tab 名 / 装饰名结构）；极端未知格式回退灰字 |
| 聊天头像（ChatHeads） | ✅ 兼容 |
| 聊天动画（Chat Animation）/ 同类型动画 Mod | ✅ 兼容 |
| EasyBot | 🟡 尽力识别（QQ 消息类型解析）；无法解析则回退灰字 |
| Quark | 🚫 不显示表情菜单，正尝试兼容 |
| 其他加载器 / 版本 | ❌ 目前仅支持 Fabric / NeoForge 的 1.21.1 与 Forge 的 1.20.1 |

---

## 已知限制

1. 支持 Fabric / NeoForge 1.21.1 与 Forge 1.20.1；Skija Windows x64 原生库已内置，Linux / macOS 尚未打包（无法运行）
2. 服务端媒体托管需要服务端也安装 AtomChat；未安装或关闭托管时图片仍走第三方图床 uguu.se（约 3 小时过期）。超过 `maxFileKb`（默认 2 MB）的文件不会再上传服务端而是直接回退图床，客户端也不会为它重新压缩
3. 暂无 E33Chat 的私聊侧边栏、搜索等能力；服务端格式模板已支持（手改 `chatTemplates` / `whisperTemplates`），按世界聊天记录持久化已内置（默认关闭）
4. 玩家身份解析为尽力而为：tell-click 结构捕获、离线 seen 缓存、ownDisplayName 多级降级；极端未知格式回退灰字
5. 聊天记录持久化默认关闭；开启后按服务器 / 世界保存在磁盘，重进同一服务器恢复，跨世界 / 服务器不会串台
6. 服务端媒体托管的端到端联调目前只做过「单人 / 局域网主机的服务端侧」一种场景，独立服务器（尤其 Linux，无内置 Skija 原生库）与两个客户端互相拉图尚未实测

---

## 隐私与数据

> [!WARNING]
> 你发送的本地图片会被上传到第三方图床（默认 uguu.se），或（服务端开启托管时）保存到你所在服务器的磁盘上，且他人可保存 / 转发。你的消息也可能被开启了聊天记录的用户保存。请勿发送敏感或私密内容。

- 模组不上传任何遥测 / 个人信息
- 图片上传仅在主动选择 / 粘贴 / 拖入图片时发生
- 服务端托管只通过游戏连接传输字节，不开放 HTTP 端口，也不会把媒体暴露给未连接的玩家
- 开启托管的服务端会在 `<服务端游戏目录>/atomchat-data/media/` 保留一份你的图片副本、在 `avatars/` 保存你上传的头像，两者都由服务端按容量（`maxTotalMb` / `maxAvatarTotalMb`）与保留天数 `retentionDays`（默认 7 天）自动修剪，管理员也可随时手动删除
- 本地配置与表情包仅存于 `.minecraft/config/atomchat/`，不会自动同步
- 皮肤头像解析会按玩家名 / UUID 请求 Minecraft 皮肤服务，属于原版同款行为

---

## 常见问题

**需要装服务端吗？** 不需要，AtomChat 是纯客户端模组。服务端（或单人 / 局域网主机的服务端侧）装了 AtomChat 后，会额外激活**服务端媒体托管**（图片与 GIF 走服务器、不依赖图床）与**自定义头像同步**。

**怎么发图片？** 点图片按钮选择本地图片，或拖图片进窗口 / Ctrl+V 粘贴；上传完成后自动插入草稿，再回车发送。

**图片是走图床还是走服务器？** 服务端装了 AtomChat 且 `hostingEnabled=true` 时优先走服务器，消息里是 `atomchat-media:<id>` 短链；服务端没装 / 关闭托管 / 文件超过 `maxFileKb` / 上传失败时自动回退 uguu.se 图床。服务端托管的内容由服务器按容量与时效自动清理。想确认实际走了哪条路，可以看日志：托管成功是 `Stored hosted media`，回退图床是 `Uploaded chat image to the external host`。

**为什么某条消息显示为灰色系统消息？** 客户端无法确定它是玩家消息时会保守归为系统灰字（例如昵称插件使用无法解析的格式）。

**表情包存在哪里？** `.minecraft/config/atomchat/emotes/`，最多 10 个，支持 png / jpg / jpeg / gif。

**怎么改颜色 / 大小？** 游戏内 `设置 → 外观` 即时调整（颜色项带预设与自定义拾色器）；也可以编辑 `config/atomchat/atomchat-client.json` 后重启游戏。

**可以放进整合包吗？** 可以。AtomChat 代码为 MIT，无需额外授权；若整合包分发 JAR，请保留 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 中的第三方声明。

---

## 问题反馈

若发现 bug，或想提建议，可到 [Issues](https://github.com/E33EPUS/AtomChat/issues) 反馈，或直接在百科 / 平台评论区留言。

---

## 开发与构建

```bash
./gradlew.bat build
```

产物位于 `build/libs/`：

- Fabric：`atomchat-Fabric-1.21.1-<version>.jar`
- NeoForge：`atomchat-NeoForge-1.21.1-<version>.jar`
- Forge：`atomchat-Forge-1.20.1-<version>.jar`（另有未内嵌依赖的 `-slim` 版本，发版请用不带 `-slim` 的那个）

```bash
./gradlew.bat test
```

运行 JUnit 测试（纯逻辑层：动画 / 布局 / 消息解析 / 表情包存储）。

主要模块：

- `AtomChatScreen` — Skia 自绘聊天面板（编排层）
- `UiLayout` / `UiTokens` / `UiMotion` — 布局数学、尺寸 Token、动画时长
- `chat/` — e33chat 思路移植的消息捕获 / 分类 / 呈现管线
- `emote/` — 表情包持久化与 Skia 图片缓存
- `mixin/` — 原版聊天捕获与 IME / 建议框接入

---

## 更新日志

完整变更见源码分支 [Fabric-1.21.1/CHANGELOG.md](https://github.com/E33EPUS/AtomChat/blob/Fabric-1.21.1/CHANGELOG.md)。

---

## 第三方许可证

AtomChat 自身代码以 [MIT](LICENSE) 发布，但分发的 JAR 中捆绑了以下第三方组件，各自保留其许可证：

| 组件 | 许可证 |
|---|---|
| Skija（Java 绑定） | Apache License 2.0 |
| HumbleUI types | Apache License 2.0 |
| FlatLaf | Apache License 2.0 |
| Skia（原生库） | BSD 3-Clause |

完整版权与许可证文本见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

## 许可证

[MIT License](LICENSE)

Copyright © 2026 E33EPUS
