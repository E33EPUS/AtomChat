package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * One colour row in a settings section: a title, a fixed preset palette, and a
 * live binding to a config field. Like {@link SettingsItem}, applying a colour
 * writes {@code atomchat-client.json} immediately.
 *
 * <p>Presets instead of a free-form hex input: the chat field is the only text
 * entry surface in this UI and routing a modal editor through it is out of
 * proportion — eight curated swatches plus the current value cover the need.
 * A current value outside the palette still renders (and is selectable), so a
 * hand-edited config file is never punished.
 */
public final class SettingsColor {
    private final String id;
    private final String titleKey;
    private final int[] presets;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public SettingsColor(String id, String titleKey, int[] presets,
                         IntSupplier getter, IntConsumer setter) {
        this.id = id;
        this.titleKey = titleKey;
        this.presets = presets;
        this.getter = getter;
        this.setter = setter;
    }

    public String id() {
        return id;
    }

    public String titleKey() {
        return titleKey;
    }

    public int[] presets() {
        return presets;
    }

    public int value() {
        return getter.getAsInt();
    }

    /** Number of swatches: the presets plus the live value when it is foreign. */
    public int swatchCount() {
        return presets.length + (presetIndex(value()) < 0 ? 1 : 0);
    }

    /** Swatch colour at {@code index}; the tail slot is the live foreign value. */
    public int swatchColor(int index) {
        if (index < presets.length) {
            return presets[index];
        }
        return value();
    }

    public int presetIndex(int color) {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == color) {
                return i;
            }
        }
        return -1;
    }

    /** Applies the colour and writes {@code atomchat-client.json} immediately. */
    public void apply(int color) {
        setter.accept(color);
        AtomChatConfig.save(AtomChatConfig.get());
    }
}
