package com.atom.chat.net;

import net.minecraft.network.RegistryByteBuf;

import java.util.Objects;

/**
 * Translates {@link Wire}'s neutral byte actions into Fabric's buffer.
 *
 * <p>Fabric's spelling differs from the other two targets ({@code writeString} /
 * {@code readString} instead of {@code writeUtf} / {@code readUtf}); the bytes on
 * the wire are the same, which the shared WireTest pins down with goldens.
 */
final class FabricWireIo implements Wire.Out, Wire.In {
    private final RegistryByteBuf buf;

    FabricWireIo(RegistryByteBuf buf) {
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
        buf.writeString(value);
    }

    @Override
    public void string(String value, int maxChars) {
        buf.writeString(value, maxChars);
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
        return buf.readString();
    }

    @Override
    public String string(int maxChars) {
        return buf.readString(maxChars);
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
