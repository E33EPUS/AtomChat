# Release Notes

发版约定：仓库 GitHub Release 正文取对应版本整段（中文 + English，双语）；
Modrinth / CurseForge 的 changelog 只取 **English** 之后的英文段。
发版前在顶部补新版本段。

## v0.2.5-hotfix

**中文**
修复部分 NeoForge 整合包里注册表冻结导致整个 mod 加载失败的问题（音效注册改走 RegisterEvent，失败时降级为仅损失提示音）。
**English**
Fixed a registry-freeze crash that broke the whole mod in some NeoForge modpacks; sound registration now goes through RegisterEvent and degrades to a lost cue sound only.

## v0.2.5

**中文**
新增消息通知横幅、提示音与通知设置分组；修复头像同步误判停用、发送者名字丢色、面板背景色失效、装饰名泄漏尖括号、横幅把界面推出屏幕等一批问题。
**English**
Added message notification banners, cue sounds and a notification settings group; fixed avatar-sync misdetection, sender-name color loss, ineffective panel background color, decorated-name bracket leaks and the banner that pushed the UI off-screen.
