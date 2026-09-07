# AtomChat

[![Release](https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver&label=%E6%9C%80%E6%96%B0%E7%89%88)](https://github.com/E33EPUS/AtomChat/releases/latest)
[![CurseForge](https://img.shields.io/curseforge/dt/1681271?label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/atomchat)
[![Build](https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg)](https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml)

为 Minecraft 带来手机 App 风格的聊天体验（NeoForge 1.21.1，Skia 渲染）。以 [E33Chat](https://modrinth.com/mod/e33chat) 的设计思路为蓝本的独立重写。

> 本分支是 **NeoForge 源码分支**。完整用户文档（中文 / English）位于默认分支 [Master/README.md](https://github.com/E33EPUS/atomchat/blob/Master/README.md)。

## 功能特性

- **手机面板 UI**：圆角面板 + 毛玻璃背景（可换壁纸），页面推入/消息入场动画
- **消息气泡**：左右分栏、真实玩家名 + 皮肤头像、引用胶囊、时间戳分隔、系统消息胶囊
- **图片消息**：CICode 图片发送/显示/保存，接收开关（关闭时显示绿色占位）
- **交互**：文字拖选 + Ctrl+C 复制、可点击 span（tell/坐标/链接）、右键菜单（气泡/头像/玩家卡）、头像双击 poke
- **输入**：多行输入 + 行内选择、表情 / 颜文字 / 自制表情面板、@提及、WATUT 输入中指示
- **外观自定义**：主题预设、全部界面元素配色、面板尺寸/透明度/描边/圆角/缩放

## 开发

```bash
./gradlew.bat build    # 构建并跑测试
./gradlew.bat test     # 仅测试
```

产物：`build/libs/atomchat-NeoForge-1.21.1-<version>.jar`。开发环境需要 JDK 21。

### 包结构

一个包一个职责，新功能按域落位（避免上帝类）：

| 包 | 职责 |
|---|---|
| `com.atom.chat.chat` | 消息捕获/分类/存储等数据层，含 CICode 工具（`Cicodes`） |
| `com.atom.chat.page` | 推入式页面（会话列表、档案、`MessageListView` 消息列表展示层） |
| `com.atom.chat.ui` | 通用 UI 组件（布局、滚动、动画令牌、输入路由、表情面板） |
| `com.atom.chat.settings` | 设置目录与设置页（开关/滑条/色板/动作） |
| `com.atom.chat.render` | Skia 渲染封装（绘制、富文本、动画器） |
| `com.atom.chat.theme` / `wallpaper` / `avatar` / `image` | 主题预设、壁纸、头像、图片加载 |
| `com.atom.chat.nav` | 导航栈与页面枚举 |
| `com.atom.chat.screen.AtomChatScreen` | 屏幕壳：布局、路由装配、输入处理 |

### 代码约定

- 新交互注册 `InputHandler` 进 `InputRouter`，不往 if-else 链里插分支
- CHANGELOG 每个版本一节（`## vX.Y.Z`），中文组（`### 新增/修复/更改`）在前、英文组（`### Added/Fixed/Changed`）在后，发布说明直接取自该节

## 发版

```bash
git tag vX.Y.Z && git push origin vX.Y.Z
```

CI 自动完成：构建测试 → GitHub Release（附 jar）→ Modrinth / CurseForge 上传。发布说明取自 CHANGELOG.md 对应 `## vX.Y.Z` 节，热修标签（如 `v0.2.2-hotfix`）复用主版本节。平台上传可在 Actions 页对 Release 工作流 Run workflow 手动重跑，不会重建 GitHub Release。

## 下载

- GitHub Releases：<https://github.com/E33EPUS/AtomChat/releases/latest>
- CurseForge：<https://www.curseforge.com/minecraft/mc-mods/atomchat>
- Modrinth：项目公开后在此补充链接

## 依赖

- Minecraft 1.21.1 + NeoForge 21.1.235
- Java 21+
- Skija `0.116.8` / FlatLaf `3.7.2`（构建时打入 JAR）
- 可选：WATUT（输入中指示）、IMBlocker（输入法桥）
