# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

## v0.2.5-hotfix

修复：部分 NeoForge 整合包注册表冻结导致整个 mod 加载失败（音效注册改走 RegisterEvent，失败仅损失提示音）。

Fixed: a registry-freeze crash that broke the whole mod in some NeoForge modpacks — sound registration now goes through RegisterEvent and degrades to a lost cue sound only.

## v0.2.5

新增：消息通知横幅、提示音、通知设置分组。
修复：头像同步误判停用、发送者名字丢色、面板背景色失效、装饰名泄漏尖括号、横幅把界面推出屏幕。

Added: message notification banners, cue sounds, notification settings group.
Fixed: avatar-sync misdetection, sender-name color loss, ineffective panel background color, decorated-name bracket leaks, banner pushing the UI off-screen.
