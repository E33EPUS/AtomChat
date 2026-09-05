[简体中文](README.md) | [English](README_EN.md)

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>为MC原版聊天框带来聊天APP版的体验</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-21%2B-yellow">
  <img alt="Version" src="https://img.shields.io/badge/Version-0.1.7-informational">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

AtomChat是基于 [E33Chat](https://github.com/E33EPUS/E33Chat) 理念而开发的全新聊天美化mod，旨在把原版聊天屏改造成「手机 App」风格的独立聊天面板，元素包括：圆角气泡、头像、真实玩家名、表情 / 颜文字 / 表情包、图片消息、复制引用、多行输入与 QQ 式动效。

渲染层使用 [Skija](https://github.com/HumbleUI/skija)

所有界面由矢量绘制，不依赖原版聊天纹理。

> 状态：**v0.1.7 已发布**。可从 [Releases](https://github.com/E33EPUS/AtomChat/releases) 下载；项目是有意做成 E33Chat 思路的干净重写，不是 E33Chat 的 fork。

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
- [开发与构建](#开发与构建)
- [更新日志](#更新日志)
- [第三方许可证](#第三方许可证)
- [许可证](#许可证)

---

## 安装

| 依赖 | 类型 | 说明 |
|---|---|---|
| Minecraft | 必需 | 1.21.1 |
| Fabric Loader | 必需 | 0.16.0+ |
| Fabric API | 必需 | 任意兼容 1.21.1 的版本 |
| Java | 必需 | 21+ |

1. 从 [Releases](https://github.com/E33EPUS/AtomChat/releases) 下载 v0.1.7 JAR，或按 [开发与构建](#开发与构建) 自行构建
2. 将 JAR 放入 `.minecraft/mods/`
3. 启动游戏，按聊天键（默认 `T` / `/`）打开 AtomChat

---

## 快速开始

1. 打开聊天即可看到手机面板：顶部为「世界频道」与系统时间，中部为消息列表，底部为输入栏
2. 输入文字回车发送；文字超过一行时输入栏会自动长高，仍可上下移动光标
3. 点 **图片图标** 选择本地图片，或直接 **拖图片进窗口 / Ctrl+V 粘贴**，上传后自动插入草稿
4. 点 **表情图标** 打开面板：`表情` / `颜文字` / `表情包` 三个标签页
5. 右键任意消息可 **复制** 或 **引用回复**；点头像 `@`，双击头像触发 QQ 式戳一戳动画

---

## 功能

- 📱 **手机面板界面** — 原版聊天 HUD 打开期间隐藏，AtomChat 以独立面板呈现；面板模糊背景 + 半透明毛玻璃输入栏
- 💬 **聊天气泡与头像** — 自己的消息靠右、他人靠左，带头像与玩家名；皮肤头像自动从正版 / 离线皮肤解析降级
- 🖼️ **图片消息** — 原生渲染 `[[CICode]]` 图片协议（与 E33Chat / ChatImage 互通），按原图比例显示；加载中显示占位文案；右键图片可保存原图到本地
- 📤 **本地图片发送** — FlatLaf 文件选择器默认“详细信息”视图并直接显示缩略图；支持拖放 / Ctrl+V 粘贴；上传到图床后自动生成 CICode
- 🔗 **富文本消息** — 玩家名/正文支持颜色、下划线、点击与悬停：`/tell`、坐标、FTB 接受/拒绝、外部链接均可点；裸 URL 自动转链接
- 🧹 **原版 HUD 占位** — 面板外的原版聊天栏把图片代码显示为绿色 `[图片]`，引用显示为蓝色 `[引用]`，不再刷长串代码
- 😀 **表情 / 颜文字 / 表情包** — 三个标签页带滑动指示器与全宽 push 切换动画；表情包从 `<config>/atomchat/emotes/` 持久化（png/jpg/jpeg，最多 10 个），`+` 号选择图片，悬停 `×` 删除
- 📋 **复制 / 引用 / 保存** — 右键消息复制、引用；图片消息可保存原图；菜单带 20×20 SVG 线性图标
- 🧭 **手机式导航** — 底部三个标签页（聊天 / 个人 / 设置）与页面级推入/弹出转场，公屏与私聊之间同款切换动画
- 👥 **在线玩家与私聊** — 会话列表按「公屏 → 在线玩家 → 最近离线」排序，玩家卡带真实 ID、皮肤头像、在线/离线状态点与未读红点；私聊走 `/msg`，分会话存储草稿与滚动位置，离线/屏蔽只读
- ⚙️ **设置页** — Win11 风格 2×2 磁贴主页（外观 / 聊天 / 隐私与屏蔽 / 关于），点击进入子页；开关为 iOS 比例滑块动画，全部选项**即时生效并立即写盘，无需重启**
- 🎛️ **可调项** — 背景模糊、界面动画（装饰动效总闸）、背景不透明度、面板宽度（400–600）、界面缩放（x0.75–x1.50，即时缩放整套 UI）、消息入场动画、双击头像戳一戳
- 🖼️ **自定义壁纸** — 从本地选一张图片作为面板背景，自动降采样（长边 ≤1024，后台解码不卡帧）；与背景模糊互斥，不透明度滑块用于压暗壁纸保证文字可读
- 🚫 **屏蔽管理** — 设置内可视化屏蔽名单（头像 + 名字 + 一键取消）；「隐藏被屏蔽玩家的消息」可关，关掉后公屏仍可见但私聊保持屏蔽
- ✏️ **多行输入** — 输入框最高两行，超出后内部滚动；Up/Down 在行间移动光标，单行时沿用原版聊天历史
- 🎨 **SVG 图标与统一动效** — 图片 / 表情 / 发送按钮为内嵌 SVG 线性图标
- 🌍 **本地化** — 支持中英双语，切换MC系统语言即可生效
- 🧠 **消息捕获** — 从 MessageHandler 三层通道捕获真实玩家 UUID / 名字 / 装饰名，支持花名服与系统灰字兜底
- 🛠️ **纯 Skia 渲染** — 圆角、阴影、滚动、文字全部矢量绘制；提供动画 / 布局 / Token 纯类与 JUnit 测试

---

## 使用说明

### 聊天与消息

- 自己的气泡靠右、他人靠左；名称贴在气泡边缘，头像与气泡顶对齐
- 点击头像：向输入框插入 `@玩家名 `
- 双击头像：触发头像抖动（QQ 式 poke）
- 右键气泡：`复制` / `引用`；图片气泡额外显示 `保存`，可下载原图到本地
- 收到含 `[[CICode,url=...,name=...,w=...,h=...]]` 的消息会渲染为图片气泡；兼容旧版无尺寸代码

### 图片发送

- 点击图片按钮 → FlatLaf 选择器（默认打开 `Pictures` / `图片` 文件夹，带缩略图预览）
- 拖拽图片文件到游戏窗口，或复制图片后 `Ctrl+V`：自动上传并插入草稿
- 上传过程输入框占位显示「图片上传中…」；完成后按回车发送
- 当前默认图床为 uguu.se，链接约 3 小时过期；本模组暂无服务端媒体托管

### 表情包

- 目录：`.minecraft/config/atomchat/emotes/`
- 支持 png / jpg / jpeg，按文件名排序，最多 10 个
- 在「表情包」标签页点末尾 `+` 添加；悬停缩略图显示 `×` 可删除
- 点击表情包会自动上传并插入草稿，然后关闭面板（一次一个）

### 语言切换

- AtomChat 使用 Minecraft 的语言文件：`assets/atomchat/lang/zh_cn.json` 与 `en_us.json`
- 在游戏设置切换语言后，标题、标签页、右键菜单、输入占位符、文件选择器文案会随之变化

---

## 配置

配置文件：`.minecraft/config/atomchat/atomchat-client.json`（首次启动自动生成，修改后需重启游戏）

| 键 | 默认值 | 说明 |
|---|---|---|
| `panelWidth` | `420.0` | 面板宽度（设计像素，内部再乘 UI 缩放） |
| `panelHeight` | `780.0` | 面板高度 |
| `blurEnabled` | `true` | 面板背景高斯模糊（raw GL + core shader） |
| `animationEnabled` | `true` | 动画总开关 |
| `debug` | `false` | 调试输出 / 头像采样 PNG（写入 `config/atomchat/debug/`） |
| `accentColor` | `0xFF4A90E2` | 强调色（发送按钮 / 引用条等） |
| `ownBubbleColor` | `0xFF4A90E2` | 自己气泡颜色 |
| `otherBubbleColor` | `0xFF343A44` | 他人气泡颜色 |
| `panelBgColor` | `0xEE16191F` | 面板背景色 |
| `textPrimaryColor` | `0xFFFFFFFF` | 主文字颜色 |
| `textSecondaryColor` | `0xDCAAAABA` | 次要文字颜色 |

---

## 兼容性

| 项 | 状态 |
|---|---|
| Fabric 1.21.1 | ✅ 支持 |
| Java 21+ | ✅ 必需 |
| `[[CICode]]` 图片协议 | ✅ 与 E33Chat / ChatImage 系互通 |
| 花名 / 昵称插件 | 🟡 尽力识别（点击私聊 / Tab 名 / 装饰名结构）；极端未知格式回退灰字 |
| 服务端 | ✅ 无需安装（纯客户端） |
| 其他加载器 / 版本 | ❌ 当前仅 Fabric 1.21.1 |

---

## 已知限制

1. 仅 Fabric 1.21.1，Skija Windows x64 原生库已内置；Linux / macOS 尚未打包
2. 无 GUI 配置界面，设置需手动编辑 `config/atomchat/atomchat-client.json`
3. 图片默认上传第三方图床 uguu.se，约 3 小时过期；暂无服务端媒体托管
4. 无 E33Chat 的服务端模板、私聊侧边栏、搜索、通知横幅、聊天历史持久化等能力
5. 玩家身份解析为尽力而为：tell-click 结构捕获、离线 seen 缓存、ownDisplayName 多级降级、私聊 / whisper 分类尚未实现
6. 聊天历史仅保存在内存（上限 500 条），重启不保留；跨世界 / 服务器不会自动清空

---

## 隐私与数据

> [!WARNING]
> 你发送的本地图片会被上传到第三方图床（默认 uguu.se）。请勿发送敏感或私密内容。

- 模组不上传任何遥测 / 个人信息
- 图片上传仅在主动选择 / 粘贴 / 拖入图片时发生
- 本地配置与表情包仅存于 `.minecraft/config/atomchat/`，不会自动同步
- 皮肤头像解析会按玩家名 / UUID 请求 Minecraft 皮肤服务，属于原版同款行为

---

## 常见问题

**需要装服务端吗？** 不需要，AtomChat 是纯客户端模组。

**怎么发图片？** 点图片按钮选择本地图片，或拖图片进窗口 / Ctrl+V 粘贴；上传完成后自动插入草稿，再回车发送。

**为什么某条消息显示为灰色系统消息？** 客户端无法确定它是玩家消息时会保守归为系统灰字（例如昵称插件使用无法解析的格式）。

**表情包存在哪里？** `.minecraft/config/atomchat/emotes/`，最多 10 个，支持 png / jpg / jpeg。

**怎么改颜色 / 大小？** 编辑 `.minecraft/config/atomchat/atomchat-client.json` 后重启游戏。

**可以放进整合包吗？** 可以。AtomChat 代码为 MIT，无需额外授权；若整合包分发 JAR，请保留 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 中的第三方声明。

---

## 开发与构建

```bash
./gradlew.bat build
```

产物位于 `build/libs/atomchat-Fabric-1.21.1-<version>.jar`。

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

完整变更见源码分支 [Fabric-1.21.1/CHANGELOG.md](https://github.com/E33EPUS/atomchat/blob/Fabric-1.21.1/CHANGELOG.md)。

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
