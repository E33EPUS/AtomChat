# AtomChat Rich Text / Click / Hover / Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 AtomChat 的 Skija 消息渲染加一套通用富文本 run 引擎，使消息可显示颜色/下划线、可点击、可悬浮 tooltip，并补强玩家/系统消息分流。

**Architecture:** 在消息进入 `ChatStore` 时把 Minecraft `Text` 树切成 `RichText`（名字/正文各一份，保留 `Style`）。渲染/换行/命中/选择全部通过 `RichTextLayout` 产生的可视行完成；点击走原版 `Screen.handleTextClick`，tooltip 走原版 `DrawContext.drawHoverEvent`。分流仍以结构化捕获为第一证据，翻译键为第二证据，文本守卫为兜底。

**Tech Stack:** Fabric 1.21.1 / Yarn / Skija 0.116.8 / JUnit 5

## Global Constraints

- 只在 AtomChat 聊天面板打开时生效；原版 HUD 不动。
- 本轮只实现颜色、下划线、ClickEvent、HoverEvent；粗体/斜体/删除线/混淆码不做。
- 有 ClickEvent 的 run 自动补下划线；有显式色的 run 尊重原色，无显式色沿用 AtomChat 白/灰。
- 裸 `http://`/`https://` 自动补 `OPEN_URL`；URL 的 `SHOW_TEXT` hover 由客户端渲染层补（纯 JUnit 下不构造 HoverEvent）。
- 点击行为：建议类填输入框、执行类直接执行、其余走原版；按下不触发，松开且未拖动才触发。
- 任何 HoverEvent 都显示 tooltip。
- 兜底文本解析要求名字能解析到当前在线玩家（profile + tab 名），否则系统灰字。
- 验收环境：无 NCR 标准服 + 插件改格式系统通道；NCR 碾键场景尽力但不单独验收。
- 提交信息用英文、无 emoji；测试与模块先行。

---

## File Structure

**New files**
- `src/main/java/com/atom/chat/text/RichText.java` — `RichRun` + 不可变富文本片段，负责 `Text` 扁平化、字面量、切片。
- `src/main/java/com/atom/chat/text/TextMeasurer.java` — 纯测量函数接口，使 layout 可离线单测。
- `src/main/java/com/atom/chat/text/RichTextLayout.java` — 纯富文本换行/行命中/字符定位。
- `src/main/java/com/atom/chat/render/RichTextRenderer.java` — Skia 绘制富文本并收集 `ClickableSpan`。
- `src/main/java/com/atom/chat/render/ClickableSpan.java` — 命中矩形 + `Style`。
- `src/test/java/com/atom/chat/text/RichTextTest.java`
- `src/test/java/com/atom/chat/text/RichTextLayoutTest.java`
- `src/test/java/com/atom/chat/chat/ChatClassifierTest.java`
- `src/test/java/com/atom/chat/chat/RichChatPartsTest.java`

**Modified files**
- `src/main/java/com/atom/chat/chat/ChatMessage.java` — 增加 `richSender`/`richContent`。
- `src/main/java/com/atom/chat/chat/SenderMeta.java` — 增加可选 `Text senderComponent/contentComponent`。
- `src/main/java/com/atom/chat/chat/MessagePresentation.java` — 增加 `labelEnd`/分隔符起点信息，供富文本切片。
- `src/main/java/com/atom/chat/chat/ChatPipeline.java` — 增加 `sliceRichText`。
- `src/main/java/com/atom/chat/chat/ChatClassifier.java` — 增加正/负/未知三态翻译键分类。
- `src/main/java/com/atom/chat/mixin/MessageHandlerMixin.java` — 捕获 `params.name()` 和正文 `Text`。
- `src/main/java/com/atom/chat/mixin/ChatHudMixin.java` — 用富文本部件构造 `ChatMessage`。
- `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java` — 接入渲染/命中/点击/tooltip。

---

### Task 1: RichText 核心（扁平化 + 切片 + 自动链接化）

**Files:**
- Create: `src/main/java/com/atom/chat/text/RichText.java`
- Test: `src/test/java/com/atom/chat/text/RichTextTest.java`

