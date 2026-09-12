package com.atom.chat.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * Translates {@link Wire}'s neutral byte actions into Forge's buffer.
 *
 * <p>Forge 1.20.1 uses the same spellings as NeoForge ({@code writeUtf} /
 * {@code readUtf}); the file is separate because each target owns its own copy of
 * the platform layer, not because the calls differ.
 */
final class ForgeWireIo implements Wire.Out, Wire.In {
    private final FriendlyByteBuf buf;

    ForgeWireIo(FriendlyByteBuf buf) {
        this.buf = Objects.requireNonNull(buf, "buf");
    }

    @Override
    public void bool(boolean value) {
        buf.writeBoolean(value);
    }

    @Override
    public void varInt(int value) {
        buf.writeVarInt(value);
    }

    @Override
    public void varLong(long value) {
        buf.writeVarLong(value);
    }

    @Override
    public void string(String value) {
        buf.writeUtf(value);
    }

    @Override
    public void string(String value, int maxChars) {
        buf.writeUtf(value, maxChars);
    }

    @Override
    public void bytes(byte[] value) {
        buf.writeByteArray(value);
    }

    @Override
    public boolean bool() {
        return buf.readBoolean();
    }

    @Override
    public int varInt() {
        return buf.readVarInt();
    }

    @Override
    public long varLong() {
        return buf.readVarLong();
    }

    @Override
    public String string() {
        return buf.readUtf();
    }

    @Override
    public String string(int maxChars) {
        return buf.readUtf(maxChars);
    }

    @Override
    public byte[] bytes() {
        return buf.readByteArray();
    }

    @Override
    public byte[] bytes(int maxBytes) {
        return buf.readByteArray(maxBytes);
    }
}
