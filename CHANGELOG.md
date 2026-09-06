# Changelog

## v0.1.11

### 新增

- **tpa/tp 命令自适应**：右键菜单的「传送」不再写死 `/tp`（在装了 tpa 插件的服务器上原版 /tp 常被权限屏蔽）。三招互补：①进服探测服务器命令树（有 `tpa`/`tpaccept`/`tpahere` 即用 `/tpa`）②发出后收到"未知命令/没有权限"回包自动切换并记住 ③设置页「聊天」新增三档手动覆盖（auto/tp/tpa）。
- **TellClickDetector 归因移植**（e33chat 2.3.14 层 2）：发送者名带 `/tell`/`/msg` SUGGEST_COMMAND 点击事件时，命令值携带真实档案名——昵称服务器上的确定性归因，不再依赖文本匹配。
- **EasyBotParser 移植**：内置识别 EasyBot QQ 群转发消息（`[群名] <昵称(QQ号)> 内容` 等四种实战形状，QQ 号解析 + 广播标签守卫 + 已知玩家让路）。无服务器环境，逻辑与母本一致并附单测。
- **@ 提及检测**（MentionDetector 移植）：`@名字` 或（可配置的）裸名字提及自己时，公屏卡片显示琥珀色 `@n` 未读提及角标；预留 `MentionObserver` 通知接口，横幅/音效后续版本接入。设置页「聊天」新增 `mentionRequireAt` 配置。

### 修复

- **输入框鼠标点选与拖选**：点击输入栏此前只设焦点、从不把事件映射到文本，EditBox 的原生点选/拖选从未生效。现在自建"虚拟坐标→字符索引"映射（多行 Skia 换行布局，按字符中点取位），支持单击定位、拖动选区、Shift 点击扩展选区（渲染沿用 EditBox 选区状态）。

### 更改

- **WATUT 集成：「对方正在输入…」**：纯反射读取已安装 WATUT 的客户端状态（CHAT_TYPING），私聊输入框空置时占位符切换为「对方正在输入…」。不装 WATUT 静默降级；无 payload 接收器注册、不与 WATUT 自身冲突。聊天区域的原版提示若被面板盖住，面板内的这个显示即替代。
- **个人档案页重设计**：延迟 / 在线时长 / 统计从三张等权信息卡改为 hero 卡下方的一行三格仪表盘磁贴（大数值 + 小标签，点击复制完整值），身份项（名字 / UUID / 身份 / 服务器）保留为分组行——卡片数量 8→5，信息层级分明。
- **图片按钮图标重绘**：字形占满 ~14×12 viewBox（旧版 13×9 按最长边缩放后高度比表情/发送矮 30%），渲染尺寸从 18×12.5 提升到 18×15.4。
- **私聊文本兜底新增关键词锚定族**（e33chat WhisperDetector/WhisperSignal 移植）：已知玩家名 + 冒号前私聊关键词（悄悄/私聊/密语/私信/密谈/whisper/pm/msg/tell，词边界防误判）即认领为私聊，覆盖结构族表达不了的装饰形状（如 `[VIP] Steve 私聊说: hi`）。

### 评估（未实施）

- **ChatStore 持久化**：维持内存单例的现状。上 keyed storage（按服务器/世界隔离 + JSONL 落盘 + join 清理）与 0.1.10 的头像缓存策略、跨服隔离语义有耦合，需要单独一轮设计；当前行为在 CHANGELOG/README 中如实标注为已知限制。
- 跨消息文字拖选、IME 组字窗内联定位：前者是中等特性（选区状态需消息级化），后者受 vanilla 无公开 IME 定位 API 限制（e33chat 同样未解），均顺延 0.1.12。

## v0.1.10

### 新增

- **服务端 companion：自定义头像跨端可见**（同 jar 双端入口，e33chat「装了才生效」理念）：双开联机（内置服务端）或独立 Fabric 服装 AtomChat 后，玩家可互相看到自定义头像。协议三条：本机设置头像后自动上传（≤256KB PNG、限频 60s、校验 uuid 防伪造 + PNG 魔数）；他人头像懒加载（渲染遇到未缓存的 uuid 才请求，去重 + 3s 探测超时 → 无 companion 服务器本会话静默降级为皮肤）；无头像负缓存 30s。缓存按会话存活（进服清空重拉），头像变更重进后生效，服务端零状态。
- **单击对方头像跳转其个人档案**：QQ 式竞争窗口——单击后 300ms 内无第二次点击 → 跳对方档案页（身份卡/信息行按对方数据渲染）；双击仍是戳一戳。戳一戳或界面动画关闭时双击本就无动作，单击免窗口立即跳。离开档案页自动回到自己的档案。
- **调色盘与裁剪器对称淡出**：HSV 调色盘、图片裁剪器补齐关闭动画（倒放打开曲线，110ms；淡出期间输入仍被吞掉，动画播完才真正关闭）。至此全部浮层淡入淡出对称。

### 更改