**Interfaces:**
- Produces:
  - `record RichRun(String text, net.minecraft.text.Style style)`
  - `class RichText`
    - `static RichText of(net.minecraft.text.Text text)`
    - `static RichText literal(String text)`
    - `static RichText empty()`
    - `String getString()`
    - `List<RichRun> runs()`
    - `boolean isEmpty()`
    - `RichText slice(int from, int to)` — UTF-16 下标，不允许切在代理对中间
    - `RichText linkifyUrls()`
    - `net.minecraft.text.Style rootStyle()`

- [ ] **Step 1: 写失败测试**

```java
class RichTextTest {
    @Test
    void flattenPreservesRunStyles() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/x"));
        Text text = Text.literal("a").setStyle(click).append(Text.literal("b"));
        RichText rich = RichText.of(text);
        assertEquals("ab", rich.getString());
        assertEquals(2, rich.runs().size());
        assertEquals(click, rich.runs().get(0).style());
    }

    @Test
    void sliceKeepsStyles() {
        Text text = Text.literal("abc").setStyle(Style.EMPTY.withColor(0xFF0000))
                .append(Text.literal("def").setStyle(Style.EMPTY.withUnderline(true)));
        RichText sliced = RichText.of(text).slice(2, 5);
        assertEquals("cde", sliced.getString());
        assertEquals(0xFF0000, sliced.runs().get(0).style().getColor().getRgb());
    }

    @Test
    void linkifyBareUrls() {
        RichText rich = RichText.literal("see https://example.com/x now");
        RichText linked = rich.linkifyUrls();
        assertTrue(linked.runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests com.atom.chat.text.RichTextTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 `RichText`**

核心扁平化实现：

```java
public final class RichText {
    public record RichRun(String text, net.minecraft.text.Style style) {}

    private final List<RichRun> runs;
    private final net.minecraft.text.Style rootStyle;

    private RichText(List<RichRun> runs, net.minecraft.text.Style rootStyle) {
        this.runs = List.copyOf(runs);
        this.rootStyle = rootStyle;
    }

