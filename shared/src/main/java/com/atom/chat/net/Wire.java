package com.atom.chat.net;

import com.atom.chat.config.ServerConfigValues;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.List;

/**
 * 字节层：协议的格式规则只写一遍。
 *
 * <p>四个载荷家族以前各端各写一套编解码体 —— 同一套「字段顺序 + 长度上限 + 取值校验 +
 * null 归一」抄三遍。抄错一份的后果不是编译错，而是两个目标在线上互相看不懂，而且**各自
 * 的往返测试照样绿**（自己跟自己对得上）。所以规则收到这里，载荷那边只剩「自家类型 ↔ 中立
 * 记录」的搬运。
 *
 * <p>纯 JDK + {@code io.netty.handler.codec.DecoderException}（对端送来的东西坏了，
 * 在原版管线里这是个协议违规信号，不是普通异常）。缓冲区用什么由各目标实现
 * {@link Out} / {@link In}：NeoForge/Forge 是 {@code FriendlyByteBuf}，Fabric 是
 * {@code RegistryByteBuf}，两者的方法名还不一样（{@code writeUtf} vs {@code writeString}），
 * 这正是这一层存在的理由。
 *
 * <p><strong>字节格式一字节都不许变</strong>：{@code shared} 的 WireTest 拿改前
 * 编解码器打出来的 hex 当金标（每个家族的代表消息都有一份），平台侧的
 * {@code PackPayloadsCodecTest} / {@code ConfigPayloadsCodecTest} 再各自往返一遍。
 */
public final class Wire {
    /** 清单里允许声明的文件数；远高于服务端自己的上限，低到足以拒绝胡言乱语。 */
    public static final int MAX_FILES = 512;
    /** 清单里允许携带的常用语条数。 */
    public static final int MAX_PHRASES = 64;
    /** 配置快照里允许携带的常用语条数（与包清单是两个理由，所以是两个常量）。 */
    public static final int MAX_CONFIG_PHRASES = 64;
    /** 名字、哈希、明细串在线上允许的最长长度。 */
    private static final int MAX_NAME_CHARS = 96;
    private static final int MAX_HASH_CHARS = 80;
    private static final int MAX_DETAIL_CHARS = 120;

    private Wire() {
    }

    /** 写方向。实现方按自家缓冲区翻译这六个动作。 */
    public interface Out {
        void bool(boolean value);

        void varInt(int value);

        void varLong(long value);

        /** 无长度上限的字符串（原版 {@code writeUtf(String)} 那条路）。 */
        void string(String value);

        /** 有长度上限的字符串；超长由缓冲区自己抛错，这里不截断。 */
        void string(String value, int maxChars);

        void bytes(byte[] value);
    }

    /** 读方向。上限是**入参**：同一份字节在不同消息里允许的大小不同。 */
    public interface In {
        boolean bool();

        int varInt();

        long varLong();

        String string();

        String string(int maxChars);

        byte[] bytes();

        byte[] bytes(int maxBytes);
    }

    /** 配置屏里的包摘要：服务端此刻会发出去的东西，外加它的哈希。 */
    public record PackSummary(int files, long bytes, String packHash) {
    }

    /**
     * 中立消息 → 字节：一个消息一个入口。
     *
     * <p>这里**故意不做按类型的分派**：调用方（各端载荷的编码体）本来就知道自己是哪个
     * 类型，分派只会把「共享层必须能在 Java 17 上编译」这条约束拖进来 —— 模式匹配 switch
     * 在 17 上还是预览特性，而三端里有一个是 17。分派按方向做在平台侧（那边各有各的写法）。
     */
    public static void writeHello(Out out, PackMessage.Hello message) {
    }

    public static void writeNeed(Out out, PackMessage.Need message) {
        List<String> names = message.names() == null ? List.of() : message.names();
        out.varInt(names.size());
        for (String name : names) {
            out.string(nullToEmpty(name), MAX_NAME_CHARS);
        }
    }

    public static void writeAck(Out out, PackMessage.Ack message) {
        out.bool(message.ok());
        out.string(nullToEmpty(message.detail()), MAX_DETAIL_CHARS);
    }

    public static void writeManifest(Out out, PackMessage.Manifest message) {
        out.bool(message.enabled());
        out.string(nullToEmpty(message.packHash()), MAX_HASH_CHARS);
        out.string(nullToEmpty(message.serverName()));
        out.bytes(message.icon() == null ? new byte[0] : message.icon());
        List<PackMessage.PackFile> files = message.files() == null ? List.of() : message.files();
        out.varInt(files.size());
        for (PackMessage.PackFile file : files) {
            out.string(nullToEmpty(file.name()), MAX_NAME_CHARS);
            out.string(nullToEmpty(file.sha256()), MAX_HASH_CHARS);
            out.varInt(file.size());
        }
        List<String> phrases = message.phrases() == null ? List.of() : message.phrases();
        out.varInt(phrases.size());
        for (String phrase : phrases) {
            out.string(nullToEmpty(phrase));
        }
    }

