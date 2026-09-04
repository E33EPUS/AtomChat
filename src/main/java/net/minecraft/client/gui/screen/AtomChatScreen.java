package net.minecraft.client.gui.screen;
import com.atom.chat.AtomChat;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.emote.EmoteImageCache;
import com.atom.chat.emote.EmoteStore;
import com.atom.chat.image.AvatarRenderer;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.ImageSaver;
import com.atom.chat.image.SkinResolver;
import com.atom.chat.image.ImageUploader;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.AtomChatState;
import com.atom.chat.nav.NavigationStack;
import com.atom.chat.page.ConversationListPage;
import com.atom.chat.page.PageHost;
import com.atom.chat.page.PlaceholderPage;
import com.atom.chat.font.FontManager;
import com.atom.chat.mixin.MouseHandlerAccessor;
import com.atom.chat.render.Animator;
import com.atom.chat.render.ClickableSpan;
import com.atom.chat.render.Easing;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.util.ClipboardImages;
import com.atom.chat.util.FilePicker;
import com.atom.chat.util.ImageFiles;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.skija.SamplingMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;
import net.fabricmc.loader.api.FabricLoader;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AtomChatScreen extends ChatScreen implements PageHost {
    /** Which context menu is open: normal message bubble actions or player-avatar actions. */
    private enum ContextMenuMode { BUBBLE, AVATAR }

    /** How this screen was opened: from the vanilla chat box or from the AtomChat key. */
    public enum AtomChatOpenMode { DIRECT_WORLD, RESTORE }

    private final NavigationStack<AppPage> navigation;

    private final ConversationListPage conversationListPage = new ConversationListPage(this);
    private final PlaceholderPage profilePage = new PlaceholderPage(AppPage.PROFILE);
    private final PlaceholderPage settingsPage = new PlaceholderPage(AppPage.SETTINGS);

    private final String originalChatText;
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final ImageUploader imageUploader = new ImageUploader();
    private final List<MessageHit> hits = new ArrayList<>();
    /** Local emote pack; see {@link EmoteStore} for the persistence rules. */
    private final EmoteStore emoteStore = new EmoteStore(
            FabricLoader.getInstance().getConfigDir().resolve("atomchat/emotes"));
    private final EmoteImageCache emoteImageCache = new EmoteImageCache();

    private boolean inputFocused = true;
    /** Vanilla command completion over ChatScreen's chatField, anchored to our input row. */
    private float scrollY;
    private float maxScroll;
    /** List viewport height from the previous frame; detects input-bar growth. */
    private float lastListHeight = -1.0F;
    private ChatMessage replyTarget;
    private boolean emojiOpen;
    private int emojiTab;
    private int emojiScroll;
    private ChatMessage contextMessage;
    private float contextX;
    private float contextY;
    private ContextMenuMode contextMenuMode = ContextMenuMode.BUBBLE;

    // Per-cell hover fade shared by the emoji / kaomoji / emote grids.
    private final Map<Integer, Float> cellHover = new HashMap<>();
    private final float[] contextMenuHover = new float[4];
    // Emoji tab transition: double-layer content slide + sliding indicator.
    private final Animator tabContentAnim = new Animator(Easing::easeInOutCubic);
    private final Animator tabIndicatorAnim = new Animator(Easing::easeInOutCubic);
    private int tabAnimFrom = -1;
    private int tabAnimTo = -1;


    // Animation state — durations live in UiMotion so every transition is tuned
    // in one place and none of them can drift back to a sluggish value.
    private static final long OPEN_ANIM_MS = UiMotion.PANEL_MS;
    private static final long MESSAGE_ANIM_MS = UiMotion.MESSAGE_MS;
    /**
     * Once a message has been visible this long it is considered settled by
     * time alone, so the settled set never needs to hold it — that keeps the
     * "never replay an entrance" guarantee bounded to recent messages.
     */
    private static final long ENTRANCE_SETTLE_GUARD_MS = 5000L;
    private static final long SCROLL_ANIM_MS = UiMotion.SCROLL_SNAP_MS;
    private static final long WHEEL_ANIM_MS = UiMotion.SCROLL_WHEEL_MS;
    // Toolbar icons are kept as inline SVG path data (not assets): three tiny
    // paths are cheaper than a resource pipeline, stay crisp at every scale,
    // and are trivial to recolour for hover/pressed/theme states. The paths use
    // a 20x20 logical space; drawIcon() fits them into the button bounds.
    private static final String ICON_IMAGE_SVG = "M5.5 3 C4.7 3 4 3.7 4 4.5 L4 15.5 C4 16.3 4.7 17 5.5 17 L14.5 17 C15.3 17 16 16.3 16 15.5 L16 4.5 C16 3.7 15.3 3 14.5 3 Z"
            + " M7.5 6.5 m-1.3 0 a1.3 1.3 0 1 0 2.6 0 a1.3 1.3 0 1 0 -2.6 0"
            + " M4.3 15.7 L8 11.6 L10.6 14.1 L13.8 10.4 L15.7 12.3";
    private static final String ICON_EMOJI_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M7 8.6 v1.3 M13 8.6 v1.3"
            + " M6.8 12.2 C8.5 14.3 11.5 14.3 13.2 12.2";
    // Feather-style send: one diagonal fold + the paper-plane outline.
    private static final String ICON_SEND_SVG = "M18 2.5 L9.5 11"
            + " M18 2.5 L13.5 18.5 L9.5 11 L2.5 7.5 Z";
    // Context-menu icons, same 20x20 line-icon language as the toolbar.
    private static final String ICON_COPY_SVG = "M5 3 L11 3 L11 9 L5 9 Z M8 7 L14 7 L14 13 L8 13 Z";
    private static final String ICON_QUOTE_SVG = "M3 3 L17 3 L17 12 L10 12 L6 16 L7 12 L3 12 Z"
            + " M7 6 L7 9 M10 6 L10 9";
    private static final String ICON_SAVE_SVG = "M10 3 L10 11 M7 8 L10 11 L13 8"
            + " M4 15 L4 17 L16 17 L16 15";
    // Avatar context-menu icons, same 20x20 line-icon language.
    private static final String ICON_MENTION_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M10 8 v3"
            + " M7.5 10.5 a2.5 2.5 0 1 0 5 0 v-.5";
    private static final String ICON_WHISPER_SVG = "M3 4 L17 4 L17 14 L10 14 L6 18 L7 14 L3 14 Z";
    private static final String ICON_TP_SVG = "M3 17 L17 3 M17 3 L10 3 M17 3 L17 10";
    private static final String ICON_BLOCK_SVG = "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M4.5 4.5 L15.5 15.5";
    private static final io.github.humbleui.skija.Path ICON_IMAGE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_IMAGE_SVG);
    private static final io.github.humbleui.skija.Path ICON_EMOJI_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_EMOJI_SVG);
    private static final io.github.humbleui.skija.Path ICON_SEND_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_SEND_SVG);
    private static final io.github.humbleui.skija.Path ICON_COPY_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_COPY_SVG);
    private static final io.github.humbleui.skija.Path ICON_QUOTE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_QUOTE_SVG);
    private static final io.github.humbleui.skija.Path ICON_SAVE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_SAVE_SVG);
    private static final io.github.humbleui.skija.Path ICON_MENTION_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_MENTION_SVG);
    private static final io.github.humbleui.skija.Path ICON_WHISPER_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_WHISPER_SVG);
    private static final io.github.humbleui.skija.Path ICON_TP_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_TP_SVG);
    private static final io.github.humbleui.skija.Path ICON_BLOCK_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_BLOCK_SVG);

    // Shell icons: bottom tabs and the future back affordance, same 20x20
    // line-icon language as the toolbar/menus above.
    private static final String ICON_TAB_CHAT_SVG = "M4 3 L16 3 L16 13 L10 13 L6 17 L7 13 L4 13 Z";
    private static final String ICON_TAB_PROFILE_SVG = "M10 3 a3.5 3.5 0 1 1 0 7 a3.5 3.5 0 1 1 0 -7"
            + " M4 17 C4 13.5 6.5 11.5 10 11.5 C13.5 11.5 16 13.5 16 17";
    private static final String ICON_TAB_SETTINGS_SVG = "M10 6.5 a3.5 3.5 0 1 0 0 7 a3.5 3.5 0 1 0 0 -7"
            + " M10 2.5 v2 M10 15.5 v2 M3.5 10 h2 M14.5 10 h2"
            + " M5.3 5.3 l1.4 1.4 M13.3 13.3 l1.4 1.4 M14.7 5.3 l-1.4 1.4 M6.7 13.3 l-1.4 1.4";
    private static final String ICON_BACK_SVG = "M4 10 L10 4 M4 10 L10 16 M4 10 L18 10";
    private static final io.github.humbleui.skija.Path ICON_TAB_CHAT_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    private static final io.github.humbleui.skija.Path ICON_TAB_PROFILE_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_TAB_PROFILE_SVG);
    private static final io.github.humbleui.skija.Path ICON_TAB_SETTINGS_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_TAB_SETTINGS_SVG);
    private static final io.github.humbleui.skija.Path ICON_BACK_PATH =
            io.github.humbleui.skija.Path.makeFromSVGString(ICON_BACK_SVG);

    private static final Pattern CICODE = Pattern.compile(
            "\\[\\[CICode,url=([^,\\]]+),name=([^,\\]]*)(?:,w=(\\d+),h=(\\d+))?\\]\\]");
    private static final int GLFW_KEY_V = 86;
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    private long closeStart;
    private float panelProgress = 1.0F;
    private boolean blurDrawnThisFrame;
    private boolean firstRender = true;
    private boolean scrollToBottom = true;
    private float scrollTarget;
    private boolean scrollAnimActive;
    private float scrollAnimFrom;
    private float scrollAnimTo;
    private long scrollAnimStart;
    private long scrollAnimMs = SCROLL_ANIM_MS;
    private int pressedButton = -1;
    private long pressTime;

    // Scrollbar state (e33chat style: hover/sheet fade, drag to scroll)
    private float scrollBarAlpha;
    // Per-frame animation state (smooth hover/popup transitions)
    private final float[] buttonHover = new float[3];
    private float scrollEmphasis;
    private float emojiAnim;
    private float contextAnim;
    private ChatMessage lastContextMessage;
    private ContextMenuMode lastContextMenuMode = ContextMenuMode.BUBBLE;
    private long frameDt = 16;
    private long lastFrameMs = System.currentTimeMillis();
    private boolean draggingScrollbar;
    private float dragStartY;
    private float dragStartScroll;
    private long lastScrollbarFrame;
    /** Scrollbar colour state: only a held left button turns the thumb blue. */
    private float scrollActive;

    // Multi-line input: the bar grows upward by whole line heights, and once the
    // text passes INPUT_MAX_LINES it scrolls inside the fixed box.
    private float inputExtraH;
    /**
     * Tracks the height transition as start/end/time rather than per-frame lerp:
     * UiMotion.approach is unitless, so on a pixel-valued target it only covers
     * a fraction of the distance per frame and leaves a multi-hundred-ms tail.
     * Animator guarantees the bar reaches its target height within INPUT_GROW_MS.
     */
    private final Animator inputHeightAnim = new Animator(t -> t);
    private int inputScrollLine;
    private String inputWrapText;
    private float inputWrapWidth = -1.0F;
    private List<String> inputWrapCache;

    /** Set while an upload is in flight; the input placeholder reads it. */
    private volatile boolean imageUploading;
    /** Short-lived hint shown in the empty input placeholder (save errors etc). */
    private volatile String transientHint;
    private long transientHintSetAt;
    /** GLFW drop callback, installed while this screen is open (MC sets none). */
    private GLFWDropCallback dropCallback;

    private long lastAvatarClickTime;
    private int lastAvatarClickIndex = -1;
    private int pokeIndex = -1;
    private long pokeStartTime;

    // Message text drag-selection state (Skia-drawn highlight; Ctrl+C copies).
    private ChatMessage selectionMessage;
    private int selectionAnchorLine = -1;
    private int selectionAnchorChar = -1;
    private int selectionFocusLine = -1;
    private int selectionFocusChar = -1;
    private boolean selecting;
    private boolean selectionMoved;
    private List<String> selectionMessageLines = List.of();

    /** Clickable/hoverable spans collected while drawing the latest frame. */
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();
    /** Pending click candidate used by the click/drag coexistence flow (Task 8). */
    private ClickableSpan pendingClickSpan;
    private boolean pendingClickMoved;

    /**
     * New-message entrance animation starts when the message first becomes
     * visible, not when it was added. Bursts arriving while auto-scroll is
     * still moving used to spend most of their 140ms off-screen and looked
     * like a rushed flash; this map records the first visible frame instead.
     */
    private final Map<ChatMessage, Long> messageEnterStart = new HashMap<>();
    /**
     * Messages whose entrance animation has already played out. The start entry
     * must NOT be deleted when the animation finishes: entranceEase() treats a
     * missing entry as "first visible frame" and would re-stamp the timestamp,
     * so the 140ms animation restarted on the very next frame — an endless loop
     * that only stopped when the screen was reopened and openStart moved past
     * the message. Once a message settles it must NEVER replay while this screen
     * is open, even after it scrolls out of view and back — scrolling through
     * history should be silent. The set is pruned by
     * {@link #ENTRANCE_SETTLE_GUARD_MS} so it stays bounded to recent arrivals.
     */
    private final Set<ChatMessage> messageEnterSettled = new HashSet<>();
    private long lastEntrancePrune;

    public AtomChatScreen(String originalChatText) {
        this(originalChatText, AtomChatOpenMode.DIRECT_WORLD);
    }

    public AtomChatScreen(String originalChatText, AtomChatOpenMode mode) {
        super(originalChatText);
        this.originalChatText = originalChatText;
        this.navigation = new NavigationStack<>(AppPage.CHAT_LIST);
        if (mode == AtomChatOpenMode.DIRECT_WORLD) {
            navigation.replaceWithRoot(AppPage.CHAT_LIST);
            navigation.push(AppPage.WORLD_CHAT);
        } else {
            List<AppPage> saved = AtomChatState.snapshot();
            navigation.replaceWithRoot(saved.get(0));
            for (int i = 1; i < saved.size(); i++) {
                navigation.push(saved.get(i));
            }
        }
    }

    private AppPage topPage() {
        return navigation.peek();
    }

    private boolean isWorldChatPage() {
        return topPage() == AppPage.WORLD_CHAT;
    }

    /**
     * Hit rect for the world-chat header's SVG back button. It is fixed to the
     * left edge of the header card and vertically centered, matching where the
     * icon is drawn.
     */
    private boolean isBackButtonHit(float vmx, float vmy) {
        float size = s(36);
        UiLayout.Rect header = layout().header;
        return vmx >= header.x() + s(4) && vmx <= header.x() + s(4) + size
                && vmy >= header.y() + (header.h() - size) / 2.0F
                && vmy <= header.y() + (header.h() - size) / 2.0F + size;
    }

    @Override
    public void pushPage(AppPage page) {
        navigation.push(page);
    }

    @Override
    public void popPage() {
        navigation.pop();
    }

    @Override
    public void switchRoot(AppPage root) {
        navigation.replaceWithRoot(root);
    }

    public String getOriginalChatText() {
        return originalChatText;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (closing && System.currentTimeMillis() - closeStart >= OPEN_ANIM_MS) {
            this.client.setScreen(null);
            return;
        }
        panelProgress = currentPanelProgress();

        // The blur pre-pass is raw GL and must run before Skia paints the panel.
        // Load the shader first so drawPanel knows whether it may use the
        // translucent tint or must keep the solid fallback. The actual draw
        // result is tracked in blurDrawnThisFrame so a silent shader no-op can
        // never strip the solid background again.
        blurDrawnThisFrame = false;
        if (AtomChatConfig.get().blurEnabled) {
            PanelBlurRenderer.ensureLoaded();
        }

        graphics.checkFrameBufferId();
        Runnable preUi = null;
        if (AtomChatConfig.get().blurEnabled && PanelBlurRenderer.isAvailable()) {
            preUi = () -> {
                try {
                    float strokeWidth = s(3);
                    float slide = (panelProgress - 1.0F) * 36.0F;
                    float vx = panelX() + strokeWidth + slide;
                    float vy = panelY() + strokeWidth;
                    float vw = panelWidth() - strokeWidth * 2.0F;
                    float vh = panelHeight() - strokeWidth * 2.0F;
                    float vRadius = UiTokens.PANEL_RADIUS - strokeWidth;
                    double density = uiDensity();
                    double scaleFactor = this.client.getWindow().getScaleFactor();
                    float gx = (float) (vx * density / scaleFactor);
                    float gy = (float) (vy * density / scaleFactor);
                    float gw = (float) (vw * density / scaleFactor);
                    float gh = (float) (vh * density / scaleFactor);
                    float gr = (float) (vRadius * density / scaleFactor);
                    blurDrawnThisFrame = PanelBlurRenderer.render(
                            context.getMatrices().peek().getPositionMatrix(),
                            gx, gy, gw, gh, gr, panelProgress);
                } catch (Throwable t) {
                    AtomChat.LOGGER.warn("AtomChat panel blur pre-pass failed, using solid background", t);
                    blurDrawnThisFrame = false;
                }
            };
        }

        // No super.render: ChatScreen/Screen would draw the vanilla input box and
        // widget chrome; our UI is fully Skia-drawn, the suggestor renders explicitly.
        graphics.draw(preUi, (canvas, worldSnapshot) -> drawPhone(canvas, worldSnapshot, mouseX, mouseY, delta));
        // The hidden EditBox stays positioned so the IME floating window anchors
        // correctly; its text/cursor are drawn by Skia above. The suggestion popup
        // still renders through the vanilla pipeline on top. Root pages do not
        // show the composer, so none of this may run outside WORLD_CHAT.
        if (!closing && chatField != null && isWorldChatPage()) {
            positionInputField(layout());
            if (chatInputSuggestor != null) {
                chatInputSuggestor.render(context, mouseX, mouseY);
            }
        }

        // Hover tooltips are drawn through the vanilla pipeline after the Skia
        // panel and the suggestion popup so they stay readable on top of both.
        // Root pages keep their own hover model; stale world spans must not show.
        Style hovered = isWorldChatPage() ? findHoveredStyle(toVirtualX(mouseX), toVirtualY(mouseY)) : null;
        if (hovered != null && hovered.getHoverEvent() != null) {
            context.drawHoverEvent(this.textRenderer, hovered, mouseX, mouseY);
        }
    }

    private float currentPanelProgress() {
        long now = System.currentTimeMillis();
        if (closing) {
            return 1.0F - Easing.easeOutCubic(Math.min(1.0F, (now - closeStart) / (float) OPEN_ANIM_MS));
        }
        return Easing.easeOutCubic(Math.min(1.0F, (now - openStart) / (float) OPEN_ANIM_MS));
    }

    /** Collapses the suggestion popup and clears the gray ghost suffix. */
    private void dismissSuggestor() {
        if (chatInputSuggestor != null) {
            chatInputSuggestor.setWindowActive(false);
            chatField.setSuggestion(null);
        }
    }

    /** GUI-space anchor for the suggestion window: bottom edge of the popup. */
    private int anchorInputTopY() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        // The popup must clear the whole input bar (button row + text row),
        // not just the caret line; otherwise it overlaps the bar's top half.
        return (int) Math.round((layout().inputBar.y() - s(4)) * density / scaleFactor);
    }

    private int anchorInputLeftX() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        return (int) Math.round((layout().inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor);
    }

    private void positionInputField(UiLayout layout) {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        chatField.setX((int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor));
        chatField.setY((int) Math.round(caretLineTopY() * density / scaleFactor));
        chatField.setWidth((int) Math.max(10.0F, Math.round((layout.inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F) * density / scaleFactor)));
        chatField.setHeight((int) Math.round(inputLineHeight() * density / scaleFactor));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // World stays fully visible, same as vanilla chat; the panel provides its own background.
    }

    @Override
    protected void init() {
        super.init();
        // Swap the vanilla suggestor for our anchored one on the same chat field;
        // ChatScreen's changed listener drives whatever sits in chatInputSuggestor.
        this.chatInputSuggestor = new AtomChatSuggestor(this.client, this, this.chatField,
                this.client.textRenderer, false, false, 1, 10, true, -805306368,
                this::anchorInputTopY, this::anchorInputLeftX);
        this.chatInputSuggestor.setCanLeave(false);
        this.chatInputSuggestor.setWindowActive(false);
        // init() also runs on every resize, hence the guard inside.
        installDropCallback();
    }

    private String inputGetText() {
        return chatField != null ? chatField.getText() : "";
    }

    private void inputSetText(String text) {
        if (chatField != null) {
            chatField.setText(text == null ? "" : text);
        }
    }

    private void inputAppend(String text) {
        if (chatField == null) {
            return;
        }
        String current = chatField.getText();
        if (current.length() + text.length() <= 256) {
            chatField.setText(current + text);
        }
        inputFocused = true;
        setFocused(chatField);
        chatField.setFocused(true);
    }

    private void drawPhone(Canvas canvas, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        float x = panelX();
        float y = panelY();
        float progress = panelProgress;
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * progress), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - 32.0F, y - 32.0F, panelWidth() + 64.0F, panelHeight() + 64.0F), layer);
            canvas.translate((progress - 1.0F) * 36.0F, 0.0F);
            // The world snapshot sits inside the saveLayer/translate stack so it
            // fades in with the panel and slides with it — no special handling.
            drawPanel(canvas, x, y, worldSnapshot, mouseX, mouseY, delta);
            canvas.restore();
        }
        canvas.restore();
    }

    private void requestClose() {
        if (!closing) {
            closing = true;
            closeStart = System.currentTimeMillis();
        }
    }

    /**
     * GLFW only hands file drops to whoever is listening, and Minecraft
     * registers no drop callback at all — the Win32 backend already calls
     * DragAcceptFiles, so the events have been arriving and being discarded.
     * The callback fires on the render thread, from inside glfwPollEvents.
     */
    private void installDropCallback() {
        if (dropCallback != null) {
            return;
        }
        try {
            dropCallback = GLFW.glfwSetDropCallback(this.client.getWindow().getHandle(),
                    (win, count, names) -> onFilesDropped(count, names));
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to install the file drop callback", t);
        }
    }

    private void uninstallDropCallback() {
        if (dropCallback == null) {
            return;
        }
        try {
            GLFW.glfwSetDropCallback(this.client.getWindow().getHandle(), null);
            dropCallback.free();
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to remove the file drop callback", t);
        }
        dropCallback = null;
    }

    /** Window-wide: the drop event carries no cursor position we could hit-test with. */
    private void onFilesDropped(int count, long names) {
        // File drops only feed the world-chat composer; root pages must ignore them.
        if (!isWorldChatPage()) {
            return;
        }
        try {
            PointerBuffer buffer = MemoryUtil.memPointerBuffer(names, count);
            for (int i = 0; i < count; i++) {
                Path file = Path.of(MemoryUtil.memUTF8(buffer.get(i)));
                if (ImageFiles.isImage(file)) {
                    uploadAndAppend(file);
                    return;
                }
            }
            if (count > 0) {
                AtomChat.LOGGER.info("Ignored {} dropped file(s): none was an image", count);
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("Failed to handle dropped files", t);
        }
    }

    @Override
    public void removed() {
        AtomChatState.save(navigation.snapshot());
        uninstallDropCallback();
        // Give back the GPU texture the panel blur was sampling.
        graphics.releaseWorldSnapshot();
        messageEnterStart.clear();
        messageEnterSettled.clear();
        super.removed();
    }

    private void drawPanel(Canvas canvas, float x, float y, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        inputFocused = chatField != null && chatField.isFocused();
        long nowMs = System.currentTimeMillis();
        frameDt = Math.min(50L, Math.max(1L, nowMs - lastFrameMs));
        lastFrameMs = nowMs;
        float emojiTarget = emojiOpen ? 1.0F : 0.0F;
        emojiAnim = UiMotion.approach(emojiAnim, emojiTarget, frameDt, UiMotion.POPUP_MS);
        UiLayout layout = layout();
        UiLayout.Rect panel = layout.rect();
        // Phone bezel: background is inset by the full stroke width so nothing can
        // bleed outside; the white ring itself is drawn LAST (see end of method)
        // so every component sits beneath a clean edge.
        float strokeWidth = s(3);
        // The raw-GL blur pre-pass already painted the rounded blurred image on
        // the main framebuffer. When it is available we only add the translucent
        // tint; otherwise the solid panelBg() stays as the safe fallback.
        boolean blurred = AtomChatConfig.get().blurEnabled && blurDrawnThisFrame;
        int tint = blurred ? UiTokens.PANEL_BLUR_TINT : panelBg();
        try (Paint bg = new Paint().setColor(tint)) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth, panel.y() + strokeWidth,
                    panel.w() - strokeWidth * 2.0F, panel.h() - strokeWidth * 2.0F, UiTokens.PANEL_RADIUS - strokeWidth), bg);
        }

        // Root pages (CHAT_LIST / PROFILE / SETTINGS) share the panel chrome but
        // draw their own header/body and the bottom tab bar instead of the
        // world-chat composer/message stack.
        if (!isWorldChatPage()) {
            UiLayout root = rootLayout();
            drawRootPage(canvas, root);
            drawBottomTabBar(canvas, root);
            drawBezel(canvas, layout);
            return;
        }

        // Header: inset card, same style as the input bar.
        try (Paint header = new Paint().setColor(Color.makeARGB(60, 255, 255, 255))) {
            canvas.drawRRect(RRect.makeXYWH(layout.header.x(), layout.header.y(), layout.header.w(), layout.header.h(), UiTokens.HEADER_RADIUS), header);
        }
        // Back arrow sits at the header's left edge; the title stays centered.
        if (isWorldChatPage()) {
            drawIconCentered(canvas, ICON_BACK_PATH,
                    layout.header.x() + s(4) + s(18),
                    layout.header.y() + layout.header.h() / 2.0F,
                    s(18), textPrimary());
        }
        // Channel name is centered in the card (both axes); the clock stays
        // pinned to the right edge.
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, tr("atomchat.channel.world"),
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.y() + layout.header.h() / 2.0F, textPrimary());
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        Font timeFont = FontManager.font(UiTokens.FONT_TIME);
        SkiaFontRenderer.drawTextRight(canvas, timeFont, time, layout.header.right() - UiTokens.HEADER_PAD_X,
                layout.header.y() + layout.header.h() / 2.0F, textPrimary());

        // Grow the input bar before the list is measured, so the list loses
        // exactly the height the bar gains.
        layout = updateInputLayout(layout);

        drawMessages(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h());

        // Reply bar floats above the input bar. It is drawn after the message
        // list so it always sits on top; the layout keeps an 8px gap below it.
        if (replyTarget != null) {
            UiLayout.Rect reply = layout.replyBar;
            float replyH = s(26);
            SkiaDraw.drawRoundedRect(canvas, reply.x(), reply.y(), reply.w(), replyH, s(8), Color.makeARGB(90, 74, 144, 226));
            Font replyFont = FontManager.font(UiTokens.FONT_NAME);
            String replyLabel = tr("atomchat.reply.to", messageSenderName(replyTarget),
                    abbreviate(replyTarget.getContentText(), 26));
            SkiaFontRenderer.drawText(canvas, replyFont, replyLabel, reply.x() + UiTokens.QUOTE_PAD_X,
                    SkiaFontRenderer.centerBaselineY(replyFont, reply.y() + s(13)), textPrimary());
        }

        // Input bar: one button row (image / emoji … send), text row below.
        // The list layout already ends at this bar's top, so the translucent
        // card never has message content underneath it.
        UiLayout.Rect bar = layout.inputBar;
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), s(18), Color.makeARGB(60, 255, 255, 255));
        drawIconButton(canvas, layout.imageBtn.x(), layout.imageBtn.y(), 0, mouseX, mouseY);
        drawIconButton(canvas, layout.emojiBtn.x(), layout.emojiBtn.y(), 1, mouseX, mouseY);
        drawSendButton(canvas, layout.sendBtn.x(), layout.sendBtn.y(), mouseX, mouseY);

        // Input text: rendered by Skia at fixed density; the hidden EditBox is the
        // input backend (IME/keys) only. It wraps onto a second line (the bar has
        // already grown for it) and scrolls past INPUT_MAX_LINES.
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = inputLineHeight();
        String current = inputGetText();
        float textX = bar.x() + UiTokens.INPUT_TEXT_X;
        List<String> lines = wrappedInput(layout.inputTextMaxWidth());
        int total = lines.size();
        int caretRow = total == 0 ? 0 : caretLine(lines, caretIndex());
        scrollInputToCaret(caretRow, total);
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, total);
        int from = total == 0 ? 0 : Math.min(inputScrollLine, total - shown);

        // Clip to whatever the bar currently has room for, so the text can never
        // spill past the card while the height is still animating.
        float clipTop = layout.inputTextCenterY - lineH / 2.0F;
        float clipBottom = bar.bottom() - UiTokens.INPUT_ROW_PAD;
        canvas.save();
        SkiaDraw.clip(canvas, textX, clipTop, layout.inputTextMaxWidth(), Math.max(0.0F, clipBottom - clipTop), 0.0F);
        // Placeholder stays visible while the field is focused: ChatScreen
        // focuses the chat field the moment the screen opens, so a hint gated
        // on "not focused" was literally never on screen. It doubles as the
        // upload progress readout, which is the only feedback a file drop can
        // give — GLFW reports the drop itself but has no drag-enter to react to.
        if (current.isEmpty()) {
            String hintText;
            if (imageUploading) {
                hintText = tr("atomchat.input.uploading");
            } else if (transientHint != null && System.currentTimeMillis() - transientHintSetAt < 4000L) {
                hintText = transientHint;
            } else {
                hintText = tr("atomchat.input.placeholder");
            }
            String hint = truncateToWidth(inputFont, hintText, layout.inputTextMaxWidth());
            SkiaFontRenderer.drawText(canvas, inputFont, hint, textX,
                    SkiaFontRenderer.centerBaselineY(inputFont, layout.inputTextCenterY), textSecondary());
        } else {
            drawInputSelection(canvas, inputFont, lines, from, shown, textX, layout.inputTextCenterY, lineH);
            for (int i = from; i < from + shown && i < total; i++) {
                float cy = layout.inputTextCenterY + (i - from) * lineH;
                SkiaFontRenderer.drawText(canvas, inputFont, lines.get(i), textX,
                        SkiaFontRenderer.centerBaselineY(inputFont, cy), textPrimary());
            }
        }
        if (inputFocused && chatField != null && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caret = caretIndex();
            float cursorY;
            String measure;
            if (total == 0) {
                // Empty draft: caret sits at the start of the first visible line.
                cursorY = layout.inputTextCenterY;
                measure = "";
            } else {
                int lineStart = 0;
                for (int i = 0; i < caretRow; i++) {
                    lineStart += lines.get(i).length();
                }
                int col = MathHelper.clamp(caret - lineStart, 0, lines.get(caretRow).length());
                cursorY = layout.inputTextCenterY + (caretRow - from) * lineH;
                measure = lines.get(caretRow).substring(0, col);
            }
            float cursorX = textX + SkiaFontRenderer.getStringWidth(inputFont, measure) + 2.0F;
            float cursorH = SkiaFontRenderer.textHeight(inputFont);
            SkiaDraw.drawRoundedRect(canvas, cursorX, cursorY - cursorH / 2.0F, 2.0F, cursorH, 1.0F, textPrimary());
        }
        canvas.restore();

        // Scrollbar (e33chat style): fades in near/hinting scroll, draggable, highlights.
        drawScrollbar(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY));

        drawEmojiPanel(canvas, toVirtualX(mouseX), toVirtualY(mouseY));
        drawContextMenu(canvas, toVirtualX(mouseX), toVirtualY(mouseY));

        // Bezel ring last: nothing at the panel edge can sit on top of it.
        drawBezel(canvas, layout);
    }

    /**
     * White phone-style ring around the panel. Drawn last on both world-chat
     * and root pages so no component can sit on top of the clean edge.
     */
    private void drawBezel(Canvas canvas, UiLayout layout) {
        UiLayout.Rect panel = layout.rect();
        float strokeWidth = s(3);
        try (Paint border = new Paint().setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth).setColor(0xFFFFFFFF)) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth / 2.0F, panel.y() + strokeWidth / 2.0F,
                    panel.w() - strokeWidth, panel.h() - strokeWidth, UiTokens.PANEL_RADIUS), border);
        }
    }

    private UiLayout rootLayout() {
        return UiLayout.ofRoot(panelX(), panelY(), panelWidth(), panelHeight());
    }

    private void drawRootPage(Canvas canvas, UiLayout layout) {
        if (topPage() == AppPage.CHAT_LIST) {
            conversationListPage.render(canvas, layout);
        } else if (topPage() == AppPage.PROFILE) {
            profilePage.render(canvas, layout);
        } else if (topPage() == AppPage.SETTINGS) {
            settingsPage.render(canvas, layout);
        }
    }

    private void drawBottomTabBar(Canvas canvas, UiLayout layout) {
        UiLayout.Rect bar = layout.tabBar;
        if (bar.w() <= 0.0F) {
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), s(18),
                Color.makeARGB(60, 255, 255, 255));
        Font tabFont = FontManager.font(UiTokens.FONT_QUOTE);
        AppPage[] roots = {AppPage.CHAT_LIST, AppPage.PROFILE, AppPage.SETTINGS};
        io.github.humbleui.skija.Path[] icons = {
                ICON_TAB_CHAT_PATH, ICON_TAB_PROFILE_PATH, ICON_TAB_SETTINGS_PATH
        };
        String[] labels = {
                tr("atomchat.tab.chat"), tr("atomchat.tab.profile"), tr("atomchat.tab.settings")
        };
        for (int i = 0; i < 3; i++) {
            float cellCenterX = bar.x() + bar.w() * (i + 0.5F) / 3.0F;
            drawIconCentered(canvas, icons[i], cellCenterX, bar.y() + s(18), s(20), textPrimary());
            SkiaFontRenderer.drawTextCentered(canvas, tabFont, labels[i], cellCenterX,
                    bar.y() + bar.h() - s(12), textPrimary());
        }
    }

    /** Routes a root-page click on one of the three bottom tab cells. */
    private boolean handleBottomTabClick(float vmx, float vmy) {
        UiLayout.Rect bar = rootLayout().tabBar;
        if (bar.w() <= 0.0F || vmx < bar.x() || vmx > bar.right() || vmy < bar.y() || vmy > bar.bottom()) {
            return false;
        }
        int index = Math.max(0, Math.min(2,
                (int) ((vmx - bar.x()) / (bar.w() / 3.0F))));
        AppPage root = index == 0 ? AppPage.CHAT_LIST : (index == 1 ? AppPage.PROFILE : AppPage.SETTINGS);
        switchRoot(root);
        return true;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Minecraft language lookup for all AtomChat UI copy. */
    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private void drawIconButton(Canvas canvas, float bx, float by, int id, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[id] = UiMotion.approach(buttonHover[id], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        int fill = Math.min(255, (int) (70 + buttonHover[id] * 45.0F + (buttonPressed(id) ? 50 : 0)));
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, Color.makeARGB(fill, 255, 255, 255));
        drawIcon(canvas, id == 0 ? ICON_IMAGE_PATH : ICON_EMOJI_PATH, bx, by, textPrimary());
    }

    private void drawSendButton(Canvas canvas, float bx, float by, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[2] = UiMotion.approach(buttonHover[2], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, accent());
        float overlay = buttonHover[2] * 55.0F + (buttonPressed(2) ? 90.0F : 0.0F);
        if (overlay > 0.5F) {
            SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS,
                    Color.makeARGB((int) Math.min(160, overlay), 255, 255, 255));
        }
        drawIcon(canvas, ICON_SEND_PATH, bx, by, textPrimary());
    }

    /**
     * Draws one of the inline SVG-path toolbar icons, centered in its button
     * and scaled to fit an {@code ICON_SIZE} box. Stroke width is divided by the
     * path scale so the rendered line stays a constant UI thickness no matter
     * how the icon's own path bounds differ.
     */
    private void drawIcon(Canvas canvas, io.github.humbleui.skija.Path icon, float bx, float by, int color) {
        drawIconCentered(canvas, icon, bx + UiTokens.BUTTON_W / 2.0F, by + UiTokens.BUTTON_H / 2.0F,
                s(18), color);
    }

    /** Draws an icon centered on an arbitrary point; used by toolbar and menus. */
    private void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon, float cx, float cy,
                                  float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(s(1.5F) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Draws the hidden EditBox's selection as Skia highlight blocks over the
     * wrapped input lines. AtomChat renders its own text, so the vanilla field's
     * selection highlight would otherwise be invisible.
     */
    private void drawInputSelection(Canvas canvas, Font font, List<String> lines, int from, int shown,
                                    float textX, float centerY, float lineH) {
        if (chatField == null || lines.isEmpty()) {
            return;
        }
        int len = inputGetText().length();
        int a = MathHelper.clamp(chatField.selectionStart, 0, len);
        int b = MathHelper.clamp(chatField.selectionEnd, 0, len);
        int selStart = Math.min(a, b);
        int selEnd = Math.max(a, b);
        if (selStart >= selEnd) {
            return;
        }

        for (int i = from; i < from + shown && i < lines.size(); i++) {
            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart += lines.get(j).length();
            }
            int lineLen = lines.get(i).length();
            int lineEnd = lineStart + lineLen;
            if (selEnd <= lineStart || selStart >= lineEnd) {
                continue;
            }
            int c0 = Math.max(0, Math.min(selStart - lineStart, lineLen));
            int c1 = Math.max(0, Math.min(selEnd - lineStart, lineLen));
            if (c0 >= c1) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c0));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c1));
            float cy = centerY + (i - from) * lineH;
            SkiaDraw.drawRoundedRect(canvas, x0, cy - lineH / 2.0F, Math.max(1.0F, x1 - x0), lineH, s(2), 0xE02D6FD6);
        }
    }

    /**
     * Circular avatar from the player's real skin face (face + hat layer sampled
     * from the 64x64 skin). The face image is an opaque square; the circle is
     * produced by drawRoundedImage's clip only, so there is exactly one rounded
     * edge (no CPU mask + clip double edge, and no placeholder bleeding through
     * the avatar). Falls back to a flat gray circle while the skin is missing.
     */
    private void drawAvatar(Canvas canvas, ChatMessage msg, float avatarX, float avatarY) {
        UUID uuid = msg.isOwn() ? (this.client.player != null ? this.client.player.getUuid() : null) : msg.getSenderUuid();
        String name = msg.isOwn() ? ownName() : msg.getProfileName();
        if (name == null || name.isBlank()) {
            name = messageSenderName(msg);
        }
        Image face = AvatarRenderer.face(SkinResolver.getSkin(uuid, name));
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
    }

    private boolean buttonPressed(int id) {
        return pressedButton == id && System.currentTimeMillis() - pressTime < 150L;
    }

    private void pressButton(int id) {
        pressedButton = id;
        pressTime = System.currentTimeMillis();
    }

    /**
     * Rounded scrollbar: fades in only while the pointer is near the track or
     * while dragged, and fades straight back out otherwise.
     * Thumb maps scrollY to the track; drag maps 1:1 with clamping.
     */
    private void drawScrollbar(Canvas canvas, UiLayout layout, float vmx, float vmy) {
        long now = System.currentTimeMillis();
        long dt = Math.min(50L, now - lastScrollbarFrame);
        lastScrollbarFrame = now;

        UiLayout.Rect list = layout.list;
        float trackW = s(6);
        float trackX = list.right() - trackW - s(2);
        float trackH = list.h();
        float visibleRatio = Math.min(1.0F, trackH / (trackH + maxScroll));
        float thumbH = Math.max(s(30), trackH * visibleRatio);
        float thumbY = list.y() + (trackH - thumbH) * (scrollY / maxScroll);

        // Fade in only when the pointer is near the scrollbar itself (or dragging it).
        boolean nearTrack = vmx >= trackX - s(12) && vmx <= trackX + trackW + s(12)
                && vmy >= list.y() - s(12) && vmy <= list.bottom() + s(12);
        boolean active = maxScroll > 0.0F && (draggingScrollbar || nearTrack);
        float target = active ? 1.0F : 0.0F;
        scrollBarAlpha = UiMotion.approach(scrollBarAlpha, target, dt, UiMotion.SCROLLBAR_FADE_MS);
        if (scrollBarAlpha <= 0.0F) {
            return;
        }

        if (draggingScrollbar) {
            float travel = trackH - thumbH;
            float delta = (vmy - dragStartY) * (travel > 0.0F ? maxScroll / travel : 0.0F);
            scrollY = Math.max(0.0F, Math.min(dragStartScroll + delta, maxScroll));
            scrollTarget = scrollY;
            scrollToBottom = false;
            scrollAnimActive = false;
        }

        boolean hover = !draggingScrollbar
                && vmx >= trackX - s(8) && vmx <= trackX + trackW + s(8)
                && vmy >= list.y() && vmy <= list.bottom();
        // Two separate states: hovering only thickens the thumb (so it reads as
        // grabbable), while the accent colour is reserved for a held left button.
        scrollEmphasis = UiMotion.approach(scrollEmphasis, (hover || draggingScrollbar) ? 1.0F : 0.0F, frameDt, UiMotion.SCROLLBAR_EMPHASIS_MS);
        scrollActive = UiMotion.approach(scrollActive, draggingScrollbar ? 1.0F : 0.0F, frameDt, UiMotion.SCROLLBAR_EMPHASIS_MS);
        float w = trackW + scrollEmphasis * s(3);
        int ar = (accent() >> 16) & 0xFF;
        int ag = (accent() >> 8) & 0xFF;
        int ab = accent() & 0xFF;
        int r = (int) (255 + (ar - 255) * scrollActive);
        int g = (int) (255 + (ag - 255) * scrollActive);
        int bch = (int) (255 + (ab - 255) * scrollActive);
        int alpha = MathHelper.clamp((int) ((170 + 60 * scrollEmphasis) * scrollBarAlpha), 0, 255);
        int color = (alpha << 24) | (r << 16) | (g << 8) | bch;
        SkiaDraw.drawRoundedRect(canvas, trackX - (w - trackW) / 2.0F, thumbY, w, thumbH, w / 2.0F, color);
    }

    private boolean overScrollbarTrack(UiLayout layout, float vmx, float vmy) {
        float trackW = s(6);
        float trackX = layout.list.right() - trackW - s(2);
        return maxScroll > 0.0F
                && vmx >= trackX - s(8) && vmx <= trackX + trackW + s(8)
                && vmy >= layout.list.y() && vmy <= layout.list.bottom();
    }

    private void drawMessages(Canvas canvas, float x, float y, float width, float height) {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        hits.clear();
        clickableSpans.clear();
        // Snapshot "was at bottom" before maxScroll grows: after new messages
        // arrive the old target is no longer near the new max, so comparing after
        // recompute would make us miss the follow and leave a growing gap.
        boolean wasAtBottom = scrollToBottom || scrollTarget >= maxScroll - 3.0F;
        boolean viewportChanged = lastListHeight >= 0.0F && Math.abs(lastListHeight - height) > 0.01F;
        lastListHeight = height;
        recomputeMaxScroll(messages, width, y, height);
        if (wasAtBottom) {
            if (viewportChanged) {
                // The list is shrinking/growing in lockstep with the animated
                // input bar. Keep the bottom pinned directly: chasing the moving
                // maxScroll with an eased scroll restarts every frame and visibly
                // lags behind the bar, which is why growing felt desynced while
                // shrinking (a plain clamp) felt fine.
                scrollToBottom = false;
                scrollY = maxScroll;
                scrollTarget = maxScroll;
                scrollAnimActive = false;
            } else {
                scrollToBottom = true;
            }
        }
        updateScrollAnimation();
        canvas.save();
        try {
            SkiaDraw.clip(canvas, x, y, width, height, 0.0F);
            canvas.translate(0.0F, -scrollY);
            long now = System.currentTimeMillis();
            pruneEntranceSettled(now);
            float cursorY = y;
            for (ChatMessage msg : messages) {
                float h = messageHeight(msg, width);
                float offset = cursorY - y;
                if (offset > scrollY + height + 80.0F) {
                    break;
                }
                if (offset + h >= scrollY - 80.0F) {
                    float t = entranceProgress(msg, now);
                    boolean layered = t < 1.0F;
                    if (!layered) {
                        messageEnterSettled.add(msg);
                    }
                    canvas.save();
                    if (layered) {
                        // QQ-style entrance: own bubbles come in from the right
                        // (toward the left), other bubbles from the left. The
                        // layer rectangle must cover the full travel so a sliding
                        // bubble is never clipped by its own offscreen layer.
                        float travel = UiTokens.MESSAGE_SLIDE;
                        // Two curves, one timeline: the slide decelerates hard
                        // (cubic) while the fade ramps gently across the whole
                        // entrance (quad), so the opacity change is still
                        // happening while the bubble is still moving.
                        float fade = Easing.easeOutQuad(t);
                        float move = Easing.easeOutCubic(t);
                        // System capsules are centered and have no sender side;
                        // they fade in place rather than pretending to be someone's
                        // bubble.
                        float dx = msg.isSystem() ? 0.0F
                                : msg.isOwn() ? (1.0F - move) * travel
                                : -(1.0F - move) * travel;
                        try (Paint layer = new Paint()) {
                            layer.setColor(Color.makeARGB((int) (255.0F * fade), 0, 0, 0));
                            canvas.saveLayer(Rect.makeXYWH(x - travel - 4.0F, cursorY - 4.0F,
                                    width + travel * 2.0F + 8.0F, h + 28.0F), layer);
                            canvas.translate(dx, 0.0F);
                        }
                    }
                    int spanStart = clickableSpans.size();
                    MessageHit hit = drawMessage(canvas, msg, x, cursorY, width, hits.size());
                    // Clickable spans are recorded in content space (like hits
                    // before conversion); convert them to screen space so later
                    // hit-testing can compare them directly against the mouse.
                    for (int i = spanStart; i < clickableSpans.size(); i++) {
                        ClickableSpan s = clickableSpans.get(i);
                        clickableSpans.set(i, new ClickableSpan(s.x(), s.y() - scrollY, s.w(), s.h(), s.style()));
                    }
                    if (layered) {
                        canvas.restore();
                    }
                    canvas.restore();
                    // Hits are hit-tested in screen space; drawing happens in content space.
                    hits.add(new MessageHit(hit.message(), hit.index(), hit.x(), hit.y() - scrollY, hit.maxWidth(),
                            hit.bottom() - scrollY, hit.avatarX(), hit.avatarY() - scrollY, hit.avatarSize(),
                            hit.bubbleY() - scrollY, hit.bubbleX(), hit.bubbleWidth(), hit.bubbleBottom() - scrollY));
                } else {
                    // Left the viewport: drop the start timestamp only. The
                    // settled marker is deliberately kept so scrolling back up
                    // through history never replays an entrance; the set is
                    // bounded by pruneEntranceSettled's time guard.
                    messageEnterStart.remove(msg);
                }
                cursorY += h + UiTokens.LIST_GAP;
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Raw 0..1 progress of a message's entrance. Messages that existed before
     * this screen was opened are already settled; messages arriving while the
     * screen is open start their animation on the first frame they are actually
     * drawn inside the viewport.
     *
     * <p>Linear on purpose: the caller picks the curve. The fade and the slide
     * must not share one — easeOutCubic covers ~88% of its distance in the
     * first half of the duration, which feels right for a slide but spends the
     * opacity ramp in ~70ms, far too fast to read as a fade.
     */
    private float entranceProgress(ChatMessage msg, long now) {
        if (msg.getTimestamp() < openStart
                || messageEnterSettled.contains(msg)
                || now - msg.getTimestamp() > ENTRANCE_SETTLE_GUARD_MS) {
            return 1.0F;
        }
        Long start = messageEnterStart.get(msg);
        if (start == null) {
            start = now;
            messageEnterStart.put(msg, start);
        }
        return Math.min(1.0F, (now - start) / (float) MESSAGE_ANIM_MS);
    }

    /**
     * Bounded housekeeping for the never-replay guarantee: once a message is
     * older than the guard window it is settled by time alone, so its entry can
     * leave the set. Runs at most once a second.
     */
    private void pruneEntranceSettled(long now) {
        if (messageEnterSettled.isEmpty() || now - lastEntrancePrune < 1000L) {
            return;
        }
        lastEntrancePrune = now;
        long cutoff = now - ENTRANCE_SETTLE_GUARD_MS;
        messageEnterSettled.removeIf(m -> m.getTimestamp() < cutoff);
    }

    /**
     * Tuui WheelUtils-style scrolling: wheel only moves a target value and the
     * actual offset eases toward it. Bottom-stickiness (e33chat pattern) retargets
     * to maxScroll while the view is at the bottom.
     */
    private void updateScrollAnimation() {
        long now = System.currentTimeMillis();
        if (firstRender) {
            scrollY = maxScroll;
            scrollTarget = maxScroll;
            scrollToBottom = false;
            firstRender = false;
            return;
        }
        // Stickiness follows the target: a wheel-up retarget immediately releases it.
        boolean wasAtBottom = scrollTarget >= maxScroll - 3.0F;
        if (scrollToBottom || wasAtBottom) {
            scrollTarget = maxScroll;
            scrollToBottom = false;
            startScrollAnim(scrollTarget, SCROLL_ANIM_MS);
        }
        if (scrollAnimActive) {
            float t = Math.min(1.0F, (now - scrollAnimStart) / (float) scrollAnimMs);
            scrollY = scrollAnimFrom + (scrollAnimTo - scrollAnimFrom) * Easing.easeOutCubic(t);
            if (t >= 1.0F) {
                scrollY = scrollAnimTo;
                scrollAnimActive = false;
            }
        }
    }

    private void startScrollAnim(float to, long durationMs) {
        scrollTarget = to;
        if (Math.abs(scrollY - to) <= 0.5F) {
            scrollY = to;
            scrollAnimActive = false;
            return;
        }
        if (scrollAnimActive && scrollAnimTo == to) {
            return;
        }
        scrollAnimFrom = scrollY;
        scrollAnimTo = to;
        scrollAnimStart = System.currentTimeMillis();
        scrollAnimMs = durationMs;
        scrollAnimActive = true;
    }

    private MessageHit drawMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        if (msg.isSystem()) {
            return drawSystemMessage(canvas, msg, x, y, maxWidth, index);
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float bubbleMaxWidth = maxWidth - UiTokens.BUBBLE_RETRACT;
        String raw = msg.getRawText();
        String imageUrl = extractImageUrl(raw);
        if (imageUrl != null) {
            return drawImageMessage(canvas, msg, raw, imageUrl, x, y, maxWidth, index);
        }
        RichText content = msg.getContentRich();
        float textMaxWidth = bubbleMaxWidth - UiTokens.BUBBLE_PAD * 2.0F;
        List<RichLine> richLines = RichTextRenderer.wrapFor(content, font, textMaxWidth);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        // The bubble must hug the longest visible line. Using the single-line
        // width of the whole message collapsed multi-line messages (e.g. a hard
        // newline between two short lines) to a pill only as wide as the bubble
        // padding, because the old expression forced a 0 width whenever there
        // was more than one line.
        float maxLineWidth = 0.0F;
        for (RichLine line : richLines) {
            maxLineWidth = Math.max(maxLineWidth, RichTextRenderer.width(font, line));
        }
        float bubbleWidth;
        if (maxLineWidth + UiTokens.BUBBLE_PAD * 2.0F <= bubbleMaxWidth) {
            bubbleWidth = Math.max(UiTokens.BUBBLE_MIN_W, maxLineWidth + UiTokens.BUBBLE_PAD * 2.0F);
        } else {
            bubbleWidth = bubbleMaxWidth;
        }
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, lines.size() * lineHeight);

        // Layout formula: name band -> quote pill -> bubble; bubble hugs the avatar side.
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float bubbleTop = y + UiTokens.NAME_BAND + quoteH;
        float bubbleHeight = textHeight + UiTokens.BUBBLE_PAD_Y;
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        float bubbleX = msg.isOwn() ? x + maxWidth - bubbleWidth - nameOffset : x + nameOffset;

        // Name hugs the bubble's outer edge: right-aligned for own, left for others.
        drawMessageName(canvas, msg, y, bubbleX, bubbleX + bubbleWidth);

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);

        // Poke animation: shake avatar horizontally for ~600ms after double-click.
        if (pokeIndex == index && pokeStartTime > 0) {
            long elapsed = System.currentTimeMillis() - pokeStartTime;
            if (elapsed < 600) {
                float offset = (float) Math.sin(elapsed / 40.0) * 6.0F * (1.0F - elapsed / 600.0F);
                avatarX += offset;
            } else {
                pokeIndex = -1;
            }
        }
        drawAvatar(canvas, msg, avatarX, avatarY);

        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.BUBBLE_RADIUS, msg.isOwn() ? ownBubble() : otherBubble());
        drawMessageSelection(canvas, msg, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        RichTextRenderer.drawLines(canvas, font, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F,
                lineHeight, textPrimary(), clickableSpans, true);

        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * System lines (death, command feedback, join...) render as a compact
     * centered gray capsule: no avatar, no name, smaller text.
     */
    private MessageHit drawSystemMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        List<RichLine> richLines = RichTextRenderer.wrapFor(msg.getContentRich(), font,
                maxWidth - UiTokens.BUBBLE_PAD * 2.0F);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, lines.size() * lineHeight);
        float bubbleHeight = textHeight + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        float lineMax = 0.0F;
        for (RichLine line : richLines) {
            lineMax = Math.max(lineMax, RichTextRenderer.width(font, line));
        }
        float bubbleWidth = Math.min(maxWidth, Math.max(s(40), lineMax + UiTokens.BUBBLE_PAD * 2.0F));
        float bubbleX = x + (maxWidth - bubbleWidth) / 2.0F;
        float bubbleTop = y + s(2);
        // System capsules keep the translucent alpha but borrow the placeholder
        // bubble's RGB (otherBubbleColor), so they are not darker than the
        // "image loading" capsule users already see as the chat's grey tone.
        int placeholderRgb = otherBubble();
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, s(10),
                Color.makeARGB(150,
                        (placeholderRgb >> 16) & 0xFF,
                        (placeholderRgb >> 8) & 0xFF,
                        placeholderRgb & 0xFF));
        drawMessageSelection(canvas, msg, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        RichTextRenderer.drawLines(canvas, font, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F,
                lineHeight, textSecondary(), clickableSpans, true);
        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, 0.0F, 0.0F, 0.0F, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * e33chat-style quote capsule: anchored to the avatar edge (right side for
     * own messages, left for others) with a full-row width budget, truncated
     * with an ellipsis only when exceeding that budget.
     */
    private void drawQuotePill(Canvas canvas, ChatMessage msg, float x, float maxWidth, float pillY, boolean own) {
        Font quoteFont = FontManager.font(UiTokens.FONT_QUOTE);
        float capW = maxWidth - UiTokens.AVATAR_SIZE - s(18);
        float barW = s(3);
        float textMaxW = capW - UiTokens.QUOTE_PAD_X * 2.0F - barW - s(4);
        String name = msg.getQuoteName().startsWith("@") ? msg.getQuoteName() : "@" + msg.getQuoteName();
        String quote = name + ": " + msg.getQuoteText();
        String display = truncateToWidth(quoteFont, quote, textMaxW);
        float pillW = Math.min(capW, SkiaFontRenderer.getStringWidth(quoteFont, display) + UiTokens.QUOTE_PAD_X * 2.0F + barW + s(4));
        // Align the quote's outer edge with the bubble's outer edge, not with
        // the avatar. The bubble uses AVATAR_GAP as the horizontal gap to the
        // avatar, so the quote must use the same token.
        float pillX = own ? x + maxWidth - UiTokens.AVATAR_SIZE - UiTokens.AVATAR_GAP - pillW
                : x + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        // Quote pill shares the same light gray-white fill as the header/input
        // cards (translucent white over the panel), so it reads as one family.
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, UiTokens.QUOTE_HEIGHT, s(6), Color.makeARGB(60, 255, 255, 255));
        SkiaDraw.drawRoundedRect(canvas, pillX + UiTokens.QUOTE_PAD_X, pillY + s(3), barW, UiTokens.QUOTE_HEIGHT - s(6), barW / 2.0F, accent());
        SkiaFontRenderer.drawText(canvas, quoteFont, display, pillX + UiTokens.QUOTE_PAD_X + barW + s(4),
                SkiaFontRenderer.centerBaselineY(quoteFont, pillY + UiTokens.QUOTE_HEIGHT / 2.0F), textPrimary());
    }

    private static String truncateToWidth(Font font, String text, float maxW) {
        if (SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && SkiaFontRenderer.getStringWidth(font, t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    /**
     * Draws a message's name hugging its bubble's outer edge. One helper for
     * text and image bubbles so their spacing can never drift apart: the image
     * path centred the name in the band while the text path drew it from the raw
     * baseline, which put the two a cap-height apart.
     */
    private void drawMessageName(Canvas canvas, ChatMessage msg, float rowY, float leftX, float rightX) {
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        RichText sender = msg.getSenderRich();
        if (sender.isEmpty()) {
            sender = RichText.literal(messageSenderName(msg));
        }
        List<RichLine> lines = RichTextRenderer.wrapFor(sender, nameFont, Float.MAX_VALUE);
        if (lines.isEmpty()) {
            return;
        }
        float lineHeight = SkiaFontRenderer.getHeight(nameFont);
        float nameWidth = 0.0F;
        for (RichLine line : lines) {
            nameWidth = Math.max(nameWidth, RichTextRenderer.width(nameFont, line));
        }
        float x = msg.isOwn() ? rightX - nameWidth : leftX;
        // RichTextRenderer.drawLines takes a centerY and internally converts
        // it to the cap-height baseline, matching the old drawText helper.
        float centerY = rowY + UiTokens.NAME_BAND / 2.0F;
        RichTextRenderer.drawLines(canvas, nameFont, lines, x, centerY, lineHeight, textPrimary(),
                clickableSpans, true);
    }

    private MessageHit drawImageMessage(Canvas canvas, ChatMessage msg, String raw, String imageUrl, float x, float y, float maxWidth, int index) {
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float bubbleTop = y + UiTokens.NAME_BAND + quoteH;
        float[] size = imageBubbleSize(parseImageMeta(raw), maxWidth);
        float imageW = size[0];
        float imageH = size[1];
        float bubbleX = msg.isOwn() ? x + maxWidth - imageW - nameOffset : x + nameOffset;

        // Name hugs the bubble's outer edge, exactly like a text bubble.
        // Anchoring it to the row instead (the old behaviour) left the name
        // drifting away from the bubble as soon as the bubble width changed —
        // image bubbles are always wider than a short text bubble.
        drawMessageName(canvas, msg, y, bubbleX, bubbleX + imageW);

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);
        drawAvatar(canvas, msg, avatarX, avatarY);
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS, otherBubble());

        Image image = ImageLoader.get().get(imageUrl);
        if (image != null) {
            // No aspect fix-up here: the CICode carries the intrinsic size, so
            // the bubble already has the image's proportions and the bitmap is
            // simply fitted to it. Stretching only happened because the box used
            // to be a fixed 275x175 with the height clamped rather than scaled.
            SkiaDraw.drawRoundedImage(canvas, image, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS);
        } else {
            Font loadingFont = FontManager.font(UiTokens.FONT_QUOTE);
            SkiaFontRenderer.drawTextCentered(canvas, loadingFont, tr("atomchat.image.loading"),
                    bubbleX + imageW / 2.0F, bubbleTop + imageH / 2.0F, textSecondary());
        }

        float bottom = bubbleTop + imageH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleTop, bubbleX, imageW, bottom);
    }

    private void recomputeMaxScroll(List<ChatMessage> messages, float width, float top, float height) {
        float contentHeight = 0;
        for (ChatMessage msg : messages) {
            contentHeight += messageHeight(msg, width) + UiTokens.LIST_GAP;
        }
        maxScroll = Math.max(0, contentHeight - height);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
    }

    /**
     * Must match what drawMessage/drawImageMessage actually lay out:
     * name band + (quote pill + gap) + bubble; image bubbles are s(140) tall.
     */
    private float messageHeight(ChatMessage msg, float maxWidth) {
        if (msg.isSystem()) {
            Font font = FontManager.font(UiTokens.FONT_QUOTE);
            float lineHeight = SkiaFontRenderer.getHeight(font);
            int lines = RichTextRenderer.wrapFor(msg.getContentRich(), font,
                    maxWidth - UiTokens.BUBBLE_PAD * 2.0F).size();
            return s(2) + Math.max(lineHeight, lines * lineHeight) + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        }
        float quoteH = msg.getQuoteName() != null ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        ImageMeta imageMeta = parseImageMeta(msg.getRawText());
        if (imageMeta != null) {
            return UiTokens.NAME_BAND + quoteH + imageBubbleSize(imageMeta, maxWidth)[1];
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float wrapW = Math.max(s(20), maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.BUBBLE_PAD * 2.0F);
        int lines = RichTextRenderer.wrapFor(msg.getContentRich(), font, wrapW).size();
        return UiTokens.NAME_BAND + quoteH + UiTokens.BUBBLE_PAD_Y + Math.max(lineHeight, lines * lineHeight);
    }

    private static float emojiPanelW() {
        return UiTokens.EMOJI_COLS * UiTokens.EMOJI_CELL + UiTokens.EMOJI_PANEL_PAD * 2.0F;
    }

    private static float emojiContentH() {
        return UiTokens.EMOJI_VISIBLE_ROWS * UiTokens.EMOJI_CELL;
    }

    private static float emojiPanelH() {
        return UiTokens.EMOJI_TAB_H + emojiContentH() + UiTokens.EMOJI_PANEL_PAD;
    }

    private static final String[] EMOJI_TAB_KEYS = {
            "atomchat.emoji.tab.emoji",
            "atomchat.emoji.tab.kaomoji",
            "atomchat.emoji.tab.emote"
    };

    private static String[] emojiTabLabels() {
        String[] labels = new String[EMOJI_TAB_KEYS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = tr(EMOJI_TAB_KEYS[i]);
        }
        return labels;
    }

    private static final String[] EMOJIS = {
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
            "😋", "😛", "😜", "🤪", "😎", "🤗", "🤔", "😐",
            "😢", "😭", "😤", "😡", "🥺", "😴", "😷", "🤒",
            "🐱", "🐶", "🐼", "🐨", "🐰", "🦊", "🐸", "🐵",
            "🐭", "🐹", "🐮", "🦁", "🐯", "🐻", "🐧", "🐤",
            "🐴", "🦄", "🐝", "🐞", "🦋", "🐙", "🦀", "🐠",
            "🐷", "🐖",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
            "💕", "💖", "💗", "💘", "💝", "💟", "❣️", "💌",
            "👍", "👎", "👏", "🙌", "💪", "🤝", "👋", "✌️",
            "🎮", "🎯", "🎨", "🎵", "🎶", "🎤", "🎧", "🎼",
            "⭐", "🌟", "🔥", "💧", "🌈", "❄️", "🎉", "🎊",
            "🍕", "🍔", "🌮", "🍩", "🍪", "🎂", "☕", "🍺",
            "⬆️", "⬇️", "✅", "❌", "❓", "❗", "💤", "💡",
            "💀", "🗿", "🤡", "👀", "💯", "💢", "💬", "💭",
    };

    private static final String[] KAOMOJI = {
            "(｡•̀ᴗ-)✧", "(๑˃̵ᴗ˂̵)و", "(๑•̀ㅂ•́)و✧", "(◍•ᴗ•◍)",
            "╰(*°▽°*)╯", "(≧∇≦)ﾉ", "(＾▽＾)", "✧٩(ˊωˋ*)و✧",
            "ฅ^•ﻌ•^ฅ", "(•ω•)", "(￣▽￣*)", "(⌒▽⌒)☆",
            "(o゜▽゜)o☆", "＼(￣▽￣)／", "(◔◡◔)", "／(=✪ x ✪=)＼",
            "¯\\_(ツ)_/¯", "(ー_ー゛)", "(￢_￢)", "(¬_¬)",
            "(⇀‸↼‶)", "(｡ŏ_ŏ)", "(・∀・)", "_(:з」∠)_",
            "(╯°□°）╯︵ ┻━┻", "(´;ω;｀)", "Σ(°△°|||)", "(◎ロ◎)",
            "(∪.∪ )...zzz",
    };

    private float emojiPanelX() {
        return panelX() + UiTokens.LIST_PAD_X;
    }

    /** Sits directly above the input bar, so it follows the bar's grown height. */
    private float emojiPanelY() {
        return layout().inputBar.y() - UiTokens.PANEL_TOP_GAP - emojiPanelH() - s(6);
    }

    private boolean overEmojiPanel(float mx, float my) {
        float px = emojiPanelX();
        float py = emojiPanelY();
        return mx >= px && mx <= px + emojiPanelW() && my >= py && my <= py + emojiPanelH();
    }

    private String[] emojiTabItems() {
        return emojiTab == 1 ? KAOMOJI : EMOJIS;
    }

    private int emojiMaxScroll() {
        if (emojiTab == 2) {
            // 10 emotes in six columns fill two rows and never exceed the fixed
            // content height, so the emote grid never scrolls.
            return 0;
        }
        String[] items = emojiTabItems();
        int cols = emojiTab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float itemH = emojiTab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int rows = (items.length + cols - 1) / cols;
        float totalH = rows * itemH;
        return Math.max(0, (int) Math.ceil(totalH - emojiContentH()));
    }

    private String emojiPanelClick(float mx, float my) {
        float px = emojiPanelX();
        float py = emojiPanelY();
        float pw = emojiPanelW();
        if (!overEmojiPanel(mx, my)) {
            return null;
        }
        // Tab bar. The strip is inset by EMOJI_PANEL_PAD so it aligns with the
        // content grid below; the active pill then keeps a uniform s(4) inside it.
        if (my < py + UiTokens.EMOJI_TAB_H) {
            String[] labels = emojiTabLabels();
            float tabInset = UiTokens.EMOJI_PANEL_PAD;
            float tabStripX = px + tabInset;
            float tabStripW = pw - tabInset * 2.0F;
            float tabW = tabStripW / labels.length;
            int t = (int) ((mx - tabStripX) / tabW);
            if (t >= 0 && t < labels.length && t != emojiTab) {
                int from = emojiTab;
                emojiTab = t;
                emojiScroll = 0;
                cellHover.clear();
                if (t == 2) {
                    // Rescan so files dropped into the emote dir by hand show up.
                    emoteStore.refresh();
                }
                startTabTransition(from, t);
            }
            return "";
        }
        // The emote (sticker) tab has its own grid — images, an add slot and a
        // per-cell remove button. It does its work inline and never inserts text.
        if (emojiTab == 2) {
            return emotePanelClick(mx, my);
        }
        // Content grid.
        String[] items = emojiTabItems();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float itemH = emojiTab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int cols = emojiTab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float contentH = emojiContentH();
        // The padding strips and the strip below the last visible row are dead
        // space. Unclamped maths used to wrap them around: col -1 landed on the
        // previous row's last emoji and col == cols on the next row's first, so
        // a click in the gutter silently inserted a different emoji.
        if (mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return "";
        }
        int col = (int) ((mx - contentX) / (contentW / cols));
        int row = (int) ((my - contentY + emojiScroll) / itemH);
        col = Math.max(0, Math.min(cols - 1, col));
        int idx = row * cols + col;
        if (idx >= 0 && idx < items.length) {
            return items[idx];
        }
        return "";
    }

    private void drawEmojiPanel(Canvas canvas, float vmx, float vmy) {
        if (emojiAnim < 0.01F) {
            return;
        }
        float panelX = emojiPanelX();
        float panelY = emojiPanelY();
        float panelW = emojiPanelW();
        float panelH = emojiPanelH();
        emojiScroll = Math.max(0, Math.min(emojiScroll, emojiMaxScroll()));
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * emojiAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(panelX - s(24), panelY - s(24), panelW + s(48), panelH + s(48)), layer);
            float sc = 0.92F + 0.08F * emojiAnim;
            float cx = panelX + panelW / 2.0F;
            float cy = panelY + panelH / 2.0F;
            canvas.translate(cx, cy);
            canvas.scale(sc, sc);
            canvas.translate(-cx, -cy);
            canvas.translate(0.0F, (1.0F - emojiAnim) * s(10));
            SkiaDraw.drawRoundedRect(canvas, panelX, panelY, panelW, panelH, s(14), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, panelX, panelY, panelW, panelH, s(14), s(8), Color.makeARGB(100, 0, 0, 0));

            // Tabs: the active pill slides between slots when the tab changes.
            tabIndicatorAnim.update(frameDt);
            tabContentAnim.update(frameDt);
            if (tabContentAnim.isDone()) {
                tabAnimFrom = -1;
            }
            Font tabFont = FontManager.font(UiTokens.FONT_BUTTON);
            String[] labels = emojiTabLabels();
            float tabInset = UiTokens.EMOJI_PANEL_PAD;
            float tabStripX = panelX + tabInset;
            float tabStripW = panelW - tabInset * 2.0F;
            float tabW = tabStripW / labels.length;
            float indicator = tabIndicatorAnim.getValue();
            // The active pill keeps a uniform s(4) inset on every side of its tab
            // slot, and the whole strip is inset so it never crowds the panel's
            // rounded border (Apple-style calculated spacing).
            // The active pill leaves s(6) above and only s(2) below: the extra
            // bottom length makes the label's visual centre line up with the
            // pill's centre (the text baseline is drawn slightly low).
            SkiaDraw.drawRoundedRect(canvas, tabStripX + indicator * tabW + s(4), panelY + s(6),
                    tabW - s(8), UiTokens.EMOJI_TAB_H - s(8), s(8), Color.makeARGB(90, 255, 255, 255));
            for (int t = 0; t < labels.length; t++) {
                float tx = tabStripX + t * tabW;
                SkiaFontRenderer.drawTextCentered(canvas, tabFont, labels[t],
                        tx + tabW / 2.0F, panelY + UiTokens.EMOJI_TAB_H / 2.0F + s(2), textPrimary());
            }

            // Content area (clipped, scrollable). Switching tabs plays an opaque
            // push, like moving from one screen to the next: the outgoing tab is
            // pushed out as the incoming one slides in from the same direction,
            // both fully opaque and covering the full content width. A short
            // faded slide reads as a jitter, not a screen change.
            float contentX = panelX + UiTokens.EMOJI_PANEL_PAD;
            float contentY = panelY + UiTokens.EMOJI_TAB_H + s(2);
            float contentW = panelW - UiTokens.EMOJI_PANEL_PAD * 2.0F;
            float contentH = emojiContentH();
            updateGridHover(vmx, vmy);
            canvas.save();
            SkiaDraw.clip(canvas, contentX, contentY, contentW, contentH, 0.0F);
            boolean transitioning = tabAnimFrom >= 0 && tabAnimFrom != emojiTab && !tabContentAnim.isDone();
            float tp = transitioning ? tabContentAnim.getValue() : 1.0F;
            if (transitioning) {
                float travel = contentW;
                float inSign = tabAnimTo > tabAnimFrom ? 1.0F : -1.0F;
                drawEmojiTabContent(canvas, tabAnimFrom, contentX, contentY, contentW, contentH,
                        -inSign * travel * tp, 1.0F, false);
                drawEmojiTabContent(canvas, emojiTab, contentX, contentY, contentW, contentH,
                        inSign * travel * (1.0F - tp), 1.0F, true);
            } else {
                drawEmojiTabContent(canvas, emojiTab, contentX, contentY, contentW, contentH,
                        0.0F, 1.0F, true);
            }
            canvas.restore();
            canvas.restore();
        }
        canvas.restore();
    }

    private void startTabTransition(int from, int to) {
        tabAnimFrom = from;
        tabAnimTo = to;
        tabContentAnim.setValue(0.0F);
        tabContentAnim.animateTo(UiMotion.TAB_MS, 1.0F);
        tabIndicatorAnim.animateTo(UiMotion.TAB_MS, to);
    }

    private int gridHoverKey(int tab, int index) {
        return tab * 1000 + index;
    }

    /**
     * Hovered cell index for the active text tab (emoji/kaomoji), or -1 when the
     * pointer is over the tab bar, a gutter or outside the content area. Matches
     * emojiPanelClick's geometry so highlight and hit-test never drift.
     */
    private int textGridHoveredIndex(int tab, float mx, float my) {
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        float px = emojiPanelX();
        float py = emojiPanelY();
        float pw = emojiPanelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = emojiContentH();
        if (my < py + UiTokens.EMOJI_TAB_H
                || mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return -1;
        }
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int col = (int) ((mx - contentX) / (contentW / cols));
        int row = (int) ((my - contentY + emojiScroll) / itemH);
        if (col < 0 || col >= cols) {
            return -1;
        }
        int idx = row * cols + col;
        return idx >= 0 && idx < items.length ? idx : -1;
    }

    /**
     * Hovered cell index for the emote tab (0..count, count = the "+" add slot),
     * or -1 outside the content area / tab bar / gutters.
     */
    private int emoteGridHoveredIndex(float mx, float my) {
        float px = emojiPanelX();
        float py = emojiPanelY();
        float pw = emojiPanelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = emojiContentH();
        if (my < py + UiTokens.EMOJI_TAB_H
                || mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return -1;
        }
        float colW = contentW / UiTokens.EMOTE_COLS;
        int col = (int) ((mx - contentX) / colW);
        int row = (int) ((my - contentY) / UiTokens.EMOTE_CELL);
        if (col < 0 || col >= UiTokens.EMOTE_COLS) {
            return -1;
        }
        int idx = row * UiTokens.EMOTE_COLS + col;
        int total = emoteStore.count() + 1;
        return idx >= 0 && idx < total ? idx : -1;
    }

    /**
     * Fades every tracked cell highlight toward its target — the pointer can only
     * hover one cell, but every other cell must still decay when the mouse moves
     * away. Called once per frame before the content layers are drawn, so both the
     * outgoing and incoming tab layers read the same per-cell state.
     */
    private void updateGridHover(float vmx, float vmy) {
        int hovered = -1;
        if (emojiOpen && overEmojiPanel(vmx, vmy) && vmy >= emojiPanelY() + UiTokens.EMOJI_TAB_H) {
            hovered = emojiTab == 2 ? emoteGridHoveredIndex(vmx, vmy) : textGridHoveredIndex(emojiTab, vmx, vmy);
        }
        int hoveredKey = hovered < 0 ? -1 : gridHoverKey(emojiTab, hovered);
        Iterator<Map.Entry<Integer, Float>> it = cellHover.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> e = it.next();
            boolean isHovered = e.getKey() == hoveredKey;
            float next = UiMotion.approach(e.getValue(), isHovered ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
            if (!isHovered && next <= 0.001F) {
                it.remove();
            } else {
                e.setValue(next);
            }
        }
        if (hoveredKey >= 0 && !cellHover.containsKey(hoveredKey)) {
            cellHover.put(hoveredKey, 0.0F);
        }
    }

    /**
     * Draws one tab's content layer, optionally slid by dx and faded by alpha
     * during a tab switch. The old layer is non-interactive (no hover highlights)
     * because the pointer already targets the new tab.
     */
    private void drawEmojiTabContent(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                                     float dx, float alpha, boolean interactive) {
        if (alpha <= 0.005F) {
            return;
        }
        boolean layered = alpha < 0.995F;
        canvas.save();
        canvas.translate(dx, 0.0F);
        if (layered) {
            try (Paint layer = new Paint()) {
                layer.setColor(Color.makeARGB((int) (255.0F * alpha), 0, 0, 0));
                // Cover the slide travel as well, or content leaving the layer's
                // bounds would be cut out of the fade.
                canvas.saveLayer(Rect.makeXYWH(contentX - s(24), contentY - s(8),
                        contentW + s(48), contentH + s(16)), layer);
                drawTabGrid(canvas, tab, contentX, contentY, contentW, contentH, interactive);
            }
        } else {
            drawTabGrid(canvas, tab, contentX, contentY, contentW, contentH, interactive);
        }
        if (layered) {
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawTabGrid(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                             boolean interactive) {
        if (tab == 2) {
            drawEmoteGrid(canvas, contentX, contentY, contentW, contentH, interactive);
        } else {
            drawEmojiTextGrid(canvas, tab, contentX, contentY, contentW, contentH, interactive);
        }
    }

    /**
     * Emoji/kaomoji text grid with a per-cell hover highlight that fades in and
     * out (UiMotion.HOVER_MS), the same language as the buttons and emote cells.
     * The capsule leaves a uniform s(2) margin from the cell; emoji glyphs are
     * centred inside it while kaomoji rows keep a deliberate left padding so the
     * text never touches the capsule edge.
     */
    private void drawEmojiTextGrid(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                                   boolean interactive) {
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        Font itemFont = FontManager.font(tab == 1 ? UiTokens.FONT_KAOMOJI : UiTokens.FONT_EMOJI);
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float cellW = contentW / cols;
        for (int i = 0; i < items.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float ex = contentX + col * cellW;
            float ey = contentY - emojiScroll + row * itemH;
            if (ey + itemH < contentY || ey > contentY + contentH) {
                continue;
            }
            if (interactive) {
                float hov = cellHover.getOrDefault(gridHoverKey(tab, i), 0.0F);
                if (hov > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, ex + s(2), ey + s(2), cellW - s(4), itemH - s(4), s(6),
                            Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                }
            }
            if (tab == 1) {
                SkiaFontRenderer.drawText(canvas, itemFont, items[i], ex + s(8),
                        SkiaFontRenderer.centerBaselineY(itemFont, ey + itemH / 2.0F), textPrimary());
            } else {
                SkiaFontRenderer.drawTextCentered(canvas, itemFont, items[i],
                        ex + cellW / 2.0F, ey + itemH / 2.0F, textPrimary());
            }
        }
    }

    /**
     * Click handling for the emote (sticker) tab: tapping an emote sends it —
     * upload the local file, drop its CICode into the draft — and closes the
     * panel so the user cannot accidentally fire several uploads. The trailing
     * "+" slot opens the picker; the hovered × deletes. Always returns "" because
     * nothing here is inserted as plain text.
     */
    private String emotePanelClick(float mx, float my) {
        float px = emojiPanelX();
        float py = emojiPanelY();
        float pw = emojiPanelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = emojiContentH();
        float colW = contentW / UiTokens.EMOTE_COLS;
        if (mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return "";
        }
        int col = Math.max(0, Math.min(UiTokens.EMOTE_COLS - 1, (int) ((mx - contentX) / colW)));
        int row = (int) ((my - contentY) / UiTokens.EMOTE_CELL);
        int idx = row * UiTokens.EMOTE_COLS + col;
        List<File> emotes = emoteStore.list();
        if (idx < emotes.size()) {
            File emote = emotes.get(idx);
            float ex = contentX + col * colW;
            float ey = contentY + row * UiTokens.EMOTE_CELL;
            float rs = UiTokens.EMOTE_REMOVE_SIZE;
            // Remove button (top-right corner of the cell), hit before send.
            if (mx >= ex + colW - rs - s(2) && mx <= ex + colW - s(2)
                    && my >= ey + s(2) && my <= ey + s(2) + rs) {
                emoteStore.remove(emote);
                emoteImageCache.invalidate(emote);
                cellHover.clear();
                return "";
            }
            // Send: upload and close the panel (one sticker per tap).
            emojiOpen = false;
            uploadAndAppend(emote.toPath());
            return "";
        }
        // The trailing "+" add slot, disabled once the pack is full.
        if (idx == emotes.size() && !emoteStore.isFull()) {
            pickEmoteFile();
        }
        return "";
    }

    /**
     * Opens the FlatLaf image picker (same one as the image button) and copies
     * the chosen file into the emote store. The picker blocks its own worker
     * thread; the store mutation hops back to the render thread.
     */
    private void pickEmoteFile() {
        KeyBinding.unpressAll();
        if (this.client.mouse != null) {
            ((MouseHandlerAccessor) this.client.mouse).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(this::suppressAutoIconify, this::restoreAutoIconify,
                    EmoteStore::isSupportedName);
            refocusWindow();
            if (file == null) {
                return;
            }
            this.client.execute(() -> {
                if (emoteStore.add(file.toFile())) {
                    // Adding may have overwritten an existing file of the same
                    // name, so clear the decode cache rather than guess which
                    // entry went stale. Ten entries, so it is cheap.
                    emoteImageCache.clear();
                    cellHover.clear();
                }
            });
        }, "AtomChat-EmotePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Emote (sticker) grid: six columns of {@code s(44)} cells. Each emote is
     * fitted (never upscaled) into its cell; hovering highlights the cell and
     * shows the × remove button; the last cell is the "+" add slot, grayed out
     * when the pack is full.
     */
    private void drawEmoteGrid(Canvas canvas, float contentX, float contentY, float contentW, float contentH,
                               boolean interactive) {
        List<File> emotes = emoteStore.list();
        float cell = UiTokens.EMOTE_CELL;
        float colW = contentW / UiTokens.EMOTE_COLS;
        float pad = s(4);
        int total = emotes.size() + 1; // trailing "+" add slot
        for (int i = 0; i < total; i++) {
            int col = i % UiTokens.EMOTE_COLS;
            int row = i / UiTokens.EMOTE_COLS;
            float ex = contentX + col * colW;
            float ey = contentY + row * cell;
            if (ey + cell < contentY || ey > contentY + contentH) {
                continue;
            }
            float hov = interactive ? cellHover.getOrDefault(gridHoverKey(2, i), 0.0F) : 0.0F;
            if (i < emotes.size()) {
                File emote = emotes.get(i);
                // Image first, then the hover wash and remove button on top, so
                // the × can never be buried under the picture.
                Image img = emoteImageCache.image(emote);
                float avail = cell - pad * 2.0F;
                if (img != null) {
                    // Fit into the cell, never upscale, centred.
                    float scale = Math.min(1.0F, Math.min(avail / img.getWidth(), avail / img.getHeight()));
                    float dw = Math.max(1.0F, img.getWidth() * scale);
                    float dh = Math.max(1.0F, img.getHeight() * scale);
                    SkiaDraw.drawRoundedImage(canvas, img,
                            ex + (colW - dw) / 2.0F, ey + (cell - dh) / 2.0F, dw, dh, s(6));
                } else {
                    Font qFont = FontManager.font(UiTokens.FONT_QUOTE);
                    SkiaFontRenderer.drawTextCentered(canvas, qFont, "?", ex + colW / 2.0F, ey + cell / 2.0F,
                            textSecondary());
                }
                if (hov > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, ex + s(2), ey + s(2), colW - s(4), cell - s(4), s(8),
                            Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                    // Remove button.
                    float rs = UiTokens.EMOTE_REMOVE_SIZE;
                    SkiaDraw.drawRoundedRect(canvas, ex + colW - rs - s(2), ey + s(2), rs, rs, s(4),
                            Color.makeARGB((int) (200.0F * hov), 214, 48, 48));
                    Font xFont = FontManager.font(UiTokens.FONT_QUOTE);
                    SkiaFontRenderer.drawTextCentered(canvas, xFont, "×",
                            ex + colW - rs / 2.0F - s(2), ey + s(2) + rs / 2.0F,
                            Color.makeARGB((int) (255.0F * hov), 255, 255, 255));
                }
            } else {
                boolean disabled = emoteStore.isFull();
                if (hov > 0.01F && !disabled) {
                    SkiaDraw.drawRoundedRect(canvas, ex + s(2), ey + s(2), colW - s(4), cell - s(4), s(8),
                            Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                }
                Font addFont = FontManager.font(UiTokens.FONT_EMOJI);
                SkiaFontRenderer.drawTextCentered(canvas, addFont, "+", ex + colW / 2.0F, ey + cell / 2.0F,
                        disabled ? Color.makeARGB(90, 255, 255, 255) : textPrimary());
            }
        }
    }

    private void drawContextMenu(Canvas canvas, float vmx, float vmy) {
        ChatMessage shown = contextMessage != null ? contextMessage : lastContextMessage;
        if (shown == null) {
            contextAnim = 0.0F;
            return;
        }
        float target = contextMessage != null ? 1.0F : 0.0F;
        contextAnim = UiMotion.approach(contextAnim, target, frameDt, UiMotion.POPUP_MS);
        if (contextAnim < 0.01F) {
            if (target == 0.0F) {
                lastContextMessage = null;
                contextAnim = 0.0F;
            }
            return;
        }
        ContextMenuMode mode = contextMessage != null ? contextMenuMode : lastContextMenuMode;
        boolean avatarMenu = mode == ContextMenuMode.AVATAR;
        boolean imageMessage = !avatarMenu && extractImageUrl(shown.getRawText()) != null;
        int rows = avatarMenu ? 4 : (imageMessage ? 3 : 2);
        float rowH = UiTokens.MENU_H / 2.0F;
        float menuH = rowH * rows;
        float menuW = UiTokens.MENU_W;
        float menuX = Math.min(contextX, panelX() + panelWidth() - menuW - s(8));
        float menuY = Math.min(contextY, panelY() + panelHeight() - menuH - s(8));
        for (int row = 0; row < rows; row++) {
            float rowY = menuY + row * rowH;
            boolean overRow = vmx >= menuX && vmx <= menuX + menuW
                    && vmy >= rowY && vmy <= rowY + rowH;
            contextMenuHover[row] = UiMotion.approach(contextMenuHover[row], overRow ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        }
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * contextAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(menuX - s(20), menuY - s(20), menuW + s(40), menuH + s(40)), layer);
            float sc = 0.94F + 0.06F * contextAnim;
            canvas.translate(menuX + menuW / 2.0F, menuY);
            canvas.scale(sc, sc);
            canvas.translate(-(menuX + menuW / 2.0F), -menuY);
            SkiaDraw.drawRoundedRect(canvas, menuX, menuY, menuW, menuH, s(10), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, menuX, menuY, menuW, menuH, s(10), s(8), Color.makeARGB(100, 0, 0, 0));
            Font menuFont = FontManager.font(UiTokens.FONT_BUTTON);
            for (int row = 0; row < rows; row++) {
                float rowY = menuY + row * rowH;
                float hov = contextMenuHover[row];
                if (hov > 0.01F) {
                    // Uniform s(4) inset on every side of the row capsule so it
                    // never looks top-heavy against the menu edges.
                    SkiaDraw.drawRoundedRect(canvas, menuX + s(4), rowY + s(4), menuW - s(8), rowH - s(8),
                            s(6), Color.makeARGB((int) (55.0F * hov), 255, 255, 255));
                }
                String label = avatarMenu ? avatarContextLabel(row) : bubbleContextLabel(row, imageMessage);
                io.github.humbleui.skija.Path icon = avatarMenu ? avatarContextIcon(row) : bubbleContextIcon(row, imageMessage);
                drawIconCentered(canvas, icon, menuX + s(18), rowY + rowH / 2.0F, s(16), textPrimary());
                SkiaFontRenderer.drawText(canvas, menuFont, label, menuX + s(36),
                        SkiaFontRenderer.centerBaselineY(menuFont, rowY + rowH / 2.0F), textPrimary());
            }
            canvas.restore();
        }
        canvas.restore();
    }

    private static String bubbleContextLabel(int row, boolean imageMessage) {
        if (!imageMessage) {
            return switch (row) {
                case 0 -> tr("atomchat.context.copy");
                case 1 -> tr("atomchat.context.quote");
                default -> tr("atomchat.context.save");
            };
        }
        return switch (row) {
            case 0 -> tr("atomchat.context.copy");
            case 1 -> tr("atomchat.context.quote");
            case 2 -> tr("atomchat.context.save");
            default -> "";
        };
    }

    private static io.github.humbleui.skija.Path bubbleContextIcon(int row, boolean imageMessage) {
        if (!imageMessage) {
            return switch (row) {
                case 0 -> ICON_COPY_PATH;
                case 1 -> ICON_QUOTE_PATH;
                default -> ICON_SAVE_PATH;
            };
        }
        return switch (row) {
            case 0 -> ICON_COPY_PATH;
            case 1 -> ICON_QUOTE_PATH;
            case 2 -> ICON_SAVE_PATH;
            default -> ICON_SAVE_PATH;
        };
    }

    private static String avatarContextLabel(int row) {
        return switch (row) {
            case 0 -> tr("atomchat.context.mention");
            case 1 -> tr("atomchat.context.whisper");
            case 2 -> tr("atomchat.context.tp");
            case 3 -> tr("atomchat.context.block");
            default -> "";
        };
    }

    private static io.github.humbleui.skija.Path avatarContextIcon(int row) {
        return switch (row) {
            case 0 -> ICON_MENTION_PATH;
            case 1 -> ICON_WHISPER_PATH;
            case 2 -> ICON_TP_PATH;
            case 3 -> ICON_BLOCK_PATH;
            default -> ICON_MENTION_PATH;
        };
    }

    /**
     * Hands the open menu over to lastContextMessage so it can play the closing
     * animation instead of vanishing. Never recurse: it used to call itself,
     * which left contextMessage set forever and blew the stack on the caller.
     */
    private void closeContextMenu() {
        if (contextMessage != null) {
            lastContextMessage = contextMessage;
            lastContextMenuMode = contextMenuMode;
            contextMessage = null;
        }
    }

    /** Avatar menus only make sense for another player with a real sender identity. */
    private static boolean canOpenAvatarMenu(ChatMessage msg) {
        if (msg == null || msg.isSystem() || msg.isOwn()) {
            return false;
        }
        return msg.getSenderName() != null || msg.getProfileName() != null;
    }

    /**
     * Avatar context-menu actions. Row 0 (@ mention) is already wired because it
     * is the direct replacement for the old single-left-click behavior. Whisper /
     * Teleport / Block intentionally do nothing yet — the menu framework is the
     * current step, and each action is wired in its own feature pass.
     */
    private void performAvatarMenuAction(int row, ChatMessage message) {
        if (row == 0) {
            inputAppend("@" + messageSenderName(message) + " ");
        }
        // TODO(feature): row 1 whisper, row 2 teleport, row 3 block.
    }

    private void copyToClipboard(String text) {
        try {
            this.client.keyboard.setClipboard(text);
        } catch (Throwable t) {
            // Never let a clipboard failure abort the click handler: it used to
            // leave the menu stuck open with no clue why.
            AtomChat.LOGGER.warn("Failed to copy message to clipboard", t);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Root pages have no world-chat message list / emoji panel to scroll yet,
        // and must not let the hidden composer or suggestion layer see the wheel.
        if (!isWorldChatPage()) {
            return false;
        }
        // Suggestion popup scrolls first when open.
        if (chatInputSuggestor != null && chatInputSuggestor.mouseScrolled(verticalAmount)) {
            return true;
        }
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        if (emojiOpen && overEmojiPanel(mx, my)) {
            emojiScroll = Math.max(0, Math.min(
                    emojiScroll - (int) (verticalAmount * s(18)), emojiMaxScroll()));
            return true;
        }
        UiLayout.Rect list = layout().list;
        if (list.contains((float) mx, (float) my)) {
            scrollToBottom = false;
            scrollTarget = Math.max(0, Math.min(scrollTarget - (float) verticalAmount * 45.0F, maxScroll));
            startScrollAnim(scrollTarget, WHEEL_ANIM_MS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---------------------------------------------------------------- message text selection

    private void clearTextSelection() {
        selectionMessage = null;
        selectionAnchorLine = -1;
        selectionAnchorChar = -1;
        selectionFocusLine = -1;
        selectionFocusChar = -1;
        selecting = false;
        selectionMoved = false;
        selectionMessageLines = List.of();
    }

    private boolean hasTextSelection() {
        if (selectionMessage == null || selectionAnchorLine < 0 || selectionFocusLine < 0) {
            return false;
        }
        return selectionAnchorLine != selectionFocusLine || selectionAnchorChar != selectionFocusChar;
    }

    private List<MessageTextLine> textLinesForHit(MessageHit hit) {
        List<MessageTextLine> out = new ArrayList<>();
        ChatMessage msg = hit.message();
        if (extractImageUrl(msg.getRawText()) != null) {
            return out;
        }
        Font font = FontManager.font(msg.isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float textMax = Math.max(s(20), hit.bubbleWidth() - UiTokens.BUBBLE_PAD * 2.0F);
        List<RichLine> richLines = RichTextRenderer.wrapFor(msg.getContentRich(), font, textMax);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        if (lines.isEmpty()) {
            return out;
        }
        float lineH = SkiaFontRenderer.getHeight(font);
        float bubbleH = hit.bubbleBottom() - hit.bubbleY();
        float centerY = hit.bubbleY() + bubbleH / 2.0F;
        float blockTop = centerY - lines.size() * lineH / 2.0F;
        float textX = hit.bubbleX() + UiTokens.BUBBLE_PAD;
        for (int i = 0; i < lines.size(); i++) {
            out.add(new MessageTextLine(msg, i, lines.get(i), textX, blockTop + i * lineH, lineH));
        }
        return out;
    }

    private int charAtLine(MessageTextLine line, float mx) {
        String text = line.text();
        if (text.isEmpty()) {
            return 0;
        }
        Font font = FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float x = line.x();
        for (int i = 0; i < text.length(); i++) {
            float w = SkiaFontRenderer.getStringWidth(font, text.substring(i, i + 1));
            if (mx < x + w / 2.0F) {
                return i;
            }
            x += w;
        }
        return text.length();
    }

    /**
     * Draws the active selection highlight for one message before its text is
     * drawn, so the glyphs stay readable above the blue block.
     */
    private void drawMessageSelection(Canvas canvas, ChatMessage msg, List<String> lines, float textX,
                                      float centerY, float lineHeight, Font font) {
        if (selectionMessage != msg || !hasTextSelection()) {
            return;
        }
        int aLine = selectionAnchorLine;
        int aChar = selectionAnchorChar;
        int fLine = selectionFocusLine;
        int fChar = selectionFocusChar;
        if (aLine > fLine || (aLine == fLine && aChar > fChar)) {
            int tmpLine = aLine;
            aLine = fLine;
            fLine = tmpLine;
            int tmpChar = aChar;
            aChar = fChar;
            fChar = tmpChar;
        }
        float totalH = lines.size() * lineHeight;
        float blockTop = centerY - totalH / 2.0F;
        for (int i = 0; i < lines.size(); i++) {
            if (i < aLine || i > fLine) {
                continue;
            }
            int start;
            int end;
            if (aLine == fLine) {
                start = aChar;
                end = fChar;
            } else if (i == aLine) {
                start = aChar;
                end = lines.get(i).length();
            } else if (i == fLine) {
                start = 0;
                end = fChar;
            } else {
                start = 0;
                end = lines.get(i).length();
            }
            start = Math.max(0, Math.min(start, lines.get(i).length()));
            end = Math.max(0, Math.min(end, lines.get(i).length()));
            if (start >= end) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, start));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, end));
            float y = blockTop + i * lineHeight;
            SkiaDraw.drawRoundedRect(canvas, x0, y, Math.max(1.0F, x1 - x0), lineHeight, s(1), 0xE02D6FD6);
        }
    }

    private String copySelectedText() {
        if (!hasTextSelection()) {
            return "";
        }
        List<String> lines = selectionMessageLines;
        if (lines.isEmpty()) {
            return "";
        }
        int aLine = Math.max(0, Math.min(selectionAnchorLine, lines.size() - 1));
        int aChar = selectionAnchorChar;
        int fLine = Math.max(0, Math.min(selectionFocusLine, lines.size() - 1));
        int fChar = selectionFocusChar;
        if (aLine > fLine || (aLine == fLine && aChar > fChar)) {
            int tmpLine = aLine;
            aLine = fLine;
            fLine = tmpLine;
            int tmpChar = aChar;
            aChar = fChar;
            fChar = tmpChar;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = aLine; i <= fLine; i++) {
            String line = lines.get(i);
            int start = i == aLine ? aChar : 0;
            int end = i == fLine ? fChar : line.length();
            start = Math.max(0, Math.min(start, line.length()));
            end = Math.max(0, Math.min(end, line.length()));
            if (start < end) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line, start, end);
            }
        }
        return sb.toString();
    }

    private ClickableSpan findClickableSpan(float mx, float my) {
        for (ClickableSpan s : clickableSpans) {
            if (mx >= s.x() && mx <= s.x() + s.w() && my >= s.y() && my <= s.y() + s.h()) {
                return s;
            }
        }
        return null;
    }

    private Style findHoveredStyle(float mx, float my) {
        ClickableSpan span = findClickableSpan(mx, my);
        return span != null ? span.style() : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) {
            return true;
        }
        if (hasTextSelection() || selecting) {
            clearTextSelection();
        }
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);

        // Root pages have no world-chat composer/message interactions. Route
        // them before the hidden chat field/suggestion layer can see the click,
        // and consume root clicks so ChatScreen's composer never gets focus.
        if (!isWorldChatPage()) {
            if (button == 0 && handleBottomTabClick(mx, my)) {
                return true;
            }
            if (topPage() == AppPage.CHAT_LIST
                    && button == 0
                    && conversationListPage.mouseClicked(mx, my, rootLayout())) {
                return true;
            }
            return true;
        }

        float panelX = panelX();
        float panelY = panelY();
        UiLayout layout = layout();

        // Vanilla suggestion layer gets first pick on clicks too (prevents click-through).
        if (chatInputSuggestor != null && chatInputSuggestor.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }

        // Back to the conversation list before any composer/emoji/message hit.
        if (button == 0 && isBackButtonHit(mx, my)) {
            dismissSuggestor();
            popPage();
            return true;
        }

        // The emoji toggle button is tested before the panel's own "click outside
        // dismisses" rule, otherwise closing and reopening in the same click nets
        // back to open and the button can never toggle the panel off.
        if (button == 0 && layout.emojiBtn.contains((float) mx, (float) my)) {
            pressButton(1);
            inputFocused = true;
            emojiOpen = !emojiOpen;
            if (emojiOpen) {
                // Rescan on open: files dropped into the emote dir by hand appear.
                emoteStore.refresh();
                cellHover.clear();
            }
            return true;
        }

        // Emoji panel click: tabs first, then the currently visible grid.
        if (emojiOpen) {
            if (overEmojiPanel((float) mx, (float) my)) {
                String inserted = emojiPanelClick((float) mx, (float) my);
                if (inserted != null && !inserted.isEmpty()) {
                    inputAppend(inserted);
                }
                return true;
            }
            emojiOpen = false;
        }

        // Context menu click. Remember the target before dismissing so a
        // right-click on the same bubble/avatar toggles instead of reopening.
        ChatMessage menuBefore = contextMessage;
        ContextMenuMode menuBeforeMode = contextMenuMode;
        if (contextMessage != null) {
            float menuW = UiTokens.MENU_W;
            boolean avatarMenu = contextMenuMode == ContextMenuMode.AVATAR;
            boolean imageMessage = !avatarMenu && extractImageUrl(contextMessage.getRawText()) != null;
            int rows = avatarMenu ? 4 : (imageMessage ? 3 : 2);
            float rowH = UiTokens.MENU_H / 2.0F;
            float menuH = rowH * rows;
            float menuX = Math.min(contextX, panelX + panelWidth() - menuW - s(8));
            float menuY = Math.min(contextY, panelY + panelHeight() - menuH - s(8));
            boolean inside = (float) mx >= menuX && (float) mx <= menuX + menuW
                    && (float) my >= menuY && (float) my <= menuY + menuH;
            if (inside && button == 0) {
                int row = (int) ((my - menuY) / rowH);
                if (avatarMenu) {
                    performAvatarMenuAction(row, contextMessage);
                } else if (row == 0) {
                    copyToClipboard(contextMessage.getContentText());
                } else if (row == 1) {
                    replyTarget = contextMessage;
                    inputFocused = true;
                    setFocused(chatField);
                    chatField.setFocused(true);
                } else {
                    saveImage(contextMessage);
                }
                closeContextMenu();
                return true;
            }
            // Any other click — including a right-click aimed at another bubble —
            // dismisses first; the handlers below may then open a new menu.
            closeContextMenu();
        }

        // Button row: image / emoji / send share one row and one size,
        // geometry comes from UiLayout so hits can never drift from the drawing.
        if (button == 0 && layout.imageBtn.contains((float) mx, (float) my)) {
            pressButton(0);
            inputFocused = true;
            pickAndUploadImage();
            return true;
        }
        // Inserting an emoji must NOT close the panel: users often want to pick several
        // in a row. The panel still closes on any outside click or the toggle button.
        if (button == 0 && layout.sendBtn.contains((float) mx, (float) my)) {
            pressButton(2);
            sendMessage(inputGetText());
            return true;
        }

        // Input box focus
        inputFocused = false;
        dismissSuggestor();
        if (button == 0 && layout.inputBar.contains((float) mx, (float) my)) {
            inputFocused = true;
            setFocused(chatField);
            chatField.setFocused(true);
            return true;
        }
        setFocused(null);
        chatField.setFocused(false);

        // Scrollbar drag start
        if (button == 0 && overScrollbarTrack(layout, mx, my)) {
            draggingScrollbar = true;
            dragStartY = my;
            dragStartScroll = scrollY;
            return true;
        }

        // Arm a click candidate for every left press before message interactions.
        // This covers clickable sender names and any clickable span outside bubble
        // text; the text-line branch below may overwrite it with the same result.
        if (button == 0) {
            pendingClickSpan = findClickableSpan(mx, my);
            pendingClickMoved = false;
        }

        // Message interactions. Left avatar click only arms the double-click
        // poke; single-click @ is deliberately gone (use the right-click menu).
        // Right-click opens the bubble menu only when the pointer is actually on
        // the bubble; right-click on a real player's avatar opens the avatar menu.
        for (MessageHit hit : hits) {
            if (my < hit.y() || my > hit.bottom()) {
                continue;
            }
            if (button == 1 && hit.avatarSize() > 0F
                    && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                    && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()
                    && canOpenAvatarMenu(hit.message())) {
                // Right-clicking the avatar the avatar menu is already on closes it.
                if (menuBefore == hit.message() && menuBeforeMode == ContextMenuMode.AVATAR) {
                    return true;
                }
                contextMenuMode = ContextMenuMode.AVATAR;
                contextMessage = hit.message();
                contextX = mx;
                contextY = my;
                return true;
            }
            if (button == 1 && !hit.message().isSystem()
                    && mx >= hit.bubbleX() && mx <= hit.bubbleX() + hit.bubbleWidth()
                    && my >= hit.bubbleY() && my <= hit.bubbleBottom()) {
                // Right-clicking the bubble the menu is already on closes it.
                if (menuBefore == hit.message()) {
                    return true;
                }
                contextMenuMode = ContextMenuMode.BUBBLE;
                contextMessage = hit.message();
                contextX = mx;
                contextY = my;
                return true;
            }
            if (button == 0) {
                List<MessageTextLine> textLines = textLinesForHit(hit);
                for (MessageTextLine line : textLines) {
                    float lineRight = line.x() + SkiaFontRenderer.getStringWidth(
                            FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY),
                            line.text());
                    if (mx >= line.x() && mx <= lineRight && my >= line.y() && my <= line.y() + line.height()) {
                        // Remember any clickable run under the mouse before the
                        // selection anchor is armed. Dragging from this point will
                        // select text instead of firing the click; a clean click
                        // (no drag) will fire it on mouse release.
                        pendingClickSpan = findClickableSpan(mx, my);
                        pendingClickMoved = false;
                        selectionMessage = hit.message();
                        selectionMessageLines = textLines.stream().map(MessageTextLine::text).toList();
                        selectionAnchorLine = selectionFocusLine = line.line();
                        selectionAnchorChar = selectionFocusChar = charAtLine(line, mx);
                        selecting = true;
                        selectionMoved = false;
                        return true;
                    }
                }
            }
            if (button == 0 && hit.avatarSize() > 0F
                    && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                    && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()) {
                long now = System.currentTimeMillis();
                if (now - lastAvatarClickTime < 350 && lastAvatarClickIndex == hit.index()) {
                    pokeIndex = hit.index();
                    pokeStartTime = now;
                    lastAvatarClickTime = 0;
                } else {
                    lastAvatarClickTime = now;
                    lastAvatarClickIndex = hit.index();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Root pages have no world-chat selection/scrollbar drag state, and must
        // not forward drags to the hidden composer either.
        if (!isWorldChatPage()) {
            return false;
        }
        // Any drag while a click is pending must suppress the click-on-release,
        // including drags that do not start a text selection (e.g. name bars).
        if (button == 0 && pendingClickSpan != null) {
            pendingClickMoved = true;
        }
        if (selecting && button == 0 && selectionMessage != null) {
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            for (MessageHit hit : hits) {
                if (my < hit.y() || my > hit.bottom() || hit.message() != selectionMessage) {
                    continue;
                }
                for (MessageTextLine line : textLinesForHit(hit)) {
                    float lineRight = line.x() + SkiaFontRenderer.getStringWidth(
                            FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY),
                            line.text());
                    if (mx >= line.x() && mx <= lineRight && my >= line.y() && my <= line.y() + line.height()) {
                        int ch = charAtLine(line, mx);
                        if (ch != selectionFocusChar || line.line() != selectionFocusLine) {
                            selectionFocusLine = line.line();
                            selectionFocusChar = ch;
                            selectionMoved = true;
                            pendingClickMoved = true;
                        }
                        return true;
                    }
                }
            }
            // A drag that leaves the text is still a drag, so it must suppress
            // any click captured on mouse press even when no selection changed.
            pendingClickMoved = true;
            return true; // drag outside text keeps current selection active
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Root pages have no world-chat click-on-release/scrollbar handling, and
        // must not forward releases to the hidden composer either.
        if (!isWorldChatPage()) {
            return false;
        }
        if (button == 0) {
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            boolean wasSelecting = selecting;
            ClickableSpan pending = pendingClickSpan;
            pendingClickSpan = null;
            ClickableSpan released = findClickableSpan(mx, my);
            boolean shouldClick = pending != null && !pendingClickMoved
                    && pending.style().getClickEvent() != null
                    && pending.equals(released);
            if (wasSelecting) {
                selecting = false;
                if (!selectionMoved) {
                    clearTextSelection();
                }
            }
            pendingClickMoved = false;
            if (shouldClick) {
                this.handleTextClick(pending.style());
                return true;
            }
            if (wasSelecting) {
                return true;
            }
        }
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Esc: animated close
            dismissSuggestor();
            requestClose();
            return true;
        }
        if (closing) {
            return true;
        }
        // Root pages have no world-chat composer keyboard handling yet. Consume
        // keys here so they never reach the hidden ChatScreen chat field/history.
        if (!isWorldChatPage()) {
            return true;
        }
        // Copy selected message text before the vanilla field/suggestion layer
        // consumes Ctrl+C.
        if (keyCode == 67 && (modifiers & 2) != 0 && hasTextSelection()) {
            String copied = copySelectedText();
            if (!copied.isEmpty()) {
                this.client.keyboard.setClipboard(copied);
            }
            return true;
        }
        // Ctrl+V with a picture on the clipboard. MC's clipboard API only hands
        // out strings, so the vanilla field would paste nothing at all; that
        // flavour has to be intercepted before it gets that far.
        if (keyCode == GLFW_KEY_V && (modifiers & 2) != 0) {
            DataFlavor flavor = ClipboardImages.peek();
            if (flavor != null) {
                pasteFromClipboard(flavor);
                return true;
            }
        }
        // Vanilla suggestion layer gets first pick (Tab/arrows over the popup).
        if (chatInputSuggestor != null && chatInputSuggestor.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            if (inputFocused) {
                sendMessage(inputGetText());
            }
            return true;
        }
        // Up/Down become caret navigation as soon as the text wraps onto a second
        // line (>= INPUT_MAX_LINES). Once multiline, Up/Down never fall back to
        // vanilla chat history — that remains a single-line behaviour.
        if (inputFocused && chatField != null && (keyCode == 265 || keyCode == 264)) {
            List<String> lines = wrappedInput(layout().inputTextMaxWidth());
            if (lines.size() >= UiTokens.INPUT_MAX_LINES) {
                int caret = caretIndex();
                int row = caretLine(lines, caret);
                int target = (keyCode == 265) ? row - 1 : row + 1;
                if (target >= 0 && target < lines.size()) {
                    int rowStart = 0;
                    for (int i = 0; i < row; i++) {
                        rowStart += lines.get(i).length();
                    }
                    int targetStart = 0;
                    for (int i = 0; i < target; i++) {
                        targetStart += lines.get(i).length();
                    }
                    // Move straight up/down at the same visual column, clamped to
                    // the target line's length (standard text-editor behaviour).
                    int col = MathHelper.clamp(caret - rowStart, 0, lines.get(row).length());
                    int pos = targetStart + Math.min(col, lines.get(target).length());
                    chatField.setCursor(pos, false);
                }
                return true;
            }
        }
        if (inputFocused && AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info("keyPressed: {} (sc {}) mod {}", keyCode, scanCode, modifiers);
        }
        // Falls through to super (ChatScreen): the focused chatField consumes
        // backspace/ctrl+v/arrows/IME input; up/down drive vanilla chat history.
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (closing) {
            return true;
        }
        // Root pages do not own the composer, so typing must not reach the
        // hidden ChatScreen chat field.
        if (!isWorldChatPage()) {
            return true;
        }
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info("charTyped: '{}' (U+{}) focused={} field={}",
                    chr, Integer.toHexString(chr), inputFocused, chatField != null && chatField.isFocused());
        }
        return super.charTyped(chr, modifiers);
    }

    private void pickAndUploadImage() {
        // The native AWT dialog grabs OS input; release MC's held keys/button so
        // the UI does not think the image button is still pressed when it returns.
        KeyBinding.unpressAll();
        if (this.client.mouse != null) {
            ((MouseHandlerAccessor) this.client.mouse).atomchat$setActiveButton(0);
        }
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage(this::suppressAutoIconify, this::restoreAutoIconify);
            // The dialog owned OS focus while it was open; hand it back to the
            // game or the first click after picking lands on nothing.
            refocusWindow();
            if (file == null) {
                return;
            }
            uploadAndAppend(file);
        }, "AtomChat-ImagePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Saves another player's CICode image to a local file. The save dialog runs
     * on the same worker-thread pattern as the open picker; the actual download
     * happens off-thread via {@link ImageSaver} so the raw bytes stay identical.
     */
    private void saveImage(ChatMessage message) {
        String url = extractImageUrl(message.getRawText());
        if (url == null) {
            return;
        }
        Thread worker = new Thread(() -> {
            suppressAutoIconify();
            Path target;
            try {
                target = FilePicker.pickSavePath(fileNameFromUrl(url));
            } finally {
                restoreAutoIconify();
            }
            refocusWindow();
            if (target == null) {
                return;
            }
            ImageSaver.save(url, target).whenComplete((path, throwable) -> {
                if (throwable != null) {
                    this.client.execute(() -> showTransientHint(tr("atomchat.input.save_failed")));
                } else {
                    AtomChat.LOGGER.info("Saved chat image to {}", path);
                }
            });
        }, "AtomChat-ImageSavePicker");
        worker.setDaemon(true);
        worker.start();
    }

    /** URL last path segment, stripped of query/fragment; falls back to image.png. */
    private static String fileNameFromUrl(String url) {
        if (url == null) {
            return "image.png";
        }
        String clean = url;
        int cut = clean.indexOf('?');
        int hash = clean.indexOf('#');
        if (hash >= 0) {
            cut = cut >= 0 ? Math.min(cut, hash) : hash;
        }
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        int slash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        String name = slash >= 0 && slash + 1 < clean.length() ? clean.substring(slash + 1) : clean;
        return name.isEmpty() ? "image.png" : name;
    }

    private void showTransientHint(String text) {
        transientHint = text;
        transientHintSetAt = System.currentTimeMillis();
    }

    /**
     * Uploads an image and drops its CICode into the draft. Shared by the file
     * picker and by Ctrl+V pastes.
     */
    private void uploadAndAppend(Path file) {
        int[] size = ImageFiles.dimensions(file);
        imageUploading = true;
        imageUploader.upload(file, url -> {
            StringBuilder code = new StringBuilder("[[CICode,url=").append(url)
                    .append(",name=").append(file.getFileName());
            // Carry the intrinsic size so any client receiving this can lay the
            // bubble out at the right aspect ratio before the download lands —
            // that is what keeps the height from jumping when it arrives.
            if (size != null) {
                code.append(",w=").append(size[0]).append(",h=").append(size[1]);
            }
            code.append("]]");
            // The upload callback runs on the uploader's thread; the chat field
            // is only safe to touch from the render thread.
            this.client.execute(() -> {
                imageUploading = false;
                inputAppend(inputGetText().isEmpty() ? code.toString() : " " + code);
            });
        }, error -> {
            AtomChat.LOGGER.warn("Image upload failed: {}", error);
            this.client.execute(() -> imageUploading = false);
        });
    }

    /**
     * Ctrl+V with a picture on the clipboard. The payload is read off the render
     * thread — a screenshot is several megabytes and converting it is not free —
     * then uploaded and appended.
     */
    private void pasteFromClipboard(DataFlavor flavor) {
        Thread worker = new Thread(() -> {
            Path file = ClipboardImages.read(flavor);
            if (file == null) {
                AtomChat.LOGGER.warn("Clipboard held no usable image; Ctrl+V fell back to a text paste");
                return;
            }
            uploadAndAppend(file);
        }, "AtomChat-Paste");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * GLFW iconifies a fullscreen window as soon as it loses focus
     * (GLFW_AUTO_ICONIFY defaults to true and Minecraft leaves it there), so a
     * picker that takes focus would minimise the game every time. Suspension
     * lasts exactly as long as the picker is open.
     */
    private void suppressAutoIconify() {
        setAutoIconify(false);
    }

    private void restoreAutoIconify() {
        setAutoIconify(true);
    }

    private void setAutoIconify(boolean value) {
        runOnRender(() -> {
            try {
                GLFW.glfwSetWindowAttrib(this.client.getWindow().getHandle(),
                        GLFW.GLFW_AUTO_ICONIFY, value ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Failed to toggle GLFW_AUTO_ICONIFY for the image picker", t);
            }
        });
    }

    /** Runs a GLFW call on the render thread and waits for it to land. */
    private void runOnRender(Runnable task) {
        CountDownLatch done = new CountDownLatch(1);
        this.client.execute(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns OS focus to the game window after the native picker closes, and
     * clears any key/button state the dialog left behind. GLFW window calls are
     * not thread-safe, so the work hops onto the render thread.
     */
    private void refocusWindow() {
        this.client.execute(() -> {
            KeyBinding.unpressAll();
            if (this.client.mouse != null) {
                ((MouseHandlerAccessor) this.client.mouse).atomchat$setActiveButton(0);
            }
            try {
                GLFW.glfwFocusWindow(this.client.getWindow().getHandle());
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Failed to refocus the window after the image picker", t);
            }
        });
    }

    private void sendMessage(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isEmpty()) {
            return;
        }
        if (this.client.player != null) {
            String quoteName = null;
            String quoteText = null;
            if (replyTarget != null) {
                quoteName = messageSenderName(replyTarget);
                quoteText = abbreviate(replyTarget.getContentText(), 30);
                // Quote travels with the message so other players can see it too.
                normalized = "「引用 @" + quoteName + ": " + quoteText + "」" + normalized;
            }
            boolean command = normalized.startsWith("/");
            if (command) {
                this.client.player.networkHandler.sendChatCommand(normalized.substring(1));
            } else {
                if (!normalized.startsWith("「引用")
                        && (normalized.startsWith("http://") || normalized.startsWith("https://"))
                        && !normalized.contains("CICode")
                        && ImageFiles.isImageUrl(normalized)) {
                    normalized = "[[CICode,url=" + normalized + ",name=图片]]";
                }
                this.client.player.networkHandler.sendChatMessage(normalized);
            }
            this.client.inGameHud.getChatHud().addToMessageHistory(normalized);
            // Vanilla never echoes commands back into the chat feed as your own
            // message, so do not manufacture a local bubble for them either.
            // Non-command chat still gets an immediate local echo so the UI feels
            // like a phone messenger even before the server relays the message.
            if (!command) {
                UUID ownUuid = this.client.player.getUuid();
                String ownProfile = this.client.player.getName().getString();
                ChatStore.get().add(new ChatMessage(Text.literal(normalized), true, false, quoteName, quoteText,
                        ownUuid, ownProfile, ownProfile, normalized));
            }
            inputSetText("");
            replyTarget = null;
            inputFocused = true;
            scrollToBottom = true;
        }
    }

    private static String normalizeInput(String text) {
        return StringHelper.truncateChat(StringUtils.normalizeSpace(text.trim()));
    }

    /** url / name / intrinsic size carried by a CICode. width and height are 0 in codes written before they existed. */
    private record ImageMeta(String url, String name, int width, int height) {
    }

    private static ImageMeta parseImageMeta(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = CICODE.matcher(text);
        if (!m.find()) {
            return null;
        }
        int w = 0;
        int h = 0;
        if (m.group(3) != null) {
            try {
                w = Integer.parseInt(m.group(3));
                h = Integer.parseInt(m.group(4));
            } catch (NumberFormatException ignored) {
                // Malformed size: fall back to the placeholder box.
            }
        }
        return new ImageMeta(m.group(1), m.group(2), w, h);
    }

    /**
     * On-screen size of an image bubble: the intrinsic size scaled down to fit
     * IMAGE_MAX_W x IMAGE_MAX_H, never upscaled, so a small picture is never
     * blown up into a blur. Messages with no usable size — codes written before
     * w/h existed, or images whose header ImageIO cannot read — fall back to the
     * placeholder box, which is also what is drawn until the image downloads.
     */
    private static float[] imageBubbleSize(ImageMeta meta, float maxWidth) {
        float maxW = Math.min(UiTokens.IMAGE_MAX_W, maxWidth - UiTokens.BUBBLE_RETRACT - s(30));
        float maxH = UiTokens.IMAGE_MAX_H;
        if (meta == null || meta.width() <= 0 || meta.height() <= 0) {
            return new float[]{maxW, maxH};
        }
        float scale = Math.min(1.0F, Math.min(maxW / meta.width(), maxH / meta.height()));
        return new float[]{Math.max(1.0F, meta.width() * scale), Math.max(1.0F, meta.height() * scale)};
    }

    private static String extractImageUrl(String text) {
        int start = text.indexOf("[[CICode,url=");
        if (start < 0) {
            start = text.indexOf("[CICode,url=");
        }
        if (start < 0) {
            return null;
        }
        int urlStart = text.indexOf("url=", start) + 4;
        int end = text.indexOf(',', urlStart);
        if (end < 0) {
            end = text.indexOf(']', urlStart);
        }
        if (end < 0 || end <= urlStart) {
            return null;
        }
        return text.substring(urlStart, end);
    }

    private String ownName() {
        return this.client.player != null ? this.client.player.getName().getString() : tr("atomchat.sender.me");
    }

    /**
     * Display name for a message row. Own messages use the local player; other
     * messages prefer the structured sender name from the capture pipeline and
     * only fall back to the old hard-coded label when identity is unavailable.
     */
    private String messageSenderName(ChatMessage msg) {
        if (msg.isOwn()) {
            return ownName();
        }
        String name = msg.getSenderName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return tr("atomchat.sender.player");
    }

    private static String abbreviate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    private int accent() {
        return AtomChatConfig.get().accentColor;
    }

    private int ownBubble() {
        return AtomChatConfig.get().ownBubbleColor;
    }

    private int otherBubble() {
        return AtomChatConfig.get().otherBubbleColor;
    }

    private int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    private int textSecondary() {
        return AtomChatConfig.get().textSecondaryColor;
    }

    private int panelBg() {
        return AtomChatConfig.get().panelBgColor;
    }

    private float panelWidth() {
        return Math.min(UiTokens.s(AtomChatConfig.get().panelWidth), vw() - 32.0F);
    }

    private float panelHeight() {
        return Math.min(UiTokens.s(AtomChatConfig.get().panelHeight), vh() - 32.0F);
    }

    // Virtual UI space: independent of vanilla GUI scale, anchored at 1080p.
    private float uiDensity() {
        var window = this.client.getWindow();
        return Math.max(1.0F, window.getFramebufferHeight() / 1080.0F);
    }

    private float vw() {
        return this.client.getWindow().getFramebufferWidth() / uiDensity();
    }

    private float vh() {
        return this.client.getWindow().getFramebufferHeight() / uiDensity();
    }

    private float panelX() {
        return UiTokens.PANEL_ANCHOR_X;
    }

    private float panelY() {
        return (vh() - panelHeight()) / 2.0F;
    }

    /**
     * The one and only way to build the layout. Rendering and every hit test go
     * through it so the input bar's animated height can never desync a click
     * from what was drawn.
     */
    private UiLayout layout() {
        float replyH = replyTarget != null ? s(34) : 0.0F;
        return UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight(), inputExtraH, replyH);
    }

    // ---------------------------------------------------------------- input box

    /**
     * Wraps the draft text, then eases the bar's extra height toward what that
     * wrap needs (0 for one line, one line height for two — never more).
     *
     * <p>Must run before the message list is measured: the bar is bottom-anchored,
     * so whatever it gains the list gives up. When the list shrinks under a view
     * that was pinned to the bottom, re-stick it, otherwise the newest message
     * would slide out of sight.</p>
     *
     * @return a layout rebuilt with the updated height.
     */
    private UiLayout updateInputLayout(UiLayout current) {
        float lineH = inputLineHeight();
        List<String> lines = wrappedInput(current.inputTextMaxWidth());
        int targetLines = Math.min(UiTokens.INPUT_MAX_LINES, Math.max(1, lines.size()));
        float targetExtra = (targetLines - 1) * lineH;
        if (Math.abs(targetExtra - inputExtraH) > 0.5F && scrollTarget >= maxScroll - 3.0F) {
            scrollToBottom = true;
        }
        inputHeightAnim.animateTo(UiMotion.INPUT_GROW_MS, targetExtra);
        inputHeightAnim.update(frameDt);
        inputExtraH = inputHeightAnim.getValue();
        return layout();
    }

    private float inputLineHeight() {
        return SkiaFontRenderer.getHeight(FontManager.font(UiTokens.FONT_INPUT));
    }

    /** Wrapped input text, cached until the text or the available width changes. */
    private List<String> wrappedInput(float maxWidth) {
        String current = inputGetText();
        if (inputWrapCache == null || inputWrapWidth != maxWidth || !current.equals(inputWrapText)) {
            inputWrapText = current;
            inputWrapWidth = maxWidth;
            inputWrapCache = SkiaFontRenderer.wrap(FontManager.font(UiTokens.FONT_INPUT), current, maxWidth);
        }
        return inputWrapCache;
    }

    /**
     * Absolute index of the line holding the caret. wrap() drops the whitespace
     * at a break point, so line lengths can sum to slightly less than the full
     * text — the mapping is exact everywhere except right at a break.
     */
    private static int caretLine(List<String> lines, int caret) {
        int pos = 0;
        for (int i = 0; i < lines.size(); i++) {
            pos += lines.get(i).length();
            if (caret <= pos) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private int caretIndex() {
        return chatField == null ? 0 : MathHelper.clamp(chatField.getCursor(), 0, inputGetText().length());
    }

    /** Keeps the caret's line inside the visible window, clamping to the ends. */
    private void scrollInputToCaret(int caretLine, int totalLines) {
        int max = UiTokens.INPUT_MAX_LINES;
        if (totalLines <= max) {
            inputScrollLine = 0;
            return;
        }
        if (caretLine < inputScrollLine) {
            inputScrollLine = caretLine;
        } else if (caretLine > inputScrollLine + max - 1) {
            inputScrollLine = caretLine - max + 1;
        }
        inputScrollLine = Math.max(0, Math.min(inputScrollLine, totalLines - max));
    }

    /** Virtual-space top edge of the line the caret sits on, for IME anchoring. */
    private float caretLineTopY() {
        UiLayout l = layout();
        Font font = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = SkiaFontRenderer.getHeight(font);
        List<String> lines = wrappedInput(l.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return l.inputTextCenterY - lineH / 2.0F;
        }
        int line = caretLine(lines, caretIndex());
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, lines.size());
        int from = Math.min(inputScrollLine, lines.size() - shown);
        int row = Math.max(0, Math.min(line - from, shown - 1));
        return l.inputTextCenterY + row * lineH - lineH / 2.0F;
    }

    private float toVirtualX(double guiX) {
        return (float) (guiX * this.client.getWindow().getScaleFactor() / uiDensity());
    }

    private float toVirtualY(double guiY) {
        return (float) (guiY * this.client.getWindow().getScaleFactor() / uiDensity());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record MessageTextLine(ChatMessage message, int line, String text, float x, float y, float height) {
    }

    private record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                              float avatarX, float avatarY, float avatarSize, float bubbleY, float bubbleX,
                              float bubbleWidth, float bubbleBottom) {
    }
}
