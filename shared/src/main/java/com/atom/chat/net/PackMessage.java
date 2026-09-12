package com.atom.chat.net;

import com.atom.chat.pack.ServerPack;

import java.util.ArrayList;
import java.util.List;

/**
 * 中立消息：包分发协议里过了网的那些东西，剥掉加载器载荷的外壳。
 *
 * <p>各端的载荷记录（{@code PackPayloads}）由加载器 API 编解码、类型也各不相同，但过网的
 * 字段是同一组。共享层的收发逻辑只认这里的记录，于是它一次就能给三个目标用：平台侧只做
 * 「载荷 ↔ 中立记录」的搬运。
 *
 * <p>{@link C2S} / {@link S2C} 把方向写进类型里 —— 往服务端发的东西与服务端下发的东西不会
 * 串味，编译器管着这件事，两个方向的发送门面也各自只收本方向的类型。
 *
 * <p><strong>字节格式一个字都没搬过来</strong>：载荷的编解码留在各端（那是加载器 API），
 * 这里只描述解出来的内容。所以搬家的判据是「字节与搬之前逐字节相同」，见各端的
 * {@code PackPayloads}。
 */
public sealed interface PackMessage {

    /** 客户端发往服务端的三条。 */
    sealed interface C2S extends PackMessage {
    }

    /** 服务端发往客户端的三条。 */
    sealed interface S2C extends PackMessage {
    }

    /** 「把你的清单给我。」协议全由客户端发起。 */
    record Hello() implements C2S {
    }

    /** 「这些文件与我这边的不一样」——只许点清单里列过的名字。 */
    record Need(List<String> names) implements C2S {
    }

    /** 「我这边完事了：校验结果是这个」——服务端只拿它写日志。 */
    record Ack(boolean ok, String detail) implements C2S {
    }

    /** 客户端判断该拉哪些文件所需的全部信息，外加服务端身份。 */
    record Manifest(boolean enabled, String packHash, String serverName, byte[] icon,
                    List<PackFile> files, List<String> phrases) implements S2C {
    }

    /** 某个文件的一个切片（大小由 {@code MediaIds.CHUNK_BYTES} 定）。 */
    record Chunk(String name, int offset, int totalBytes, byte[] data) implements S2C {
    }

    /** 「你要的都在路上了」。 */
    record Done(int fileCount) implements S2C {
    }

    /** 清单里的一行。 */
    record PackFile(String name, String sha256, int size) {
    }

    /**
     * 中立清单 → 共享层的 {@link ServerPack}（客户端收到清单后走这一步）。
     *
     * <p>坏清单在这里炸（非法文件名、坏哈希、负长度、字段为 null），调用方按「协议违规」
     * 处理，而不是把半截数据存下来 —— 这些约束本来就写在 {@link ServerPack} 的构造里，
     * 三个目标共用同一份之后，对端也就共用同一套拒绝规则。
     */
    static ServerPack toPack(Manifest manifest) {
        List<ServerPack.FileEntry> files = new ArrayList<>(manifest.files().size());
        for (PackFile file : manifest.files()) {
            files.add(new ServerPack.FileEntry(file.name(), file.sha256(), file.size()));
        }
        return new ServerPack(manifest.packHash(), files, manifest.phrases(),
                manifest.serverName(), manifest.icon());
    }

    /**
     * {@link ServerPack} → 中立清单（服务端下发清单前走这一步）。
     *
     * <p>空图标统一成零长数组：{@code ServerPack} 用 null 表示「没有图标」，而载荷这一层
     * 只有字节数组。读回来时 {@code ServerPack} 再把零长数组折回 null，两边都不必猜。
     */
    static Manifest manifestOf(ServerPack pack) {
        List<PackFile> files = new ArrayList<>(pack.files().size());
        for (ServerPack.FileEntry entry : pack.files()) {
            files.add(new PackFile(entry.name(), entry.sha256Hex(), entry.size()));
        }
        byte[] icon = pack.icon();
        return new Manifest(true, pack.packHash(), pack.serverName(),
                icon == null ? new byte[0] : icon, files, pack.phrases());
    }

    /**
     * 「本服务器不提供包」的那条回执。
     *
     * <p>答一句比不作声好：客户端据此立刻显示「对端关掉了包分发」，而不是等 15 秒超时。
     */
    static Manifest disabledManifest() {
        return new Manifest(false, "", "", new byte[0], List.of(), List.of());
    }
}