- **ImageLoader 四件套**（图片性能）：①只有滚进可视区的图片才发起下载（刷屏不再被动并发全下）②解码降采样到长边 ≤768（20MP 照片从 ~80MB 常驻内存降到 ~2MB，画质仍远超气泡显示尺寸）③内存 LRU 48 张 + 失败负缓存 60s（坏图不再每帧重试）④磁盘缓存 `config/atomchat/image-cache/`（重进不重下）。头像侧本就按皮肤永久缓存（每皮肤 64×64 只读一次 GL），未动。
- **对方气泡默认色**改为 `#2C3E50` 深蓝灰（与配置文件已持久化值一致；新装用户直接生效）。

### Change

- **Server companion: cross-client custom avatars** (same-jar dual entrypoint, the e33chat "works only where installed" philosophy): with AtomChat on both ends of a double-open LAN session (integrated server) or on a dedicated Fabric server, players see each other's custom avatars. Three payloads: auto-upload on set (≤256KB PNG, 60s rate limit, sender-uuid anti-spoof + PNG magic check), lazy per-uuid requests with dedup and a 3s probe timeout (silent skin degradation on servers without the companion), and a 30s negative cache for no-avatar answers. Cache lives for one session (wiped on join); an avatar change shows up after re-entering; the server keeps zero state.
- **Single click opens another player's profile**: QQ-style 300ms competition window — the single click opens the profile only if no second click arrives; double click still pokes. With poke (or decorative motion) off, the click jumps immediately.
- **Symmetric fade-outs for the HSV colour picker and image cropper** (reverse of the open curve, 110ms; input stays swallowed until the fade finishes). Every overlay now fades both ways.

- **ImageLoader hardening**: ①images download only when scrolled into view (a spam burst no longer queues the whole scrollback) ②decoded bitmaps downscale to a ≤768px long edge (~80MB → ~2MB for a 20MP photo) ③48-entry memory LRU + 60s negative cache for failures ④disk cache under `config/atomchat/image-cache/`. Avatar rendering already cached per skin (one GL readback each) and was left as is.
- **Others' bubble default colour** is now `#2C3E50`.

## v0.1.9

### 新增

- **服务器格式模板**（e33chat 同款结构解，纯客户端配置）：守卫解析不出的系统通道消息可由用户自定义模板认领。在 `config/atomchat/atomchat-client.json` 手工配置 `chatTemplates` / `whisperTemplates`（打开聊天屏时生效，免重启）。占位符：`{name}`（玩家名，锚定在线/已知玩家）/ `{display_name}`（装饰名）/ `{prefix}` / `{suffix}` / `{sep}` / `{content}`（恰好一个、任意位置，支持后缀式）。多模板首匹配胜出。配置文件注释内含 EssentialsX / CMI / DeluxeChat / VentureChat 默认格式示例。
- **插件私聊文本兜底**（e33chat G1 移植 + 扩展）：vanilla 翻译 key 之外的文本形态私聊（插件改写 /msg、机器人中继）现在能识别并进入私聊面板 + 未读红点。支持箭头系（EssentialsX `[Steve -> 我] hi`、CMI `[/msg from [Steve]]`、DeluxeChat `Steve -> 我 : hi`）与关键词系（`悄悄地对你说` / `whispers to you` / 你发出的 `你对X悄悄地说`）；只有当一端是自己（或 `我`/`me` 字面量）才认领，其余留给公屏守卫；发送方向按 PrivateEchoTracker 既有语义去重。对端经在线/离线记忆解析身份。
- **解析失败诊断**（e33chat G4 同款）：`debug=true` 时整条认领链（权威 key → 文本私聊 → 守卫 → 模板）全部失手会在日志记一行原始消息，真实服务器上的未知格式可凭日志回修。

### 修复

- **多色 § 码嵌名失明**（e33chat G3 补全）：服务器把名字用色码拆开时（`S§6t§beve`），原实现用裸名对原文 `indexOf` 必然失配 → 消息掉灰字。现在名字匹配允许中间夹 § 码对，偏移保持原文坐标，富文本切片与装饰标签不受影响；尖括号路径的合成标签同步剥码。新增 4 项回归测试。

### 更改

- 模板编译全链路防御：占位符重复（`{name}{name}`）、缺 `{content}`、正则编译失败一律拒绝该模板并记日志，不再影响其它模板（e33chat 2.2.7 崩溃穿透教训）。
- ⚠️ 环境坑存档：**JDK 21.0.11（2026-04 LTS）的 `java.util.regex` 不再接受命名捕获组名中的 `_` 与 `-`**（报 "named capturing group is missing trailing '>'"）。模板正则的组名全部改为无下划线（`gname`/`gdisp`/`gprefix`/`gsuffix`/`gcontent`）。

### 新增测试

- MessagePresentation +4（多色嵌名）/ WhisperTextParserTest 15 项 / ChatTemplatesTest 14 项，全量 210 项绿。

### Change

