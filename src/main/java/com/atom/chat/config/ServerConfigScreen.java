package com.atom.chat.config;

import com.atom.chat.net.ConfigPayloads;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

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
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();

    private boolean hostingEnabled;
    private boolean packEnabled;
    private ConfigPayloads.PackStatus pack;
    private String name;
    private final Map<String, Integer> numbers = new LinkedHashMap<>();

    private Button hostingButton;
    private Button packButton;
    private EditBox nameBox;
    private EditBox phraseBox;
    private Button phraseSaveButton;
    private Button deleteButton;

    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listWidth;
    private int phraseScroll;
    private int selectedPhrase = -1;
    private String status = "";

    public ServerConfigScreen(Screen parent, int configVersion, ServerConfigValues values,
                              ConfigPayloads.PackStatus pack) {
        super(Component.translatable("atomchat.config.title"));
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
        this.status = Component.translatable("atomchat.config.rescanned").getString();
    }

    /** The server answered a save: close on success, explain a refusal. */
    public void onResult(boolean ok, String error) {
        if (ok) {
            if (minecraft != null) {
                minecraft.setScreen(parent);
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

        hostingButton = addRenderableWidget(Button.builder(
                        toggle("atomchat.config.hosting", hostingEnabled), b -> {
                            hostingEnabled = !hostingEnabled;
                            b.setMessage(toggle("atomchat.config.hosting", hostingEnabled));
                        })
                .bounds(left, y, colW, 18).build());
        packButton = addRenderableWidget(Button.builder(
                        toggle("atomchat.config.packs", packEnabled), b -> {
                            packEnabled = !packEnabled;
                            b.setMessage(toggle("atomchat.config.packs", packEnabled));
                        })
                .bounds(right, y, colW, 18).build());
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
        nameBox = addRenderableWidget(new EditBox(font, left + nameLabelW, y,
                width - 2 * left - nameLabelW, 18, Component.translatable("atomchat.config.name")));
        nameBox.setMaxLength(ServerConfigValues.MAX_NAME_CHARS);
        nameBox.setValue(name);
        rows.add(new Row("atomchat.config.name", left, y + 5));
        y += ROW_H + 6;

        int buttonY = height - 26;
        int inputY = buttonY - 22;
        listLeft = left;
        listWidth = width - 2 * left;
        listBottom = Math.max(y + PHRASE_ROW_H, inputY - 4);
        listTop = Math.max(y + 22, listBottom - 44);

        phraseBox = addRenderableWidget(new EditBox(font, left, inputY, listWidth - 130, 18,
                Component.translatable("atomchat.config.phrase_hint")));
        phraseBox.setMaxLength(ServerConfigValues.MAX_PHRASE_CHARS);
        phraseSaveButton = addRenderableWidget(Button.builder(Component.translatable("atomchat.config.phrase_add"),
                        b -> commitPhrase())
                .bounds(left + listWidth - 126, inputY, 60, 18).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("atomchat.config.phrase_delete"),
                        b -> deletePhrase())
                .bounds(left + listWidth - 62, inputY, 62, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("atomchat.config.rescan"), b -> rescan())
                .bounds(left + listWidth - 70, listTop - 28, 70, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("atomchat.config.save"), b -> save())
                .bounds(width / 2 - 92, buttonY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("atomchat.config.cancel"), b -> onClose())
                .bounds(width / 2 + 4, buttonY, 88, 20).build());
    }

    private void number(int x, int y, int colW, String key, String labelKey) {
        EditBox box = addRenderableWidget(new EditBox(font, x + colW - 58, y, 58, 18,
                Component.translatable(labelKey)));
        box.setMaxLength(6);
        box.setValue(String.valueOf(numbers.getOrDefault(key, 0)));
        boxes.put(key, box);
        rows.add(new Row(labelKey, x, y + 5));
    }

    private static Component toggle(String labelKey, boolean on) {
        return Component.translatable(labelKey).append(": ")
                .append(Component.translatable(on ? "atomchat.config.on" : "atomchat.config.off"));
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** A label drawn next to a widget that already knows its own position. */
    private record Row(String key, int x, int y) {
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        for (Row row : rows) {
            context.drawString(font, Component.translatable(row.key()), row.x(), row.y(), 0xC8C8C8);
        }
        context.drawString(font, packLine(), listLeft, listTop - 24, 0x9FE8FF);
        context.drawString(font, Component.translatable("atomchat.config.phrases"),
                listLeft, listTop - 11, 0xC8C8C8);
        drawPhrases(context);
        context.drawString(font, status, listLeft, height - 60, 0xFFC080);
    }

    /** Never pauses the world: this screen is opened from inside a running game. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String packLine() {
        int files = pack == null ? 0 : pack.files();
        long bytes = pack == null ? 0L : pack.bytes();
        String hash = pack == null || pack.packHash().isEmpty() ? "-" : pack.packHash().substring(0, 8);
        return tr("atomchat.config.pack_status") + " " + files + " / " + (bytes / 1024) + " KB / " + hash;
    }

    private void drawPhrases(GuiGraphics context) {
        int visible = visiblePhraseRows();
        phraseScroll = Math.max(0, Math.min(phraseScroll, Math.max(0, phrases.size() - visible)));
        if (phrases.isEmpty()) {
            context.drawString(font, Component.translatable("atomchat.config.phrase_empty"),
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
            context.drawString(font,
                    font.plainSubstrByWidth(phrases.get(index), listWidth - 8),
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
                phraseBox.setValue(phrases.get(index));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double vertical) {
        if (mx >= listLeft && mx <= listLeft + listWidth && my >= listTop && my < listBottom) {
            phraseScroll = Math.max(0, phraseScroll - (int) vertical);
            return true;
        }
        return super.mouseScrolled(mx, my, vertical);
    }

    private void commitPhrase() {
        String text = phraseBox.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        if (selectedPhrase >= 0 && selectedPhrase < phrases.size()) {
            phrases.set(selectedPhrase, text);
        } else if (phrases.size() < ServerConfigValues.MAX_PHRASES) {
            phrases.add(text);
        }
        phraseBox.setValue("");
        selectedPhrase = -1;
        status = "";
    }

    private void deletePhrase() {
        if (selectedPhrase >= 0 && selectedPhrase < phrases.size()) {
            phrases.remove(selectedPhrase);
            selectedPhrase = -1;
            phraseBox.setValue("");
            status = "";
        }
    }

    /** Asks the server to re-scan its emote folder; the answer updates the summary. */
    private void rescan() {
        ConfigPayloads.CHANNEL.sendToServer(new ConfigPayloads.Refresh());
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
        ConfigPayloads.CHANNEL.sendToServer(new ConfigPayloads.Save(configVersion, new ServerConfigValues(
                hostingEnabled, packEnabled, fileKb, totalMb, avatarMb, days, cooldown,
                packFiles, packMb, nameBox.getValue(), List.copyOf(phrases))));
        status = tr("atomchat.config.saving");
    }

    private Integer number(String key) {
        EditBox box = boxes.get(key);
        if (box == null) {
            return null;
        }
        try {
            return Integer.valueOf(box.getValue().strip());
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
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
