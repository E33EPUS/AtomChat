package com.atom.chat.screen;
import com.atom.chat.AtomChat;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.ImageUploader;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.util.FilePicker;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class AtomChatScreen extends Screen {
    public static final float PANEL_RADIUS = 28.0F;
    private static final float HEADER_HEIGHT = 56.0F;
    private static final float INPUT_HEIGHT = 76.0F;

    private final String originalChatText;
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final ImageUploader imageUploader = new ImageUploader();
    private final List<MessageHit> hits = new ArrayList<>();

    private String inputText;
    private boolean inputFocused;
    private float scrollY;
    private float maxScroll;
    private ChatMessage replyTarget;
    private boolean emojiOpen;
    private ChatMessage contextMessage;
    private float contextX;
    private float contextY;

    private int messageHistoryIndex = -1;
    private String chatLastMessage = "";

    private long lastAvatarClickTime;
    private int lastAvatarClickIndex = -1;
    private int pokeIndex = -1;
    private long pokeStartTime;

    public AtomChatScreen(String originalChatText) {
        super(Text.translatable("atomchat.screen.title"));
        this.originalChatText = originalChatText;
        this.inputText = originalChatText == null ? "" : originalChatText;
    }

    public String getOriginalChatText() {
        return originalChatText;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        graphics.checkFrameBufferId();
        graphics.draw(canvas -> drawPhone(canvas, mouseX, mouseY, delta));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // World stays fully visible, same as vanilla chat; the panel provides its own background.
    }

    @Override
    protected void init() {
        messageHistoryIndex = this.client.inGameHud.getChatHud().getMessageHistory().size();
    }

    private void drawPhone(Canvas canvas, int mouseX, int mouseY, float delta) {
        float x = panelX();
        float y = panelY();

        try (Paint panel = new Paint().setColor(panelBg())) {
            canvas.drawRRect(RRect.makeXYWH(x, y, panelWidth(), panelHeight(), PANEL_RADIUS), panel);
        }

        // Header
        try (Paint header = new Paint().setColor(Color.makeARGB(70, 255, 255, 255))) {
            canvas.drawRRect(RRect.makeXYWH(x, y, panelWidth(), HEADER_HEIGHT, PANEL_RADIUS), header);
        }
        Font titleFont = FontManager.font(20.0F);
        SkiaFontRenderer.drawText(canvas, titleFont, "世界频道", x + 20, SkiaFontRenderer.baselineY(titleFont, y + HEADER_HEIGHT / 2.0F), textPrimary());
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        Font timeFont = FontManager.font(13.0F);
        SkiaFontRenderer.drawTextRight(canvas, timeFont, time, x + panelWidth() - 20.0F, y + HEADER_HEIGHT / 2.0F, textSecondary());

        float listX = x + 12;
        float listY = y + HEADER_HEIGHT + 8;
        float listW = panelWidth() - 24;
        float listH = panelHeight() - HEADER_HEIGHT - INPUT_HEIGHT - 16;
        drawMessages(canvas, listX, listY, listW, listH);

        // Reply bar above input
        if (replyTarget != null) {
            float replyY = y + panelHeight() - INPUT_HEIGHT - 34;
            SkiaDraw.drawRoundedRect(canvas, listX, replyY, listW, 26, 8, Color.makeARGB(90, 74, 144, 226));
            Font replyFont = FontManager.font(12.0F);
            SkiaFontRenderer.drawText(canvas, replyFont, "回复: " + abbreviate(replyTarget.getRawText(), 26), listX + 8, SkiaFontRenderer.baselineY(replyFont, replyY + 13.0F), textPrimary());
        }

        // Input bar
        float inputX = x + 12;
        float inputY = y + panelHeight() - INPUT_HEIGHT - 8;
        float inputW = panelWidth() - 24;
        SkiaDraw.drawRoundedRect(canvas, inputX, inputY, inputW, INPUT_HEIGHT, 18, Color.makeARGB(60, 255, 255, 255));

        // 图片 / 表情 buttons
        float buttonY = inputY + 8;
        Font buttonFont = FontManager.font(13.0F);
        SkiaDraw.drawRoundedRect(canvas, inputX + 10, buttonY, 46, 26, 8, Color.makeARGB(50, 255, 255, 255));
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, "图片", inputX + 33, buttonY + 13, textSecondary());
        SkiaDraw.drawRoundedRect(canvas, inputX + 62, buttonY, 46, 26, 8, Color.makeARGB(50, 255, 255, 255));
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, "表情", inputX + 85, buttonY + 13, textSecondary());

        // Input text / placeholder / cursor
        Font inputFont = FontManager.font(15.0F);
        float inputCenterY = inputY + 55.0F;
        if (inputText.isEmpty() && !inputFocused) {
            SkiaFontRenderer.drawText(canvas, inputFont, "输入点什么，可以直接粘贴文件或图片哦~", inputX + 14, SkiaFontRenderer.baselineY(inputFont, inputCenterY), textSecondary());
        } else if (!inputText.isEmpty()) {
            SkiaFontRenderer.drawText(canvas, inputFont, inputText, inputX + 14, SkiaFontRenderer.baselineY(inputFont, inputCenterY), textPrimary());
        }
        if (inputFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cursorX = inputX + 14 + SkiaFontRenderer.getStringWidth(inputFont, inputText) + 2.0F;
            float cursorH = SkiaFontRenderer.textHeight(inputFont);
            SkiaDraw.drawRoundedRect(canvas, cursorX, inputCenterY - cursorH / 2.0F, 2.0F, cursorH, 1.0F, textPrimary());
        }

        float sendW = 64;
        float sendX = x + panelWidth() - sendW - 16;
        float sendY = inputY + 10;
        SkiaDraw.drawRoundedRect(canvas, sendX, sendY, sendW, INPUT_HEIGHT - 20, 12, accent());
        Font sendFont = FontManager.font(16.0F);
        SkiaFontRenderer.drawTextCentered(canvas, sendFont, "发送", sendX + sendW / 2.0F, sendY + (INPUT_HEIGHT - 20) / 2.0F, textPrimary());

        drawEmojiPanel(canvas);
        drawContextMenu(canvas);
    }

    private void drawMessages(Canvas canvas, float x, float y, float width, float height) {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        hits.clear();
        canvas.save();
        try {
            SkiaDraw.clip(canvas, x, y, width, height, 0.0F);
            canvas.translate(0.0F, -scrollY);
            float cursorY = y;
            int index = 0;
            for (ChatMessage msg : messages) {
                MessageHit hit = drawMessage(canvas, msg, x, cursorY, width, index);
                hits.add(hit);
                cursorY = hit.bottom() + 10;
                index++;
                if (cursorY - y > height + 200) {
                    break;
                }
            }
            recomputeMaxScroll(messages, y, height);
        } finally {
            canvas.restore();
        }
    }

    private MessageHit drawMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        Font font = FontManager.font(15.0F);
        float bubbleMaxWidth = maxWidth - 80;
        String raw = msg.getRawText();
        String imageUrl = extractImageUrl(raw);
        if (imageUrl != null) {
            return drawImageMessage(canvas, msg, raw, imageUrl, x, y, maxWidth, index);
        }
        float textWidth = SkiaFontRenderer.getStringWidth(font, raw);
        float bubbleWidth = Math.min(bubbleMaxWidth, Math.max(60, textWidth + 24));
        float lineHeight = SkiaFontRenderer.getHeight(font);
        int lineCount = SkiaFontRenderer.wrap(font, raw, bubbleWidth - 20).size();
        float textHeight = Math.max(lineHeight, lineCount * lineHeight);
        float bubbleHeight = textHeight + 18;

        String name = msg.isOwn() ? "我" : "玩家";
        float nameX = msg.isOwn() ? x + maxWidth - 80 : x + 34;
        Font nameFont = FontManager.font(12.0F);
        SkiaFontRenderer.drawText(canvas, nameFont, name, nameX, SkiaFontRenderer.baselineY(nameFont, y + 9.0F), textSecondary());

        float avatarSize = 26;
        float avatarX = msg.isOwn() ? x + maxWidth - avatarSize : x;
        float avatarY = y + 6;

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
        SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2.0F, Color.makeARGB(255, 120, 130, 145));

        float bubbleX;
        if (msg.isOwn()) {
            bubbleX = x + maxWidth - bubbleWidth - 34;
        } else {
            bubbleX = x + 34;
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, y + 18, bubbleWidth, bubbleHeight, 12, msg.isOwn() ? ownBubble() : otherBubble());
        SkiaFontRenderer.drawText(canvas, font, raw, bubbleX + 10, SkiaFontRenderer.baselineY(font, y + 18 + bubbleHeight / 2.0F), textPrimary());

        float bottom = y + 18 + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, avatarSize, bubbleX, bubbleWidth, bottom);
    }

    private MessageHit drawImageMessage(Canvas canvas, ChatMessage msg, String raw, String imageUrl, float x, float y, float maxWidth, int index) {
        String name = msg.isOwn() ? "我" : "玩家";
        float nameX = msg.isOwn() ? x + maxWidth - 80 : x + 34;
        Font nameFont = FontManager.font(12.0F);
        SkiaFontRenderer.drawText(canvas, nameFont, name, nameX, SkiaFontRenderer.baselineY(nameFont, y + 9.0F), textSecondary());

        float avatarSize = 26;
        float avatarX = msg.isOwn() ? x + maxWidth - avatarSize : x;
        float avatarY = y + 6;
        SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2.0F, Color.makeARGB(255, 120, 130, 145));

        float imageW = Math.min(220, maxWidth - 70);
        float imageH = 140;
        float bubbleX = msg.isOwn() ? x + maxWidth - imageW - 34 : x + 34;
        SkiaDraw.drawRoundedRect(canvas, bubbleX, y + 18, imageW, imageH, 12, otherBubble());

        Image image = ImageLoader.get().get(imageUrl);
        if (image != null) {
            float aspect = (float) image.getWidth() / Math.max(1, image.getHeight());
            float drawH = Math.min(imageH, imageW / aspect);
            SkiaDraw.drawRoundedImage(canvas, image, bubbleX, y + 18 + (imageH - drawH) / 2.0F, imageW, drawH, 12);
        } else {
            Font loadingFont = FontManager.font(13.0F);
            SkiaFontRenderer.drawText(canvas, loadingFont, "图片加载中…", bubbleX + 12, SkiaFontRenderer.baselineY(loadingFont, y + 18 + imageH / 2.0F), textSecondary());
        }

        float bottom = y + 18 + imageH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, avatarSize, bubbleX, imageW, bottom);
    }

    private void recomputeMaxScroll(List<ChatMessage> messages, float top, float height) {
        float contentHeight = 0;
        for (ChatMessage msg : messages) {
            contentHeight += estimateMessageHeight(msg) + 10;
        }
        maxScroll = Math.max(0, contentHeight - height);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
    }

    private float estimateMessageHeight(ChatMessage msg) {
        Font font = FontManager.font(15.0F);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        int lines = SkiaFontRenderer.wrap(font, msg.getRawText(), panelWidth() - 24 - 80 - 20).size();
        return 18 + Math.max(lineHeight, lines * lineHeight) + 18;
    }

    private void drawEmojiPanel(Canvas canvas) {
        if (!emojiOpen) {
            return;
        }
        float x = panelX();
        float y = panelY();
        float panelW = 260;
        float panelH = 120;
        float panelX = x + 12;
        float panelY = y + panelHeight() - INPUT_HEIGHT - 8 - panelH - 6;
        SkiaDraw.drawRoundedRect(canvas, panelX, panelY, panelW, panelH, 14, Color.makeARGB(245, 35, 39, 47));
        SkiaDraw.drawRoundedShadow(canvas, panelX, panelY, panelW, panelH, 14, 8, Color.makeARGB(100, 0, 0, 0));

        String[] emojis = {"👍", "😂", "❤️", "🎉", "🔥", "😮", "😢", "👀", "✨", "💯", "🙏", "🤔"};
        Font emojiFont = FontManager.font(22.0F);
        int cols = 6;
        for (int i = 0; i < emojis.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float ex = panelX + 12 + col * 38;
            float ey = panelY + 16 + row * 38;
            SkiaFontRenderer.drawText(canvas, emojiFont, emojis[i], ex, SkiaFontRenderer.baselineY(emojiFont, ey + 19.0F), textPrimary());
        }
    }

    private void drawContextMenu(Canvas canvas) {
        if (contextMessage == null) {
            return;
        }
        float menuW = 110;
        float menuH = 64;
        float menuX = Math.min(contextX, panelX() + panelWidth() - menuW - 8);
        float menuY = Math.min(contextY, panelY() + panelHeight() - menuH - 8);
        SkiaDraw.drawRoundedRect(canvas, menuX, menuY, menuW, menuH, 10, Color.makeARGB(245, 35, 39, 47));
        SkiaDraw.drawRoundedShadow(canvas, menuX, menuY, menuW, menuH, 10, 8, Color.makeARGB(100, 0, 0, 0));
        Font menuFont = FontManager.font(14.0F);
        SkiaFontRenderer.drawText(canvas, menuFont, "复制", menuX + 12, SkiaFontRenderer.baselineY(menuFont, menuY + 16.0F), textPrimary());
        SkiaFontRenderer.drawText(canvas, menuFont, "引用", menuX + 12, SkiaFontRenderer.baselineY(menuFont, menuY + 48.0F), textPrimary());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        float listX = panelX() + 12;
        float listY = panelY() + HEADER_HEIGHT + 8;
        float listW = panelWidth() - 24;
        float listH = panelHeight() - HEADER_HEIGHT - INPUT_HEIGHT - 16;
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            scrollY -= verticalAmount * 20.0F;
            scrollY = Math.max(0, Math.min(scrollY, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        float panelX = panelX();
        float panelY = panelY();

        // Emoji panel click
        if (emojiOpen) {
            float panelW = 260;
            float panelH = 120;
            float panelX2 = panelX + 12;
            float panelY2 = panelY + panelHeight() - INPUT_HEIGHT - 8 - panelH - 6;
            if (mx >= panelX2 && mx <= panelX2 + panelW && my >= panelY2 && my <= panelY2 + panelH) {
                String[] emojis = {"👍", "😂", "❤️", "🎉", "🔥", "😮", "😢", "👀", "✨", "💯", "🙏", "🤔"};
                int cols = 6;
                int col = (int) ((mx - panelX2 - 12) / 38);
                int row = (int) ((my - panelY2 - 16) / 38);
                int idx = row * cols + col;
                if (idx >= 0 && idx < emojis.length) {
                    inputText += emojis[idx];
                    emojiOpen = false;
                    return true;
                }
            }
            emojiOpen = false;
        }

        // Context menu click
        if (contextMessage != null) {
            float menuW = 110;
            float menuH = 64;
            float menuX = Math.min(contextX, panelX + panelWidth() - menuW - 8);
            float menuY = Math.min(contextY, panelY + panelHeight() - menuH - 8);
            if (mx >= menuX && mx <= menuX + menuW && my >= menuY && my <= menuY + menuH) {
                if (my < menuY + 32) {
                    this.client.keyboard.setClipboard(contextMessage.getRawText());
                } else {
                    replyTarget = contextMessage;
                }
                contextMessage = null;
                return true;
            }
            contextMessage = null;
        }

        // Emoji / image buttons
        float inputX = panelX + 12;
        float inputY = panelY + panelHeight() - INPUT_HEIGHT - 8;
        if (button == 0 && my >= inputY + 8 && my <= inputY + 34) {
            if (mx >= inputX + 10 && mx <= inputX + 56) {
                pickAndUploadImage();
                return true;
            }
            if (mx >= inputX + 62 && mx <= inputX + 108) {
                emojiOpen = !emojiOpen;
                return true;
            }
        }

        // Send button
        float sendW = 64;
        float sendX = panelX + panelWidth() - sendW - 16;
        float sendY = inputY + 10;
        if (button == 0 && mx >= sendX && mx <= sendX + sendW && my >= sendY && my <= sendY + INPUT_HEIGHT - 10) {
            sendMessage(inputText);
            return true;
        }

        // Input box focus
        float inputW = panelWidth() - 24;
        inputFocused = false;
        if (button == 0 && mx >= inputX && mx <= inputX + inputW && my >= inputY && my <= inputY + INPUT_HEIGHT) {
            inputFocused = true;
            return true;
        }

        // Message interactions
        for (MessageHit hit : hits) {
            if (my >= hit.y() && my <= hit.bottom()) {
                if (button == 1) {
                    contextMessage = hit.message();
                    contextX = mx;
                    contextY = my;
                    return true;
                }
                if (button == 0 && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                        && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()) {
                    long now = System.currentTimeMillis();
                    if (now - lastAvatarClickTime < 350 && lastAvatarClickIndex == hit.index()) {
                        pokeIndex = hit.index();
                        pokeStartTime = now;
                        lastAvatarClickTime = 0;
                    } else {
                        lastAvatarClickTime = now;
                        lastAvatarClickIndex = hit.index();
                        inputText += "@玩家 ";
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputFocused) {
            if (keyCode == 257 || keyCode == 335) {
                inputFocused = true;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (hasControlDown() && keyCode == 86) { // Ctrl+V paste
            pasteFromClipboard();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            sendMessage(inputText);
            return true;
        }
        if (keyCode == 259) { // Backspace
            if (!inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
            }
            return true;
        }
        if (keyCode == 265) { // Up: older history
            setChatFromHistory(-1);
            return true;
        }
        if (keyCode == 264) { // Down: newer history
            setChatFromHistory(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!inputFocused) {
            return true;
        }
        if (chr == '§') {
            return true;
        }
        if (inputText.length() < 256) {
            inputText += chr;
        }
        return true;
    }

    private void pasteFromClipboard() {
        String clip = this.client.keyboard.getClipboard();
        if (clip == null || clip.isEmpty()) {
            return;
        }
        String merged = StringUtils.normalizeSpace((inputText + " " + clip).trim());
        inputText = StringHelper.truncateChat(merged);
    }

    private void setChatFromHistory(int offset) {
        var history = this.client.inGameHud.getChatHud().getMessageHistory();
        int size = history.size();
        int idx = MathHelper.clamp(messageHistoryIndex + offset, 0, size);
        if (idx == messageHistoryIndex) {
            return;
        }
        if (idx == size) {
            messageHistoryIndex = size;
            inputText = chatLastMessage;
        } else {
            if (messageHistoryIndex == size) {
                chatLastMessage = inputText;
            }
            inputText = history.get(idx);
            messageHistoryIndex = idx;
        }
    }

    private void pickAndUploadImage() {
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage();
            if (file == null) {
                return;
            }
            imageUploader.upload(file, url -> {
                String code = "[[CICode,url=" + url + ",name=" + file.getFileName().toString() + "]]";
                inputText = inputText.isEmpty() ? code : inputText + " " + code;
            }, error -> {
                AtomChat.LOGGER.warn("Image upload failed: {}", error);
            });
        }, "AtomChat-ImagePicker");
        worker.setDaemon(true);
        worker.start();
    }

    private void sendMessage(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isEmpty()) {
            return;
        }
        if (this.client.player != null) {
            if (normalized.startsWith("/")) {
                this.client.player.networkHandler.sendChatCommand(normalized.substring(1));
            } else {
                if ((normalized.startsWith("http://") || normalized.startsWith("https://")) && !normalized.contains("CICode")) {
                    normalized = "[[CICode,url=" + normalized + ",name=图片]]";
                }
                this.client.player.networkHandler.sendChatMessage(normalized);
            }
            this.client.inGameHud.getChatHud().addToMessageHistory(normalized);
            ChatStore.get().add(new ChatMessage(Text.literal(normalized), true));
            inputText = "";
            replyTarget = null;
            inputFocused = false;
            messageHistoryIndex = this.client.inGameHud.getChatHud().getMessageHistory().size();
            chatLastMessage = "";
        }
    }

    private static String normalizeInput(String text) {
        return StringHelper.truncateChat(StringUtils.normalizeSpace(text.trim()));
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
        return Math.min(AtomChatConfig.get().panelWidth, vw() - 32.0F);
    }

    private float panelHeight() {
        return Math.min(AtomChatConfig.get().panelHeight, vh() - 32.0F);
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
        return 24.0F;
    }

    private float panelY() {
        return (vh() - panelHeight()) / 2.0F;
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

    private record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                              float avatarX, float avatarY, float avatarSize, float bubbleX, float bubbleWidth, float bubbleBottom) {
    }
}