- **Server-format templates** (e33chat parity, client-side): user-defined templates can now claim system-channel lines the guards cannot parse. Hand-edit `chatTemplates` / `whisperTemplates` in `config/atomchat/atomchat-client.json` (effective when a chat screen opens, no restart). Placeholders: `{name}` (anchored to known players) / `{display_name}` / `{prefix}` / `{suffix}` / `{sep}` / `{content}` (exactly one, any position — suffix style supported). First match wins. Real plugin default formats (EssentialsX / CMI / DeluxeChat / VentureChat) ship as config comment examples.
- **Plugin whisper text fallback** (e33chat G1 port + extension): text-shaped private messages beyond vanilla translation keys (plugin-reformatted /msg, bot relays) now enter the private panel with the unread badge. Arrow family (EssentialsX / CMI / DeluxeChat) and keyword family (`whispers to you`, Chinese variants) are supported; a line is only claimed when one side is the local player; outgoing echoes follow the existing PrivateEchoTracker semantics.
- **Parse-miss diagnostics** (e33chat G4 parity): with `debug=true`, a line the whole claim chain fails on is logged verbatim so unknown real-server formats can be fixed from the log alone.

### Fix

- **Color-code split names went blind** (e33chat G3 completion): when a server splits a name with § pairs (`S§6t§beve`), the old `indexOf(cleanName)` on the raw line always failed and the message degraded to a gray capsule. Name matching now tolerates interleaved § pairs while keeping raw-line offsets for rich-text slicing; 4 regression tests added.
- Template compilation is fully defensive: duplicate placeholders, a missing `{content}` or a broken regex reject just that template with a log line (the e33chat 2.2.7 crash-through lesson).
- ⚠️ Environment note: **JDK 21.0.11 (2026-04 LTS) no longer accepts `_` or `-` in named capturing groups**; template group names are underscore-free accordingly.

## v0.1.8

### 新增

- **个人档案页**：底部「个人」标签从占位页换成真实页面——顶部身份卡（大号圆形头像 + 常驻「编辑」角标 + 玩家名），下方信息卡逐行展示名字 / UUID / 延迟 / 身份（是否 OP）/ 在线时长 / 游戏统计（挖掘·击杀·里程）/ 服务器地址，**点任意一行即复制该值**。延迟与身份取自玩家列表（原版协议只同步自己的权限等级，其他玩家隐藏该行）；单人世界显示「单人世界」。
- **本机自定义头像**：点头像「编辑」角标选图，选完进入 **QQ 式裁剪界面**（面板内模态：居中圆圈固定、图片拖动平移、滚轮以圆心为锚缩放、双击重置，图片永远盖住圆圈不露边；底部对勾/叉双圆按钮，Esc 取消），确认后按可视区域裁剪为 256px PNG 存 `config/atomchat/avatar/`，清除恢复皮肤。生效范围为本机（档案页 + 自己的气泡）；跨端互通规划为服务端 companion 功能，companion 缺席时静默降级为皮肤（e33chat 优雅降级理念）。
- **颜色配置**：外观页新增「颜色」分组——气泡文字颜色 / 自身气泡颜色 / 对方气泡颜色 / 界面文字颜色 / 强调色。每行为一排预设色板（点即生效并写盘），尾部「+」格打开 **HSV 调色盘**（饱和度×亮度方块 + 色相条，实时预览圆点 + 可复制的 hex 蓝链 + hex 输入框：实时应用、失焦自动应用合法值、Esc 先退聚焦）。手改配置文件中的自定义色会追加显示且可选。
- **离线玩家识别**（移植自 e33chat）：记忆曾在聊天中出现的玩家（名字↔UUID，LRU 512）。机器人桥/中继转发的已下线玩家消息仍能解析为真实气泡与头像，而不是系统灰字；皮肤沿用名字键缓存的上次已知头像。
- **Mod 图标与链接**：修复 ModMenu/PCL 读不到图标（补 `icon` 字段），补 `contact` 主页/议题/源码链接使 mod 列表的 website 与 issues 按钮可跳转。

### 更改

- **次要文字颜色不再单独配置**：从「界面文字颜色」自动派生（同色相、降饱和降亮度），两组文字永远协调；配置文件中的旧值将被忽略。
- **底栏与输入按钮状态色**：底栏选中 tab 的图标变强调色（点击态）；表情按钮在面板展开期间保持强调色，图片/表情按钮按压瞬间高亮。
- **图片按钮图标重绘**：从竖版照片改为横版图片字形。
- 滑块数值（90% / x1.00 等）与色板色号文案改为纯白。

### 修复

- **颜文字消息被识别为系统灰字**：整条消息是一个平衡括号组时（如 `(￣▽￣)`、`(≧▽≦)`、`【滑稽】`），`MessagePresentation` 的分隔符跳过逻辑会把它当作名字后缀装饰（本意是解析 `[AFK]`/`(VIP)`）整段吞掉，导致解析失败。在 NCR 服务器上（玩家聊天经系统通道广播），回声捕获失败 → 再次解析仍失败 → 被当作系统消息渲染成灰色胶囊。现在括号跳过吞掉全部剩余文本时，自动回退为「括号组属于内容」，发送者标签收回为裸名字。
- 新增 3 项 `MessagePresentation` 回归测试（尖括号/冒号/全角括号三种格式）。

### Change

