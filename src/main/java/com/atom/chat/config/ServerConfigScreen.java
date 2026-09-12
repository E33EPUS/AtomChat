package com.atom.chat.config;

import com.atom.chat.net.ConfigPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server-config screen (0.2.9), built from vanilla widgets on purpose: it is
 * the one place in AtomChat that does not use Skija, because it edits a file on
 * the server rather than drawing the phone UI.
 *
 * <p>It is always built by the editing player's own client - the server only
 * sends a snapshot and answers the save - which is why this works on a rented
 * server that has no display at all. Nothing is written here: {@code 保存} sends
 * the values back, the server re-validates them, and the verdict either closes
 * the screen or leaves it open with a reason.
 */
public final class ServerConfigScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int PHRASE_ROW_H = 14;
    private static final String[] NUMBERS = {
        "maxFileKb", "maxTotalMb", "maxAvatarTotalMb", "retentionDays",
        "uploadCooldownMs", "packMaxFiles", "packMaxMb",
    };

    private final Screen parent;
    private final int configVersion;
    private final List<String> phrases = new ArrayList<>();
    private final Map<String, TextFieldWidget> boxes = new LinkedHashMap<>();

    private boolean hostingEnabled;
    private boolean packEnabled;
    private ConfigPayloads.PackStatus pack;
    private String name;
    private final Map<String, Integer> numbers = new LinkedHashMap<>();

    private ButtonWidget hostingButton;
    private ButtonWidget packButton;
    private TextFieldWidget nameBox;
    private TextFieldWidget phraseBox;
    private ButtonWidget phraseSaveButton;
    private ButtonWidget deleteButton;

    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listWidth;
    private int phraseScroll;
    private int selectedPhrase = -1;
    private String status = "";

    public ServerConfigScreen(Screen parent, int configVersion, ServerConfigValues values,
                              ConfigPayloads.PackStatus pack) {
        super(Text.translatable("atomchat.config.title"));
        this.parent = parent;
        this.configVersion = configVersion;
        this.pack = pack;
        this.hostingEnabled = values.hostingEnabled();
        this.packEnabled = values.packEnabled();
        this.name = values.packName();
        this.phrases.addAll(values.phrases());
        numbers.put("maxFileKb", values.maxFileKb());
        numbers.put("maxTotalMb", values.maxTotalMb());
        numbers.put("maxAvatarTotalMb", values.maxAvatarTotalMb());
        numbers.put("retentionDays", values.retentionDays());
        numbers.put("uploadCooldownMs", values.uploadCooldownMs());
        numbers.put("packMaxFiles", values.packMaxFiles());
        numbers.put("packMaxMb", values.packMaxMb());
    }

    /** The server rescanned the folders: update the summary line only. */
    public void acceptStatus(ConfigPayloads.PackStatus status) {
        this.pack = status;
        this.status = Text.translatable("atomchat.config.rescanned").getString();
    }

    /** The server answered a save: close on success, explain a refusal. */
    public void onResult(boolean ok, String error) {
        if (ok) {
            if (client != null) {
                client.setScreen(parent);
            }
            return;
        }
        status = errorText(error);
    }

    @Override
    public void removed() {
        com.atom.chat.net.ConfigScreenClient.forget(this);
    }

    @Override
    protected void init() {
        boxes.clear();
        rows.clear();
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int colW = 150;
        int y = 30;

        hostingButton = addDrawableChild(ButtonWidget.builder(
                        toggle("atomchat.config.hosting", hostingEnabled), b -> {
                            hostingEnabled = !hostingEnabled;
                            b.setMessage(toggle("atomchat.config.hosting", hostingEnabled));
                        })
                .dimensions(left, y, colW, 18).build());
        packButton = addDrawableChild(ButtonWidget.builder(
                        toggle("atomchat.config.packs", packEnabled), b -> {
                            packEnabled = !packEnabled;
                            b.setMessage(toggle("atomchat.config.packs", packEnabled));
                        })
                .dimensions(right, y, colW, 18).build());
        y += ROW_H;

        number(left, y, colW, "maxFileKb", "atomchat.config.max_file_kb");
        number(right, y, colW, "retentionDays", "atomchat.config.retention_days");
        y += ROW_H;
        number(left, y, colW, "maxTotalMb", "atomchat.config.max_total_mb");
        number(right, y, colW, "uploadCooldownMs", "atomchat.config.cooldown_ms");
        y += ROW_H;
        number(left, y, colW, "maxAvatarTotalMb", "atomchat.config.max_avatar_mb");
        number(right, y, colW, "packMaxFiles", "atomchat.config.pack_files");
        y += ROW_H;
        number(right, y, colW, "packMaxMb", "atomchat.config.pack_mb");
        y += ROW_H;

        int nameLabelW = 104;
        nameBox = addDrawableChild(new TextFieldWidget(textRenderer, left + nameLabelW, y,
                width - 2 * left - nameLabelW, 18, Text.translatable("atomchat.config.name")));
        nameBox.setMaxLength(ServerConfigValues.MAX_NAME_CHARS);
        nameBox.setText(name);
        rows.add(new Row("atomchat.config.name", left, y + 5));
        y += ROW_H + 6;

        int buttonY = height - 26;
        int inputY = buttonY - 22;
        listLeft = left;
        listWidth = width - 2 * left;
        listBottom = Math.max(y + PHRASE_ROW_H, inputY - 4);
        listTop = Math.max(y + 22, listBottom - 44);

        phraseBox = addDrawableChild(new TextFieldWidget(textRenderer, left, inputY, listWidth - 130, 18,
                Text.translatable("atomchat.config.phrase_hint")));
        phraseBox.setMaxLength(ServerConfigValues.MAX_PHRASE_CHARS);
        phraseSaveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("atomchat.config.phrase_add"),
                        b -> commitPhrase())
                .dimensions(left + listWidth - 126, inputY, 60, 18).build());
        deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("atomchat.config.phrase_delete"),
                        b -> deletePhrase())
                .dimensions(left + listWidth - 62, inputY, 62, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("atomchat.config.rescan"), b -> rescan())
                .dimensions(left + listWidth - 70, listTop - 28, 70, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("atomchat.config.save"), b -> save())
                .dimensions(width / 2 - 92, buttonY, 88, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("atomchat.config.cancel"), b -> close())
                .dimensions(width / 2 + 4, buttonY, 88, 20).build());
    }

    private void number(int x, int y, int colW, String key, String labelKey) {
        TextFieldWidget box = addDrawableChild(new TextFieldWidget(textRenderer, x + colW - 58, y, 58, 18,
                Text.translatable(labelKey)));
        box.setMaxLength(6);
        box.setText(String.valueOf(numbers.getOrDefault(key, 0)));
        boxes.put(key, box);
        rows.add(new Row(labelKey, x, y + 5));
    }

    private static Text toggle(String labelKey, boolean on) {
        return Text.translatable(labelKey).append(": ")
                .append(Text.translatable(on ? "atomchat.config.on" : "atomchat.config.off"));
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    /** A label drawn next to a widget that already knows its own position. */
    private record Row(String key, int x, int y) {
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        for (Row row : rows) {
            context.drawTextWithShadow(textRenderer, Text.translatable(row.key()), row.x(), row.y(), 0xC8C8C8);
        }
        context.drawTextWithShadow(textRenderer, packLine(), listLeft, listTop - 24, 0x9FE8FF);
        context.drawTextWithShadow(textRenderer, Text.translatable("atomchat.config.phrases"),
                listLeft, listTop - 11, 0xC8C8C8);
        drawPhrases(context);
        context.drawTextWithShadow(textRenderer, status, listLeft, height - 60, 0xFFC080);
    }

    /** Never pauses the world: this screen is opened from inside a running game. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    private String packLine() {
        int files = pack == null ? 0 : pack.files();
        long bytes = pack == null ? 0L : pack.bytes();
        String hash = pack == null || pack.packHash().isEmpty() ? "-" : pack.packHash().substring(0, 8);
        return tr("atomchat.config.pack_status") + " " + files + " / " + (bytes / 1024) + " KB / " + hash;
    }

    private void drawPhrases(DrawContext context) {
        int visible = visiblePhraseRows();
        phraseScroll = Math.max(0, Math.min(phraseScroll, Math.max(0, phrases.size() - visible)));
        if (phrases.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("atomchat.config.phrase_empty"),
                    listLeft + 4, listTop, 0x808080);
            return;
        }
        for (int i = 0; i < visible; i++) {
            int index = phraseScroll + i;
            if (index >= phrases.size()) {
                break;
            }
            int y = listTop + i * PHRASE_ROW_H;
            if (index == selectedPhrase) {
                context.fill(listLeft - 2, y - 1, listLeft + listWidth, y + PHRASE_ROW_H - 3, 0x40FFFFFF);
            }
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(phrases.get(index), listWidth - 8),
                    listLeft + 4, y, index == selectedPhrase ? 0xFFD479 : 0xE0E0E0);
        }
    }

    private int visiblePhraseRows() {
        return Math.max(1, (listBottom - listTop) / PHRASE_ROW_H);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listLeft && mx <= listLeft + listWidth && my >= listTop && my < listBottom) {
            int index = phraseScroll + (int) ((my - listTop) / PHRASE_ROW_H);
            if (index >= 0 && index < phrases.size()) {
                // Picking a row loads it for editing; the buttons do the rest.
                selectedPhrase = index;
                phraseBox.setText(phrases.get(index));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= listLeft && mx <= listLeft + listWidth && my >= listTop && my < listBottom) {
            phraseScroll = Math.max(0, phraseScroll - (int) vertical);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private void commitPhrase() {
        String text = phraseBox.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        if (selectedPhrase >= 0 && selectedPhrase < phrases.size()) {
            phrases.set(selectedPhrase, text);
        } else if (phrases.size() < ServerConfigValues.MAX_PHRASES) {
            phrases.add(text);
        }
        phraseBox.setText("");
        selectedPhrase = -1;
        status = "";
    }

    private void deletePhrase() {
        if (selectedPhrase >= 0 && selectedPhrase < phrases.size()) {
            phrases.remove(selectedPhrase);
            selectedPhrase = -1;
            phraseBox.setText("");
            status = "";
        }
    }

    /** Asks the server to re-scan its emote folder; the answer updates the summary. */
    private void rescan() {
        ClientPlayNetworking.send(new ConfigPayloads.Refresh());
        status = tr("atomchat.config.rescanning");
    }

    private void save() {
        Integer fileKb = number("maxFileKb");
        Integer totalMb = number("maxTotalMb");
        Integer avatarMb = number("maxAvatarTotalMb");
        Integer days = number("retentionDays");
        Integer cooldown = number("uploadCooldownMs");
        Integer packFiles = number("packMaxFiles");
        Integer packMb = number("packMaxMb");
        if (fileKb == null || totalMb == null || avatarMb == null || days == null
                || cooldown == null || packFiles == null || packMb == null) {
            status = tr("atomchat.config.bad_number");
            return;
        }
        ClientPlayNetworking.send(new ConfigPayloads.Save(configVersion, new ServerConfigValues(
                hostingEnabled, packEnabled, fileKb, totalMb, avatarMb, days, cooldown,
                packFiles, packMb, nameBox.getText(), List.copyOf(phrases))));
        status = tr("atomchat.config.saving");
    }

    private Integer number(String key) {
        TextFieldWidget box = boxes.get(key);
        if (box == null) {
            return null;
        }
        try {
            return Integer.valueOf(box.getText().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String errorText(String error) {
        String key = switch (error == null ? "" : error) {
            case ServerConfigValues.ERR_RANGE -> "atomchat.config.error.bad_range";
            case ServerConfigValues.ERR_PHRASES -> "atomchat.config.error.bad_phrases";
            case ServerConfigValues.ERR_NAME -> "atomchat.config.error.bad_name";
            case "conflict" -> "atomchat.config.error.conflict";
            default -> "atomchat.config.error.unknown";
        };
        return tr(key);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