    public static RichText of(net.minecraft.text.Text text) {
        List<RichRun> out = new ArrayList<>();
        text.visit((style, s) -> {
            if (!s.isEmpty()) out.add(new RichRun(s, style));
            return Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
        return new RichText(out, text.getStyle());
    }

    public static RichText literal(String text) {
        return of(net.minecraft.text.Text.literal(text));
    }

    public String getString() {
        StringBuilder sb = new StringBuilder();
        for (RichRun r : runs) sb.append(r.text());
        return sb.toString();
    }

    public RichText slice(int from, int to) {
        // 不允许切在代理对中间：from/to 若落在高代理后、低代理前，向两侧收拢。
        // 实现时用 Character.isHighSurrogate/isLowSurrogate 修正边界。
        List<RichRun> out = new ArrayList<>();
        int pos = 0;
        for (RichRun run : runs) {
            int end = pos + run.text().length();
            if (end > from && pos < to) {
                int s = Math.max(0, from - pos);
                int e = Math.min(run.text().length(), to - pos);
                out.add(new RichRun(run.text().substring(s, e), run.style()));
            }
            pos = end;
            if (pos >= to) break;
        }
        return new RichText(out, rootStyle);
    }

    public RichText linkifyUrls() {
        // 用 Pattern "(?i)\\bhttps?://[^\\s<>\"']+" 扫描每段 run.text；
        // 命中处拆成 3 段：前置普通文本、URL run（保留原 run.style 并
        //   .withClickEvent(new ClickEvent(OPEN_URL, url))）、尾部文本。
        // 只对“当前 run 没有 ClickEvent”的纯文本段做；已有事件保持原样。
        // HoverEvent 不在 RichText.linkifyUrls 构造：HoverEvent 在纯 JUnit
        // 下会触发 MC Bootstrap，URL 的 SHOW_TEXT hover 由 Task 7/8 在客户端
        // 已 bootstrap 的渲染层补（若有需要）。
    }
}
```

- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/atom/chat/text/RichText.java src/test/java/com/atom/chat/text/RichTextTest.java
git commit -m "Add rich text run model with slicing and URL linkify"
```

---

### Task 2: RichTextLayout 纯换行/命中模块

**Files:**
- Create: `src/main/java/com/atom/chat/text/TextMeasurer.java`
- Create: `src/main/java/com/atom/chat/text/RichTextLayout.java`
- Test: `src/test/java/com/atom/chat/text/RichTextLayoutTest.java`

**Interfaces:**
- Produces:
  - `@FunctionalInterface interface TextMeasurer { float measure(String text); }`
  - `record RichLine(List<RichText.RichRun> runs, int textStart, int textEnd) { String getPlainText(); }`
  - `class RichTextLayout`
    - `static List<RichLine> wrap(RichText text, TextMeasurer measurer, float maxWidth)`
    - `static int charAt(RichLine line, TextMeasurer measurer, float localX)`

- [ ] **Step 1: 写失败测试**

```java
class RichTextLayoutTest {
    @Test
    void wrapSplitsWithoutBreakingStyles() {
        RichText text = RichText.literal("abcdef");
        TextMeasurer m = s -> s.length() * 10f;
        List<RichLine> lines = RichTextLayout.wrap(text, m, 30f);
        assertEquals(2, lines.size());
        assertEquals("abc", lines.get(0).getPlainText());
        assertEquals("def", lines.get(1).getPlainText());
        assertEquals(3, lines.get(1).textStart());
    }

    @Test
    void charAtMapsLocalXToIndex() {
        RichLine line = RichTextLayout.wrap(RichText.literal("abc"), s -> s.length() * 10f, 100f).get(0);
        assertEquals(1, RichTextLayout.charAt(line, s -> s.length() * 10f, 15f));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现**

按“当前行已装宽度 + 下一个码元宽 > maxWidth 且当前行非空”换行；换行时不在代理对中间切，空格直接丢弃到下一行。每行记录它在原 `RichText` 字符串中的 `textStart/textEnd`，行内 `runs` 是按需切好的片段。

- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/atom/chat/text/TextMeasurer.java src/main/java/com/atom/chat/text/RichTextLayout.java src/test/java/com/atom/chat/text/RichTextLayoutTest.java
git commit -m "Add pure rich text wrapping and char hit testing"
```

---

### Task 3: ChatMessage 携带富文本部件

**Files:**
- Modify: `src/main/java/com/atom/chat/chat/ChatMessage.java`
- Modify: `src/test/java/com/atom/chat/chat/ChatMessageTest.java`

**Interfaces:**
- Produces:
  - `ChatMessage.getSenderRich()` → `RichText`（可为空）
  - `ChatMessage.getContentRich()` → `RichText`
  - 新增全参构造：`ChatMessage(Text component, boolean own, boolean system, String quoteName, String quoteText, UUID senderUuid, String senderName, String profileName, String contentText, RichText senderRich, RichText contentRich)`

- [ ] **Step 1: 写失败测试**

```java
@Test
void richPartsAreStored() {
    RichText sender = RichText.literal("Alice");
    RichText content = RichText.literal("hi").linkifyUrls();
    ChatMessage msg = new ChatMessage(Text.literal("<Alice> hi"), false, false,
            null, null, null, "Alice", "Alice", "hi", sender, content);
    assertEquals("Alice", msg.getSenderRich().getString());
    assertEquals("hi", msg.getContentRich().getString());
}
```

- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 修改 `ChatMessage`**

保留旧构造器，委托给新全参构造器；旧构造器用 `RichText.literal(...)` 生成纯文本富文本，保证现有调用/测试不破坏。`getDisplayText()` 改为 `getContentRich().getString()` 的兼容返回，不再只依赖字符串剥离。

- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/atom/chat/chat/ChatMessage.java src/test/java/com/atom/chat/chat/ChatMessageTest.java
git commit -m "Store rich sender/content parts in ChatMessage"
```

---

### Task 4: 捕获层携带组件级富文本

**Files:**
- Modify: `src/main/java/com/atom/chat/chat/SenderMeta.java`
- Modify: `src/main/java/com/atom/chat/mixin/MessageHandlerMixin.java`
- Modify: `src/main/java/com/atom/chat/mixin/ChatHudMixin.java`
- Test: `src/test/java/com/atom/chat/chat/SenderMetaTest.java`

**Interfaces:**
- Produces:
  - `SenderMeta(UUID, String senderName, String profileName, String contentText, boolean system, Text senderComponent, Text contentComponent)`
  - 兼容旧 5 参 record 构造（`senderComponent/contentComponent = null`）

- [ ] **Step 1: 写失败测试**

```java
@Test
void carriesStyledComponents() {
    Text sender = Text.literal("Alice").setStyle(Style.EMPTY.withUnderline(true));
    SenderMeta meta = new SenderMeta(null, "Alice", "Alice", "hi", false, sender, Text.literal("hi"));
    assertEquals(sender, meta.senderComponent());
}
```

- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 修改 `MessageHandlerMixin`**

- 签名分支 `processChatMessageInternal`：`new SenderMeta(uuid, profile, profile, content, false, params.name(), message.getContent())`
- `method_45745`/无签名分支：`params.name()` 作为 sender，`message` 作为 content。
- 系统 `onGameMessage` 不设组件（整条 Text 即系统内容）。

- [ ] **Step 4: 修改 `ChatHudMixin`**

有组件时：

```java
RichText senderRich = meta.senderComponent() != null ? RichText.of(meta.senderComponent()) : RichText.empty();
RichText contentRich = meta.contentComponent() != null ? RichText.of(meta.contentComponent()).linkifyUrls() : RichText.literal(content);
```

`onGameMessage` 文本守卫路径没有组件，本轮先继续用 `RichText.literal(content)` 保持现状；Task 5 会把它替换为 `ChatPipeline.sliceRichText` 样式保留切片。

- [ ] **Step 5: 运行测试确认通过**
- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/atom/chat/chat/SenderMeta.java src/main/java/com/atom/chat/mixin/MessageHandlerMixin.java src/main/java/com/atom/chat/mixin/ChatHudMixin.java src/test/java/com/atom/chat/chat/SenderMetaTest.java
git commit -m "Carry styled sender and content components through capture"
```

---

### Task 5: 装饰文本行的富文本切片

**Files:**
- Modify: `src/main/java/com/atom/chat/chat/MessagePresentation.java`
- Modify: `src/main/java/com/atom/chat/chat/ChatPipeline.java`
- Modify: `src/main/java/com/atom/chat/mixin/ChatHudMixin.java`
- Test: `src/test/java/com/atom/chat/chat/RichChatPartsTest.java`

**Interfaces:**
- Produces:
  - `record RichChatParts(RichText sender, RichText content)`
  - `MessagePresentation.PlayerLine` 增加 `int labelEnd`（装饰名结束位，分隔符起点之前）
  - `static Optional<RichChatParts> ChatPipeline.sliceRichText(Text fullLine, SenderMeta meta)`

- [ ] **Step 1: 写失败测试**

```java
@Test
void slicesDecoratedLine() {
    Text line = Text.literal("[萌新]player>>谁能给我钻石？")
            .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player ")));
    RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
            .orElseThrow();
    assertEquals("[萌新]player", parts.sender().getString());
    assertEquals("谁能给我钻石？", parts.content().getString());
}
```

- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 修改解析器**

`parseDecoratedPlayerLine` 现在返回的 `contentStart` 是“跳过分隔符后”的位置；新增 `labelEnd` 为“分隔符前”的位置。`ChatPipeline.sliceRichText` 流程：

1. 用 `meta.senderName/profileName` 跑 `MessagePresentation.parseDecoratedPlayerLine(fullLine.getString(), candidates)`。
2. 得到 `nameStart/labelEnd/contentStart`。
3. `RichText full = RichText.of(fullLine)`。
4. `sender = full.slice(0, labelEnd)`，`content = full.slice(contentStart, full.length()).linkifyUrls()`。
5. 解析失败返回 `Optional.empty()`。

- [ ] **Step 4: 接入 `ChatHudMixin`**

在 Task 4 保留的“无组件文本守卫路径”中，不再直接用 `RichText.literal(content)`，改为：

```java
RichChatParts sliced = ChatPipeline.sliceRichText(message, meta);
RichText senderRich = sliced != null ? sliced.sender() : RichText.empty();
RichText contentRich = sliced != null ? sliced.content().linkifyUrls() : RichText.literal(content);
```

切片失败（名字不在已知玩家/不是玩家行）保持系统灰字，不误归玩家。

- [ ] **Step 5: 运行测试确认通过**
- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/atom/chat/chat/MessagePresentation.java src/main/java/com/atom/chat/chat/ChatPipeline.java src/main/java/com/atom/chat/mixin/ChatHudMixin.java src/test/java/com/atom/chat/chat/RichChatPartsTest.java
git commit -m "Slice styled sender and content from decorated chat lines"
```

---

### Task 6: 正式翻译键分类器

**Files:**
- Modify: `src/main/java/com/atom/chat/chat/ChatClassifier.java`
- Test: `src/test/java/com/atom/chat/chat/ChatClassifierTest.java`

**Interfaces:**
- Produces:
  - `enum Route { PLAYER, SYSTEM, PRIVATE, UNKNOWN }` — `PRIVATE` 先留接口，本轮不做私聊 UI
  - `static Route classifyByKey(Text message)`
  - `isVanillaBroadcast` 改为委托 `classifyByKey(message) == SYSTEM`，且不再把 `chat.type.team.text/sent` 当系统

规则（2026-09-04 用户拍板）：
- `PLAYER` 正键：`chat.type.text`、`chat.type.team.text`、`chat.type.team.sent`
- `PRIVATE` 预留键：`commands.message.display.incoming`、`commands.message.display.outgoing`
- `SYSTEM` 负键：沿用现有 `isVanillaBroadcast` 集合，但**排除** `chat.type.team.text/sent`
- 其它返回 `UNKNOWN`

- [ ] **Step 1: 写失败测试**

```java
@Test
void classifiesVanillaPlayerAndSystemKeys() {
    assertEquals(Route.PLAYER, ChatClassifier.classifyByKey(Text.translatable("chat.type.text", "Alice", "hi")));
    assertEquals(Route.PLAYER, ChatClassifier.classifyByKey(Text.translatable("chat.type.team.text", "Alice", "hi")));
    assertEquals(Route.SYSTEM, ChatClassifier.classifyByKey(Text.translatable("multiplayer.player.joined", "Alice")));
    assertEquals(Route.PRIVATE, ChatClassifier.classifyByKey(Text.translatable("commands.message.display.incoming", "Alice", "hi")));
    assertEquals(Route.UNKNOWN, ChatClassifier.classifyByKey(Text.literal("plain")));
}
```

- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 `classifyByKey`**，并让 `isVanillaBroadcast` 调用它保持兼容。
- [ ] **Step 4: 在 `ChatHudMixin` fallback 入口接入**：无 capture 且 `classifyByKey(message) == SYSTEM` 直接系统；`== PLAYER/PRIVATE` 才允许文本守卫尝试；`UNKNOWN` 走现有文本守卫。`PRIVATE` 本轮不建私聊 UI，只保留路由分类供未来 `/msg`、`/tell` 使用。
- [ ] **Step 5: 运行测试确认通过**
- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/atom/chat/chat/ChatClassifier.java src/test/java/com/atom/chat/chat/ChatClassifierTest.java src/main/java/com/atom/chat/mixin/ChatHudMixin.java
git commit -m "Add formal translation-key chat classifier"
```

---

### Task 7: Skia 富文本渲染 + ClickableSpan 收集

**Files:**
- Create: `src/main/java/com/atom/chat/render/ClickableSpan.java`
- Create: `src/main/java/com/atom/chat/render/RichTextRenderer.java`
- Modify: `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`（消息/名字/系统绘制与 `messageHeight` 切换到 layout）

**Interfaces:**
- Produces:
  - `record ClickableSpan(float x, float y, float w, float h, Style style)`
  - `class RichTextRenderer`
    - `static List<RichLine> wrapFor(RichText text, Font font, float maxWidth)`（内部用 `s -> SkiaFontRenderer.getStringWidth(font, s)` 作 `TextMeasurer`）
    - `static void drawLines(Canvas canvas, Font font, List<RichLine> lines, float x, float centerY, float lineHeight, int fallbackColor, List<ClickableSpan> sink, boolean addClickable)`
    - `static float width(Font font, RichLine line)`

- [ ] **Step 1: 先让现有测试全绿**：`./gradlew test`
- [ ] **Step 2: 在 `AtomChatScreen` 加字段**

```java
private final List<ClickableSpan> clickableSpans = new ArrayList<>();
private ClickableSpan pendingClickSpan;
private boolean pendingClickMoved;
```

- [ ] **Step 3: 绘制接入**

`drawMessages` 每帧 `clickableSpans.clear()`；`drawMessage`/`drawSystemMessage`/`drawMessageName` 不再调 `SkiaFontRenderer.drawLines/drawText`，改调 `RichTextRenderer`。颜色规则：
- 名字/正文 fallback：玩家白 `textPrimary()`、系统灰 `textSecondary()`。
- `Style.getColor() != null` 时用原色。
- run 的 style 有 ClickEvent 或 `isUnderlined()` 时画下划线。
- 有 ClickEvent/HoverEvent 的 run 以行内真实 x/y/w/h 写入 `clickableSpans`。

- [ ] **Step 4: 同步 `messageHeight`**

高度计算改 `RichTextRenderer.wrapFor(...).size()`，确保与绘制同源。

- [ ] **Step 5: 构建 + 全测试**

Run: `./gradlew test build`
Expected: PASS/EXIT=0

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/atom/chat/render/ClickableSpan.java src/main/java/com/atom/chat/render/RichTextRenderer.java src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java
git commit -m "Render rich text runs and collect clickable spans in Skia"
```

---

### Task 8: 点击 + 拖选共存 + tooltip

**Files:**
- Modify: `src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`

- [ ] **Step 1: 鼠标按下记录候选**

在现有 `mouseClicked` 左键命中文本行处（约 `:2127`），如果 `findClickableSpan(mx, my)` 非空，则：

```java
pendingClickSpan = findClickableSpan(mx, my);
pendingClickMoved = false;
```

其余选择逻辑照旧（这样从可点击文字上也能起手拖选）。

- [ ] **Step 2: mouseDragged 标记移动**

在 `mouseDragged` 的 `selectionMoved = true` 同一处设置 `pendingClickMoved = true`；拖出文本也设 true。

- [ ] **Step 3: mouseReleased 无拖动才触发**

```java
if (pendingClickSpan != null && !pendingClickMoved) {
    ClickableSpan span = pendingClickSpan;
    pendingClickSpan = null;
    if (span.style().getClickEvent() != null && findClickableSpan(mx, my) == span) {
        this.handleTextClick(span.style());
        return true;
    }
}
pendingClickSpan = null;
```

`handleTextClick` 继承自 `ChatScreen`，已实现 SUGGEST_COMMAND 填框、RUN_COMMAND 执行、OPEN_URL/OPEN_FILE/COPY 等原版语义。

- [ ] **Step 4: tooltip**

`render(DrawContext, ...)` 在 Skia 画完后、suggestor 渲染前后加：

```java
Style hovered = findHoveredStyle(mouseX, mouseY);
if (hovered != null && hovered.getHoverEvent() != null) {
    context.drawHoverEvent(this.textRenderer, hovered, mouseX, mouseY);
}
```

- [ ] **Step 5: 手工验收清单**
- 玩家名带 `/tell`：显示下划线，单击填入 `/msg 名字 `。
- Xaero/FTB 点击文本：命令型直接执行；链接型弹原版确认。
- 裸 `http(s)`：下划线，点击打开。
- 悬停带 HoverEvent 文字：显示原版 tooltip。
- 从可点击文字起手拖选仍可复制。
- `[萌新]player>>正文` 在系统通道能显示玩家头像气泡；`<公告>xx` 显示系统灰字。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java
git commit -m "Wire clickable spans to click handling and vanilla hover tooltips"
```

---

## Self-Review

- Q1 点击/下划线：Task 1/2/7/8 覆盖。
- Q2 翻译键分流 + 文本兜底：Task 4/5/6 覆盖。
- Q3 hover tooltip：Task 7/8 覆盖。
- “从零分析、仅通用解法参考 e33chat”：Task 1/2/7 全部基于 Text→run→layout 自建，未搬 vanilla `FormattedCharSequence`。
- 已知留白：NCR 碾键场景不单独验收；粗体/斜体/删除线/混淆不做；引用胶囊仍纯文本；插件昵称与 tab 完全脱节的老死角不承诺。