- **Profile page**: the "Profile" tab now shows a real page — a hero identity card (large circular avatar with a persistent edit badge and the player name) above copyable info rows: name / UUID / ping / role (OP) / session time / stats (mined · kills · walked) / server address.
- **Local custom avatar**: pick an image via the avatar's edit badge, crop it in a QQ-style modal (fixed centred circle, drag to pan, wheel zooms around the circle centre, double-click resets, the image always covers the frame), and the visible region is stored as a 256px PNG under `config/atomchat/avatar/`. Local-only for now; cross-client sync is planned as a server-companion feature and silently degrades to the skin when it is absent (the e33chat philosophy).
- **Colour settings**: a new Colors group in Appearance — bubble text / your bubble / others' bubble / interface text / accent. Each row is a preset swatch strip (applies and saves instantly) with a trailing "+" opening an HSV picker (saturation×brightness square + hue bar, live preview dot, a copyable hex link and a hex input that applies live). Custom colours from a hand-edited config still render and stay selectable.
- **Offline player memory** (ported from e33chat): players once seen in chat are remembered (name ↔ UUID, LRU 512) so relayed lines from offline players parse as real bubbles with their last-known skin instead of gray system capsules.
- **Mod metadata**: fixed the icon not loading in ModMenu/PCL and made the website/issues buttons open the GitHub repository.
- **Changed**: the secondary text colour is now derived from the interface text colour (same hue, desaturated and darkened) and is no longer a separate setting; the selected bottom-tab icon and the emoji button (while its panel is open) take the accent colour; the image button glyph was redrawn as a landscape photo; slider/swatch values are pure white.
- **Fixed**: bracket-only kaomoji no longer render as gray system bubbles on NCR servers.

## v0.1.7

### 更改/修复

- **设置磁贴改方形**：四个磁贴从横矩形改为正方形，SVG 图标与文案作为一组垂直居中（图标在上、单行文案在下），移除副标题描述。
- **关于页重排**：每个条目改为「标题上 / 值下」两行，修复第三方组件名与值互相覆盖。
- **关于页蓝链**：GitHub 仓库、MIT 许可证，以及 Skija / Skia / FlatLaf 三个组件全部可点击跳转（蓝色 + 下划线，用 MC 自带能力打开浏览器；全屏下浏览器会开在游戏后面）。
- **新增滑块**：外观页新增三个连续值设置——背景不透明度（30–100%）、面板宽度（320–600）、界面缩放（x0.75–x1.50）。拖动手柄改值并即时写盘；点轨道一次 ±一个步进；拖动期间列表滚动被屏蔽。
- **界面缩放为真实即时缩放**：在 Skia canvas 的设计密度上乘以系数，并同步作用到全部坐标换算（`uiDensity()` 单点收敛，鼠标、模糊 pre-pass、输入法锚点自动跟随）。UiTokens 常量保持类加载期求值，不做运行时重建。
- **背景不透明度同时作用于模糊底与实色底**：一个滑块统一控制面板透出世界的程度，与「背景模糊」开关正交。
- **返回时标题立即切换**：页面推入/弹出的顶栏标题改为始终显示目标页，返回动画第一帧就切到上一级标题，不再等滑出结束。
- **面板宽度安全性确认**：Skia surface 按整个帧缓冲创建，面板只是每帧重算的矩形，改动宽度即时重排，不存在拉伸问题。
- **关于页新增 Hero 卡**：顶部一张更高的卡片，左侧白色圆角底板内嵌 Mod logo，右侧 AtomChat 字标（图片接口已预留，未来可整体替换为艺术字 logo）；logo 已降采样到 256px（681KB → 33KB）。
- **链接卡片提示重做**：「点击跳转」改为主字号纯白、垂直居中，作为卡片的主动作而非脚注。
- **外观图标重绘**：从调色板改为三段调节滑杆（与「调整」分组语义一致，原图案点在磁贴尺寸下发虚）。
- **壁纸卡片动词同款**：「选择图片 / 清除」与链接卡片的「点击跳转」一致，改为主字号纯白、右侧垂直居中。
- **修复空态误判**：聊天页会话列表滚动后如果所有卡片都滚出视野，会错误地画出「无在线玩家」空态；现在改为按数据（是否存在玩家行）判断，而不是按屏幕上可见的卡片。
- **性能优化**：文字测量新增 4096 条 LRU 缓存（文字整形是 UI 层最热的 CPU 路径，同一标签每帧都在重复测量）；截断算法从 O(n²) 次测量改为二分查找；设置目录（开关/滑块定义）与玩家卡排序比较器改为只构建一次，不再每帧重建。

### Change/Fix

