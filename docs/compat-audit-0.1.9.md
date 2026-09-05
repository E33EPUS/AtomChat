# AtomChat 0.1.9 兼容共存审计报告（2026-09-05，只审计不改动）

范围：AtomChat（Fabric 1.21.1）与常见聊天链路 mod / 服务端环境的共存风险。结论分级：🟢 无需动作 / 🟡 已知风险有缓解 / 🔴 需要触发条件出现后再处理。

## 1. ChatHud.addMessage 竞争面 🟡

**现状**：`ChatHudMixin` 在 `addMessage(Text, MessageSignatureData, MessageIndicator)` HEAD 注入（`cancellable=true`，priority 500），捕获后必要时 cancel 并以重写文本重发（`atomchat$reposting` 防自递归）。

**发现**：
- **重发会二次暴露给其它 mod**：重发的消息会再次进入 `addMessage`，我们的 capture 被 reposting 守卫跳过，但**其它 mod 的 HEAD/TAIL 捕获会看到同一逻辑消息两次**（原文 + 重写文）。ChatImage 类按 URL 检测渲染的 mod 理论上可能重复处理。缓解：重写仅在需要压缩图片码/引用前缀时发生，且重写保留 URL 语义；未观察到实际双渲染。若未来某 mod 真冲突，优先方案 = 给重发路径加可关闭的 config。
- **priority 500**：低于默认 1000，我们的 HEAD 捕获先于默认优先级的其它 HEAD 处理器。其它 mod 用更低 priority 的 HEAD cancel 消息（客户端反刷屏/过滤类）时，我们**不会**看到该消息——面板与原版聊天一致，属正确行为。
- **TAIL-cancel 不对称**：若某 mod 在 TAIL cancel（罕见），我们已在 HEAD 捕获 → AtomChat 面板显示、原版 HUD 不显示，出现分叉。未发现实际案例，仅记录。

## 2. NCR 2.9.1（防举报服）🟢

玩家消息全部 `convertToGameMessage` 降级为系统通道。`MessageHandlerMixin` 三个注入点（`processChatMessageInternal`×2 / 1 参重载 / `onGameMessage`）位于真正 `ChatHud.addMessage` 之前，绕开 chatDelay 队列与 skip/filter（0.1.x 已修复的错配）。本轮补齐 G3（多色嵌名）与模板后，NCR 灰字场景覆盖率进一步提升。颜文字括号组 0.1.8 已修。**无需动作。**

## 3. IMBlocker（输入法）🟢

`AtomChatScreen extends ChatScreen`（1.21.1 screen 包）→ `instanceof ChatScreen` 白名单天然成立。`chatField`/`chatInputSuggestor` 走 Access Widener 提权，输入全链路原版。风险仅在于 IMBlocker 未来改用其它判定（非 instanceof）——届时再跟进。**无需动作。**

## 4. 机器人桥（EasyBot 类）🟡

- 离线玩家：`SeenPlayers` LRU 512 + 名字反查 uuid（0.1.8/0.1.9 补齐）覆盖"曾经在线后来离线"的转发；**从未见过的离线玩家**仍解析不出 uuid（名字可解析出气泡，头像无皮肤）——协议层无解，接受。
- EasyBot 自有 hover 图片协议（BracketCodec SHOW_TEXT）：AtomChat RichText 有 hover 事件处理（0.1.3 富文本），真实桥环境未实测——有 NCR 实机但无 EasyBot 实机。**留观察项**。
- 桥消息经文本私聊兜底（本轮新增）会按"自己是否在场"判定，机器人代发的私聊不会误进私聊面板（两端都不是自己 → 不认领）。

## 5. 回声抑制风险（EchoTracker 触发条件清单）🟡

现状 = meta 命中抑制 + PrivateEchoTracker（私聊）。**不移植通用 EchoTracker**，以下条件出现再移植：

1. **meta==null + 守卫解析成功 + 消息是自己**：该分支**有意不抑制**（宁可不杀——Xaero 等机器消息可能长得像自己）。若某服务器格式使自己的回声总走这条路径 → 公屏重复显示自己的消息。触发特征：发消息后每条都出现两个自己的气泡，且重开聊天屏不消失。
2. **文本私聊 outgoing**：已接 PrivateEchoTracker；若插件 echo 格式变体导致 tracker 不匹配 → outgoing 消息在私聊面板出现两次（本地气泡 + echo 入库）。
3. 触发任一条件的处置：按 e33chat `EchoTracker`（发送文本 + 时间窗）移植，工作量小（纯类 + 单测）。

## 6. 模板系统共存 🟢

- 模板仅在"权威 key → 文本私聊 → 守卫"全部失手后参与，空配置 = 行为与 0.1.8 完全一致。
- 编译防御：重复占位符/缺 {content}/正则失败只拒绝该模板并记日志，不穿透。
- `{name}` 锚定已知玩家名，纯散文不会误认领。

## 7. 已知环境坑（本轮实测发现）

- **JDK 21.0.11（2026-04 LTS，Oracle）`java.util.regex` 拒绝命名捕获组名中的 `_`/`-`**：`(?<g_name>…)` 报 `named capturing group is missing trailing '>'`。已改用 `gname`/`gdisp`/`gprefix`/`gsuffix`/`gcontent`。**e33chat 的 TemplateMatcher 用带下划线字段名做组名，同 JDK 上会踩同一雷**——若 e33chat 后续跟进此 JDK，需同步改名（记录待办）。

## 结论

无需代码动作的项：NCR、IMBlocker、模板共存。有触发条件才动的项：EchoTracker 移植（条件 1/2）、重发二次暴露（条件：真实 mod 冲突出现）。本轮全部以预防性加固 + 测试 fixture 落地，无阻塞项。
