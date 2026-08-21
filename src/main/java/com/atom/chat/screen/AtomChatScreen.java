package com.atom.chat.screen;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class AtomChatScreen extends Screen {
    public static final float PANEL_RADIUS = 28.0F;
    private static final float HEADER_HEIGHT = 56.0F;
    private static final float INPUT_HEIGHT = 76.0F;

    private final String originalChatText;
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final List<MessageHit> hits = new ArrayList<>();

    private String inputText;
    private float scrollY;
    private float maxScroll;
    private ChatMessage replyTarget;
    private boolean emojiOpen;
    private ChatMessage contextMessage;
    private float contextX;
    private float contextY;

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

    private void drawPhone(Canvas canvas, int mouseX, int mouseY, float delta) {
        float x = (this.width - panelWidth()) / 2.0F;
        float y = (this.height - panelHeight()) / 2.0F;

        try (Paint dim = new Paint().setColor(Color.makeARGB(120, 0, 0, 0))) {
            canvas.drawRect(Rect.makeXYWH(0, 0, this.width, this.height), dim);
        }

        try (Paint panel = new Paint().setColor(panelBg())) {
            canvas.drawRRect(RRect.makeXYWH(x, y, panelWidth(), panelHeight(), PANEL_RADIUS), panel);
        }

        // Header
        try (Paint header = new Paint().setColor(Color.makeARGB(70, 255, 255, 255))) {
            canvas.drawRRect(RRect.makeXYWH(x, y, panelWidth(), HEADER_HEIGHT, PANEL_RADIUS), header);
        }
        SkiaFontRenderer.drawText(canvas, FontManager.font(20.0F), "世界频道", x + 20, y + 24, textPrimary());
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        SkiaFontRenderer.drawText(canvas, FontManager.font(13.0F), time, x + panelWidth() - 70, y + 25, textSecondary());

        float listX = x + 12;
        float listY = y + HEADER_HEIGHT + 8;
        float listW = panelWidth() - 24;
        float listH = panelHeight() - HEADER_HEIGHT - INPUT_HEIGHT - 16;
        drawMessages(canvas, listX, listY, listW, listH);

        // Reply bar above input
        if (replyTarget != null) {
            float replyY = y + panelHeight() - INPUT_HEIGHT - 34;
            SkiaDraw.drawRoundedRect(canvas, listX, replyY, listW, 26, 8, Color.makeARGB(90, 74, 144, 226));
            SkiaFontRenderer.drawText(canvas, FontManager.font(12.0F), "回复: " + abbreviate(replyTarget.getRawText(), 26), listX + 8, replyY + 16, textPrimary());
        }

        // Input bar
        float inputX = x + 12;
        float inputY = y + panelHeight() - INPUT_HEIGHT - 8;
        float inputW = panelWidth() - 24;
        SkiaDraw.drawRoundedRect(canvas, inputX, inputY, inputW, INPUT_HEIGHT, 18, Color.makeARGB(60, 255, 255, 255));

        // 图片 / 表情 buttons
        float buttonY = inputY + 8;
        SkiaDraw.drawRoundedRect(canvas, inputX + 10, buttonY, 46, 26, 8, Color.makeARGB(50, 255, 255, 255));
        SkiaFontRenderer.drawText(canvas, FontManager.font(13.0F), "图片", inputX + 19, buttonY + 17, textSecondary());
        SkiaDraw.drawRoundedRect(canvas, inputX + 62, buttonY, 46, 26, 8, Color.makeARGB(50, 255, 255, 255));
        SkiaFontRenderer.drawText(canvas, FontManager.font(13.0F), "表情", inputX + 71, buttonY + 17, textSecondary());

        Font inputFont = FontManager.font(15.0F);
        String display = inputText.isEmpty() ? "输入点什么，可以直接粘贴文件或图片哦~" : inputText;
        int inputColor = inputText.isEmpty() ? textSecondary() : textPrimary();
        SkiaFontRenderer.drawText(canvas, inputFont, display, inputX + 14, inputY + 48, inputColor);

        float sendW = 64;
        float sendX = x + panelWidth() - sendW - 16;
        float sendY = inputY + 10;
        SkiaDraw.drawRoundedRect(canvas, sendX, sendY, sendW, INPUT_HEIGHT - 20, 12, accent());
        Font sendFont = FontManager.font(16.0F);
        float sendTextX = sendX + (sendW - SkiaFontRenderer.getStringWidth(sendFont, "发送")) / 2.0F;
        SkiaFontRenderer.drawText(canvas, sendFont, "发送", sendTextX, sendY + 20, textPrimary());

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
        SkiaFontRenderer.drawText(canvas, FontManager.font(12.0F), name, nameX, y + 10, textSecondary());

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
        SkiaFontRenderer.drawText(canvas, font, raw, bubbleX + 10, y + 18 + 13, textPrimary());

        float bottom = y + 18 + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, avatarSize, bubbleX, bubbleWidth, bottom);
    }

    private MessageHit drawImageMessage(Canvas canvas, ChatMessage msg, String raw, String imageUrl, float x, float y, float maxWidth, int index) {
        String name = msg.isOwn() ? "我" : "玩家";
        float nameX = msg.isOwn() ? x + maxWidth - 80 : x + 34;
        SkiaFontRenderer.drawText(canvas, FontManager.font(12.0F), name, nameX, y + 10, textSecondary());

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
            SkiaFontRenderer.drawText(canvas, FontManager.font(13.0F), "图片加载中…", bubbleX + 12, y + 18 + 18, textSecondary());
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
        float x = (this.width - panelWidth()) / 2.0F;
        float y = (this.height - panelHeight()) / 2.0F;
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
            SkiaFontRenderer.drawText(canvas, emojiFont, emojis[i], ex, ey + 20, textPrimary());
        }
    }

    private void drawContextMenu(Canvas canvas) {
        if (contextMessage == null) {
            return;
        }
        float menuW = 110;
        float menuH = 64;
        float menuX = Math.min(contextX, (this.width - panelWidth()) / 2.0F + panelWidth() - menuW - 8);
        float menuY = Math.min(contextY, (this.height - panelHeight()) / 2.0F + panelHeight() - menuH - 8);
        SkiaDraw.drawRoundedRect(canvas, menuX, menuY, menuW, menuH, 10, Color.makeARGB(245, 35, 39, 47));
        SkiaDraw.drawRoundedShadow(canvas, menuX, menuY, menuW, menuH, 10, 8, Color.makeARGB(100, 0, 0, 0));
        SkiaFontRenderer.drawText(canvas, FontManager.font(14.0F), "复制", menuX + 12, menuY + 22, textPrimary());
        SkiaFontRenderer.drawText(canvas, FontManager.font(14.0F), "引用", menuX + 12, menuY + 48, textPrimary());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float panelX = (this.width - panelWidth()) / 2.0F;
        float panelY = (this.height - panelHeight()) / 2.0F;
        float listX = panelX + 12;
        float listY = panelY + HEADER_HEIGHT + 8;
        float listW = panelWidth() - 24;
        float listH = panelHeight() - HEADER_HEIGHT - INPUT_HEIGHT - 16;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            scrollY -= verticalAmount * 20.0F;
            scrollY = Math.max(0, Math.min(scrollY, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float panelX = (this.width - panelWidth()) / 2.0F;
        float panelY = (this.height - panelHeight()) / 2.0F;

        // Emoji panel click
        if (emojiOpen) {
            float panelW = 260;
            float panelH = 120;
            float panelX2 = panelX + 12;
            float panelY2 = panelY + panelHeight() - INPUT_HEIGHT - 8 - panelH - 6;
            if (mouseX >= panelX2 && mouseX <= panelX2 + panelW && mouseY >= panelY2 && mouseY <= panelY2 + panelH) {
                String[] emojis = {"👍", "😂", "❤️", "🎉", "🔥", "😮", "😢", "👀", "✨", "💯", "🙏", "🤔"};
                int cols = 6;
                int col = (int) ((mouseX - panelX2 - 12) / 38);
                int row = (int) ((mouseY - panelY2 - 16) / 38);
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
            if (mouseX >= menuX && mouseX <= menuX + menuW && mouseY >= menuY && mouseY <= menuY + menuH) {
                if (mouseY < menuY + 32) {
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
        if (button == 0 && mouseY >= inputY + 8 && mouseY <= inputY + 34) {
            if (mouseX >= inputX + 10 && mouseX <= inputX + 56) {
                // Image button: TODO uguu upload
                return true;
            }
            if (mouseX >= inputX + 62 && mouseX <= inputX + 108) {
                emojiOpen = !emojiOpen;
                return true;
            }
        }

        // Send button
        float sendW = 64;
        float sendX = panelX + panelWidth() - sendW - 16;
        float sendY = inputY + 10;
        if (button == 0 && mouseX >= sendX && mouseX <= sendX + sendW && mouseY >= sendY && mouseY <= sendY + INPUT_HEIGHT - 10) {
            sendMessage(inputText);
            return true;
        }

        // Message interactions
        for (MessageHit hit : hits) {
            if (mouseY >= hit.y() && mouseY <= hit.bottom()) {
                if (button == 1) {
                    contextMessage = hit.message();
                    contextX = (float) mouseX;
                    contextY = (float) mouseY;
                    return true;
                }
                if (button == 0 && mouseX >= hit.avatarX() && mouseX <= hit.avatarX() + hit.avatarSize()
                        && mouseY >= hit.avatarY() && mouseY <= hit.avatarY() + hit.avatarSize()) {
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
        if (keyCode == 257 || keyCode == 335) {
            sendMessage(inputText);
            return true;
        }
        if (keyCode == 259) {
            if (!inputText.isEmpty()) {
                inputText = inputText.substring(0, inputText.length() - 1);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr == '§') {
            return true;
        }
        if (inputText.length() < 256) {
            inputText += chr;
        }
        return true;
    }

    private void sendMessage(String text) {
        String normalized = text.trim();
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
            ChatStore.get().add(new ChatMessage(Text.literal(normalized), true));
            inputText = "";
            replyTarget = null;
        }
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
        return AtomChatConfig.get().panelWidth;
    }

    private float panelHeight() {
        return AtomChatConfig.get().panelHeight;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                              float avatarX, float avatarY, float avatarSize, float bubbleX, float bubbleWidth, float bubbleBottom) {
    }
}