- **Square settings tiles**: the four tiles changed from wide rectangles to squares, with the SVG glyph and a single label grouped and vertically centred (icon above, one text line below); the subtitle captions are gone.
- **About page relayout**: each entry is now two lines (title above, value below), fixing the third-party component names overlapping their values.
- **About page links**: the GitHub repository, the MIT license and the Skija / Skia / FlatLaf components are all clickable (blue + underline, opened through MC's browser hook; in fullscreen the browser opens behind the game).
- **New sliders**: the Appearance section gains three continuous settings — background opacity (30–100%), panel width (320–600) and interface scale (x0.75–x1.50). Dragging the handle writes through to the config live; clicking the track nudges by one step; list scrolling is suppressed while dragging.
- **Interface scale is really live**: the factor multiplies the Skia design density and every coordinate conversion through the single `uiDensity()` funnel (mouse, blur pre-pass and IME anchoring follow automatically). UiTokens constants stay class-initialised and are never rebuilt at runtime.
- **Background opacity covers both backgrounds**: one slider governs how much world shows through, applied to the blurred tint and the solid fallback alike, orthogonal to the blur switch.
- **Back titles switch immediately**: the pushed-page header now always names the destination, so a pop flips to the parent title on the first frame instead of waiting for the slide-out.
- **Panel width is safe**: the Skia surface spans the whole framebuffer and the panel is just a per-frame rect, so changing width re-lays out instantly — no stretching involved.
- **About-page hero card**: a taller card on top with the mod logo on a white rounded plate and the AtomChat wordmark beside it (the image slot is an interface, ready for an art-text logo later). The logo is downsampled to 256px (681KB → 33KB).
- **Link-card hint reworked**: the "Open" cue is now title-sized, pure white and vertically centred — a call to action, not a footnote.
- **Appearance icon redrawn**: palette replaced by three adjustment sliders, matching the Adjustments group; the old dots went fuzzy at tile size.
- **Wallpaper card verbs match**: "Choose" / "Clear" now use the same title-sized, white, vertically-centred style as the link cards' "Open" cue.
- **Fixed a false empty state**: scrolling the conversation list until every card left the viewport wrongly showed the "No players online" state; it is now decided from the data (whether player rows exist), not from what is visible.
- **Performance**: text measurement now goes through a 4096-entry LRU cache (text shaping is the hottest CPU path in the UI — identical labels were being re-shaped every frame); truncation switched from O(n²) measurements to a binary search; the settings catalog (switch/slider definitions) and the player-card sort comparator are built once instead of per frame.

## v0.1.6

### 更改/修复

- **设置页上线**：底栏「设置」不再是占位页，改为 Win11 风格的 2×2 磁贴主页（外观 / 聊天 / 隐私与屏蔽 / 关于），每个磁贴带自绘线性 SVG 图标、标题与副标题说明。
- **设置子页**：点击磁贴进入对应设置界面，复用与公屏/私聊一致的全宽 push/pop 转场；左上角返回箭头（或 Esc）返回，底部不再绘制 tab 栏，列表吃满面板高度。
- **开关控件**：每个配置项卡片右侧带 iOS 比例开关（140ms easeOutCubic 滑块，开=主题蓝、关=半透明白、旋钮纯白）。
- **配置热更新**：任意开关切换立即写回 `atomchat-client.json`，全部选项即时生效，无需重启游戏。
- **新开关**：背景模糊、界面动画（装饰动效总闸）、消息入场动画、双击头像戳一戳、隐藏被屏蔽玩家的消息、调试模式。
- **「界面动画」为真实开关**：此前 `animationEnabled` 字段从未被任何代码读取；现已接入消息入场、页面转场、面板开合、头像 poke 抖动与滚动吸底。hover 反馈与滚轮惯性保留，UI 不会失去响应感。
- **隐私语义可选**：屏蔽玩家原本会直接丢弃其公屏消息；现可关闭「隐藏被屏蔽玩家的消息」让公屏仍可见，但会话卡片仍灰化、私聊仍只读。
- **布局**：`UiLayout` 新增 `DETAIL` 模式（无输入栏、无 tab 栏），并补充几何单测锁定列表恰好回收 tab 栏高度。

### Change/Fix
- **Settings page ships**: the Settings tab is no longer a placeholder but a Windows-11-style 2x2 tile grid (Appearance / Chat / Privacy & blocking / About), each tile with a hand-drawn line icon, title and caption.
- **Settings sub-pages**: tapping a tile opens the section, reusing the same full-width push/pop transition as the public/private pages; back arrow (or Esc) returns, the tab bar is gone and the list uses the full panel height.
- **Toggle switch**: every option card carries an iOS-proportioned switch (140ms easeOutCubic knob, accent blue when on, translucent white when off, pure white knob).
- **Live config**: any toggle writes `atomchat-client.json` immediately; every option takes effect at once, no restart needed.
- **New switches**: background blur, interface animations (decorative-motion master), message entrance, double-tap avatar poke, hide blocked players' messages, debug mode.
- **"Interface animations" is a real switch**: the `animationEnabled` field was previously never read by any code; it now gates message entrances, page transitions, the panel open slide, the poke shake and scroll snapping. Hover feedback and wheel glide are kept so the UI never feels unresponsive.
- **Optional privacy semantics**: blocking a player used to drop their public messages outright; turning off "Hide blocked players' messages" keeps them visible in public chat while the card stays greyed out and private chat stays read-only.
- **Layout**: `UiLayout` gains a `DETAIL` mode (no composer, no tab bar) with unit tests asserting the list reclaims exactly the tab bar height.

## v0.1.6

### 更改/修复

- **私聊名字去交互**：私聊页里对方名字不再有下划线/可点击，保留颜色与装饰；公屏名字点击行为不变。
- **远端引用解析**：收到带 `「引用 @名字: 内容」` 前缀的公屏/私聊消息时，接收端会解析成引用胶囊 + 正文，不再把整段前缀当正文显示。
- **原版私聊行改写**：vanilla `/msg` 系统消息改为 e33chat 同款 `<名字>[私聊] 正文`（紫色标签），不再显示 “whispers to you” 系统句。
- **卡片时间与状态点**：根列表卡片时间改为纯白；玩家卡名字右侧新增在线绿点 / 离线红点。
- **@ 图标重绘**：头像右键菜单的 @ 图标改为 Lucide at-sign 风格的真 @ 线性图标。
- **菜单淡出文案修复**：公屏头像右键菜单淡出时，“屏蔽/取消屏蔽”不再因目标被清空而显示异常。
- **私聊引用预览补 [引用]**：私聊里引用消息时，原版聊天框预览会显示 `[引用]`/`[Quote]` 占位，不再露出整段 `「引用...」` 前缀。
- **引用图片不刷 URL**：引用图片消息时，引用内容改为绿色 `[图片]`/`[Image]`，不会把图片 URL/CICode 放进引用胶囊。
- **公屏↔私聊切屏动画**：从公屏头像右键“私聊”进入/返回私聊时，消息列表现在使用与根页一致的全宽 push/pop 动画。

### Change/Fix

- **Private sender names are no longer interactive**: in private chats the other player's name no longer shows as an underlined clickable link; public chat names keep their click behaviour.
- **Remote quote parsing**: incoming public/private messages with a `「引用 @name: text」` prefix now reconstruct the quote capsule and body instead of showing the raw prefix as bubble text.
- **Vanilla private line rewrite**: vanilla /msg system lines become e33chat-style `<name>[Whisper] body` with a purple tag instead of “whispers to you”.
- **Card time and status dot**: conversation-card time is now pure white; player cards show an online green / offline red dot right after the name.
- **@ icon redraw**: the avatar context-menu mention icon is now a true linear @ in the Lucide at-sign style.
- **Context-menu fade fix**: the Block/Unblock label no longer misbehaves while the public avatar menu is fading out.
- **Private quote preview shows [Quote]**: quoting inside private chat now renders a `[Quote]`/`[引用]` placeholder in the vanilla chat line instead of the raw `「引用...」` prefix.
- **Image quotes no longer leak URLs**: quoting an image message puts a green `[Image]`/`[图片]` placeholder in the quote capsule instead of the URL/CICode.
- **Public ↔ private page animation**: entering/leaving a private conversation from the public avatar menu now uses the same full-width push/pop animation as the root pages.

## v0.1.5


### 更改/修复

- **根页会话列表重写（QQ 同款）**：Public 固定置顶，随后按规则列出当前服务器全部在线玩家，再补“最近私聊过但现在离线”的玩家；无分组标题，列表支持滚动。
- **玩家卡片**：显示真实 ID、皮肤圆形头像（预留 `PlayerAvatarSource` 接口，后续可接自定义头像）、最近消息预览、时间；右上角未读红点计数（>99 显示 99+），屏蔽玩家整卡黑白滤镜。
- **私聊页面**：新增带目标的 PRIVATE_CHAT 页面；根列表左键玩家卡进入私聊，公屏头像右键“私聊”也进入；每个私聊会话独立保存消息历史、草稿与滚动位置；Header 显示真实 ID + 在线/离线小圆点。
- **私聊捕获**：按 vanilla `/msg` 翻译键（incoming/outgoing）分流到 `PrivateChatStore`，不混入公屏；发送时本地立即上屏，并移植 e33chat 的 pending echo 抑制，避免服务器回显双份。
- **发送语义**：私聊页普通文本自动拼 `/msg <真实ID>`，以 `/` 开头原样作为命令；图片/表情/引用/拖放/消息右键菜单随会话视图完整复用。
- **会话视图复用**：公屏与私聊共用消息渲染/输入栏/滚动/回复/表情体系；新增“回到最新”右下圆形向下箭头按钮，不在吸底状态时浮出，点击平滑回底（公屏与私聊都有）。
- **右键玩家菜单**：根列表玩家卡右键 = 传送（在线可点/离线置灰）+ 屏蔽/取消屏蔽；公屏头像菜单 = @提及 / 私聊 / 传送（在线可点/离线置灰）/ 屏蔽/取消屏蔽；传送固定 `/tp <真实ID>`。
- **屏蔽系统**：名单全局持久化到 `atomchat-client.json`；保留旧消息只挡新消息；已屏蔽玩家卡片黑白滤镜，仍可进入私聊查看历史但输入栏只读。
- **离线与只读**：离线最近会话可进入查看历史，输入栏显示“对方不在线，无法发送”；已屏蔽同理。
- **导航状态恢复**：Y 键重开会恢复上次页面栈，包括正在浏览的私聊会话目标。
- **未读体系**：Public 与每个私聊会话都维护未读数，进入会话动画开始时清零；根列表实时从 Tab 名单刷新上下线。

### Change/Fix

- **Root conversation list rewrite (QQ style)**: Public stays pinned at the top, then every online player on the current server is listed by rule, followed by recently-chatted players who are now offline; no group headers, scrolling supported.
- **Player cards**: real profile ID, circular skin avatar (behind a `PlayerAvatarSource` interface ready for custom avatars), latest message preview and time; unread red badge on the right (>99 becomes 99+); blocked players get a full-card grayscale filter.
- **Private chat pages**: a target-aware PRIVATE_CHAT page; left-clicking a player card enters it, and the public-chat avatar right-click Whisper item also enters it. Each conversation keeps its own history, draft and scroll position; the header shows the real ID with an online/offline dot.
- **Private capture**: vanilla `/msg` translation keys (incoming/outgoing) are routed into `PrivateChatStore`, never into the public feed; sends appear locally immediately and use the e33chat-style pending echo suppression so the server echo cannot duplicate bubbles.
- **Send semantics**: in a private page plain text is auto-prefixed with `/msg <real ID>`; slash input is sent as-is as a command. Images/emotes/quotes/drop/context menus are inherited from the shared chat view.
- **Shared chat view**: public and private channels reuse the same message rendering/input/scroll/reply/emoji stack. A new circular “jump to latest” down-arrow button floats bottom-right when the view is not at the bottom and scrolls smoothly back down (both public and private).
- **Player right-click menus**: root player card = Teleport (enabled online / greyed offline) + Block/Unblock; public avatar menu = Mention / Whisper / Teleport (enabled online / greyed offline) / Block/Unblock; teleport uses `/tp <real ID>`.
- **Block system**: the global list persists to `atomchat-client.json`; old messages are kept and only new ones are filtered; blocked cards are grayscale and still open read-only history.
- **Offline/read-only**: offline recent chats open read-only with a “player is offline” composer; blocked conversations behave the same.
- **Navigation restore**: the Y hotkey restores the previous page stack, including the private conversation that was open.
- **Unread system**: Public and each private conversation track unread counts; entering a conversation clears its badge at animation start; the root list refreshes online/offline status every frame from the tab list.

## v0.1.4


### 更改/修复

- **头像右键菜单框架**：右键真实玩家头像弹出菜单（@ 提及 / 私聊 / 传送 / 屏蔽）；@ 已接入，其余动作预留；左键单击头像不再插入 @，双击仍为 QQ poke。
- **导航壳**：AtomChat 升级为同面板页面栈；根页为会话列表，底部 `聊天 / 个人 / 设置` tab；默认聊天键（T）直接打开公屏，新键位（默认 Y）打开上次所在页面；世界频道详情页带 SVG 返回箭头。
- **统一壳级 Header**：所有页面右上角统一显示时间；Header/标题/返回由壳统一绘制，页面类不再重复画。
- **底部 Tab 重绘**：采用 Apple 风格紧凑公式布局；三个 tab SVG 重绘为细线 + 选中填充/高亮。
- **公屏命名**：用户可见的 “World Channel / 世界频道” 统一改为 `Public / 公屏`。
- **可复用滚动系统**：新增纯 `ScrollController`，世界频道消息列表与根页共用滚动条/滚轮/拖动逻辑；为后续长列表（私聊/设置）铺路。
- **架构拆分**：抽出 `AppIcons` / `ShellHeader` / `BottomTabBar` / `ScrollController` 等壳级组件，减少 `AtomChatScreen` 膨胀。
- **Emoji 视觉居中修正**：`U+FE0F` 表情变体选择符不再参与 Skia 的文字宽度/换行测量，带 `❤️/✌️` 等字符在表情格子、消息和输入框里的横向偏移与多余空隙消除；发送内容保持原字符不变。
- **图标-only 底栏**：去掉 `Chat / Profile / Settings` 文字，底栏高度改为 `图标尺寸(s28) + 2×胶囊内留白(s4) + 2×边缘留白(s8)` 的公式布局，图标垂直居中，胶囊与底栏四边等距。
- **图标线宽按尺寸比例统一**：所有 20×20 SVG 线性图标的描边随渲染尺寸等比缩放（参考：s16 图标 = 1.5 线宽），底栏大图标不再显得比右键菜单/工具栏细。
- **SVG 图标重绘**：使用 svg-design 方法论重绘底部三个 tab 图标——圆角聊天气泡去掉过粗内线、人物、设置改为 Lucide 真齿轮（ISC 无版权）；Public 地球恢复历史 Lucide 风格椭圆经线版本；图标尺寸回落到 s24 并采用光学渐变线宽（大图标不再等比变粗）。
- **IME 组字贴合修复**：隐藏 EditBox 的 X 坐标按“Skia 已上屏前缀宽度 − 原版字体前缀宽度”补偿，中文输入法组字窗不再与已输入文字之间出现间距。
- **根页卡片 hover**：公屏卡片复用全局 45/255 白高亮 + 90ms 淡入淡出语言。
- **会话卡片时间**：公屏卡片右上角显示最新消息时间——今天 HH:mm，跨天显示 昨天/前天/M月d日，后续私聊卡片可沿用。
- **页面切换动画**：进入/返回公屏采用 200ms easeInOutCubic 双向全宽 push/pop——根页与详情页主体同时左右移动；上栏作为固定“状态栏”不随动画移动，只切换标题与返回键可见性。
- **公屏图标放大**：卡片图标容器 s36→s44、内层地球 SVG s20→s26，四周统一 s10 留白，与圆角卡片间距按公式计算。

### Change/Fix

- **Avatar context-menu framework**: right-click a real player avatar opens a menu (Mention / Whisper / Teleport / Block); Mention is wired, the rest are placeholders for upcoming features. Left single-click no longer inserts @; double-click still triggers the QQ-style poke.
- **Navigation shell**: AtomChat now has an in-panel page stack. The root is a conversation list with Chat / Profile / Settings bottom tabs; the normal chat key (T) opens Public directly, and a new key (default Y) restores the last opened page. The Public detail page has an SVG back arrow.
- **Unified shell header**: time is shown on every page's top-right; header/title/back are drawn once by the shell instead of per page.
- **Redesigned bottom tab bar**: Apple-style compact formula layout; the three tab SVGs were redrawn with line style plus selected fill/highlight.
- **Public naming**: all user-visible “World Channel / 世界频道” copy is now `Public / 公屏`.
- **Reusable scroll system**: a pure `ScrollController` now powers both the world-chat message list and root pages, sharing scrollbar/wheel/drag behavior for future long lists.
- **Architecture cleanup**: extracted shell-level `AppIcons`, `ShellHeader`, `BottomTabBar`, and `ScrollController` components to keep `AtomChatScreen` from growing further.
- **Emoji visual centring fix**: `U+FE0F` emoji presentation selectors no longer contribute to Skia text/measure/line-wrap width, so `❤️/✌️` and similar glyphs no longer sit off-centre or leave phantom gaps in the emoji grid, messages, or the input box; the original sent text is unchanged.
- **Icon-only bottom tab bar**: Chat / Profile / Settings text labels are gone; the bar height is now `icon size (s28) + 2 × capsule padding (s4) + 2 × edge padding (s8)`, with the icon vertically centred and the selected capsule keeping equal breathing room from every bar edge.
- **Size-proportional icon strokes**: all 20×20 SVG line icons now scale their stroke with rendered size (reference: s16 icon = 1.5 stroke), so the larger bottom-tab icons no longer look thinner than context-menu/toolbar icons.
- **Redrawn shell icons**: bottom-tab icons (rounded chat bubble without heavy inner lines, user, Lucide proper gear under ISC) were redrawn with the svg-design methodology; the Public globe restores the earlier Lucide-style elliptical-meridian version. Icon size is back to s24 and strokes use an optical taper so larger icons no longer become proportionally heavier.
- **IME composition alignment**: the hidden EditBox X is offset by the difference between Skia and vanilla prefix widths, so the Chinese IME pre-edit window no longer floats away from the committed Skia text.
- **Root card hover**: the Public conversation card now reuses the global 45/255 white highlight with the 90ms fade language.
- **Conversation time**: the Public card shows the latest message time at the top right — HH:mm for today, Yesterday / 2 days ago / M/d across days — ready for future private-chat cards.
- **Page transition**: entering/leaving Public uses a 200ms easeInOutCubic full-width push/pop where root/detail bodies move together while the top header stays fixed like a status bar, only swapping title/back visibility.
- **Larger Public icon**: the card icon container grows from s36 to s44 and the inner globe from s20 to s26, with uniform s10 spacing calculated against the rounded card.

## v0.1.3

### 更改/修复

- **富文本聊天渲染**：玩家名/正文支持颜色、下划线、点击与悬停；可点击 `/tell`、Xaero 坐标、FTB 接受/拒绝、外部链接；裸 `http(s)` 自动识别为可点击链接并在悬停时显示 URL。
- **原版 HUD 占位**：`[[CICode,...]]` 图片代码在原版聊天栏显示为绿色 `[图片]`，不再刷一长串 URL；引用消息显示为蓝色 `[引用]`，保留发送者前缀。
- **图片消息右键保存**：右键图片气泡新增“保存”，通过 FlatLaf 另存为对话框选择位置，后台下载原始 URL 字节（GIF/WebP/PNG 原样保留）。
- **右键菜单图标**：复制 / 引用 / 保存均绘制 20×20 白色线性 SVG 图标。
- **文件选择器改进**：默认“详细信息”视图，图片文件在列表中直接显示内联缩略图，不再依赖右侧预览区。
- **气泡/UI 修复**：多行文本气泡宽度按最长行计算；引用胶囊与气泡外缘对齐；系统消息胶囊颜色与图片加载占位一致并保留半透明；普通网页链接不再被误当成图片消息。
- **指令不再本地弹气泡**：输入 `/` 指令不再制造自己的聊天气泡，与原版行为一致。
- **稳定性修复**：修复 AWT headless 导致图片选择器打不开；修复 0.1.2 中 HUD 重写未声明 cancellable 导致发送图片后被踢出单人游戏的问题。
- **消息不被误吞**：Xaero waypoint/路径分析等机器协议消息强制走系统通道；无频道身份、仅文本像“自己”的消息不再被 own-echo 误杀，遵循 e33chat“宁可不杀不可错杀”原则。

### Change/Fix

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