    public static void writeChunk(Out out, PackMessage.Chunk message) {
        out.string(nullToEmpty(message.name()), MAX_NAME_CHARS);
        out.varInt(message.offset());
        out.varInt(message.totalBytes());
        out.bytes(message.data() == null ? new byte[0] : message.data());
    }

    public static void writeDone(Out out, PackMessage.Done message) {
        out.varInt(message.fileCount());
    }

    public static PackMessage.Hello readHello(In in) {
        return new PackMessage.Hello();
    }

    public static PackMessage.Need readNeed(In in) {
        int count = in.varInt();
        if (count < 0 || count > MAX_FILES) {
            throw new DecoderException("AtomChat pack need: bad file count " + count);
        }
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(in.string(MAX_NAME_CHARS));
        }
        return new PackMessage.Need(names);
    }

    public static PackMessage.Ack readAck(In in) {
        return new PackMessage.Ack(in.bool(), in.string(MAX_DETAIL_CHARS));
    }

    public static PackMessage.Manifest readManifest(In in) {
        boolean enabled = in.bool();
        String packHash = in.string(MAX_HASH_CHARS);
        String serverName = in.string();
        byte[] icon = in.bytes();
        int fileCount = in.varInt();
        if (fileCount < 0 || fileCount > MAX_FILES) {
            throw new DecoderException("AtomChat pack manifest: bad file count " + fileCount);
        }
        List<PackMessage.PackFile> files = new ArrayList<>(fileCount);
        for (int i = 0; i < fileCount; i++) {
            files.add(new PackMessage.PackFile(in.string(MAX_NAME_CHARS), in.string(MAX_HASH_CHARS),
                    in.varInt()));
        }
        int phraseCount = in.varInt();
        if (phraseCount < 0 || phraseCount > MAX_PHRASES) {
            throw new DecoderException("AtomChat pack manifest: bad phrase count " + phraseCount);
        }
        List<String> phrases = new ArrayList<>(phraseCount);
        for (int i = 0; i < phraseCount; i++) {
            phrases.add(in.string());
        }
        return new PackMessage.Manifest(enabled, packHash, serverName, icon, files, phrases);
    }

    public static PackMessage.Chunk readChunk(In in) {
        String name = in.string(MAX_NAME_CHARS);
        int offset = in.varInt();
        int total = in.varInt();
        // 切片的大小由协议定死，不接受对端说了算（读之前先校验，别先分配）。
        byte[] data = in.bytes(MediaIds.CHUNK_BYTES);
        return new PackMessage.Chunk(name, offset, total, data);
    }

    public static PackMessage.Done readDone(In in) {
        return new PackMessage.Done(in.varInt());
    }

    /** 配置值 → 字节。超长的名字与常用语在这里截断，读回来时不会再超。 */
    public static void writeValues(Out out, ServerConfigValues values) {
        out.bool(values.hostingEnabled());
        out.bool(values.packEnabled());
        out.varInt(values.maxFileKb());
        out.varInt(values.maxTotalMb());
        out.varInt(values.maxAvatarTotalMb());
        out.varInt(values.retentionDays());
        out.varInt(values.uploadCooldownMs());
        out.varInt(values.packMaxFiles());
        out.varInt(values.packMaxMb());
        out.string(truncate(values.packName(), ServerConfigValues.MAX_NAME_CHARS),
                ServerConfigValues.MAX_NAME_CHARS);
        List<String> phrases = values.phrases() == null ? List.of() : values.phrases();
        int count = Math.min(phrases.size(), MAX_CONFIG_PHRASES);
        out.varInt(count);
        for (int i = 0; i < count; i++) {
            out.string(truncate(phrases.get(i), ServerConfigValues.MAX_PHRASE_CHARS),
                    ServerConfigValues.MAX_PHRASE_CHARS);
        }
    }

    public static ServerConfigValues readValues(In in) {
        boolean hosting = in.bool();
        boolean packs = in.bool();
        int fileKb = in.varInt();
        int totalMb = in.varInt();
        int avatarMb = in.varInt();
        int days = in.varInt();
        int cooldown = in.varInt();
        int packFiles = in.varInt();
        int packMb = in.varInt();
        String name = in.string(ServerConfigValues.MAX_NAME_CHARS);
        int count = in.varInt();
        if (count < 0 || count > MAX_CONFIG_PHRASES) {
            throw new DecoderException("AtomChat config: bad phrase count " + count);
        }
        List<String> phrases = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            phrases.add(in.string(ServerConfigValues.MAX_PHRASE_CHARS));
        }
        return new ServerConfigValues(hosting, packs, fileKb, totalMb, avatarMb, days, cooldown,
                packFiles, packMb, name, phrases);
    }

    public static void writePackSummary(Out out, PackSummary summary) {
        out.varInt(summary.files());
        out.varLong(summary.bytes());
        out.string(nullToEmpty(summary.packHash()), MAX_HASH_CHARS);
    }

    public static PackSummary readPackSummary(In in) {
        return new PackSummary(in.varInt(), in.varLong(), in.string(MAX_HASH_CHARS));
    }

    /** 线上的字符串永远不是 null：截断到上限，null 折成空串。 */
    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
