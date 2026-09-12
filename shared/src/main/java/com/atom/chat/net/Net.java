package com.atom.chat.net;

import java.util.Objects;

/**
 * 发送门面：把「怎么把一条中立消息发出去」收在一处，供共享层的收发逻辑用。
 *
 * <p><strong>为什么分两侧安装。</strong>往服务端发要客户端的连接对象与通道表
 * （客户端类型，专用服务端上不该碰）；往玩家发则处处可用。两条腿各自在自己的入口装上
 * 自己那一侧：公共入口装 {@link ServerSide}（集成服务端也要用），客户端入口装
 * {@link ClientSide}。缺一侧就在调用点抛错，不给默认值 —— 默认值会让「某个目标漏装」
 * 变成跑起来才发现，与 {@code Platform}、{@link Host} 同一套规矩。
 *
 * <p><strong>玩家句柄用 {@code Object} 收。</strong>三个目标的玩家类型名互不相同（官方名与
 * Yarn 名），门面的签名里一个都不能出现；实现方按自家类型强转。这正是这一层存在的理由 ——
 * 共享层不能 import 任何原版类型（守卫「共享层依赖面」盯着这件事）。
 */
public final class Net {

    /** 客户端那一侧：往服务端发，并回答「这条通道对方认不认」。 */
    public interface ClientSide {
        void sendToServer(PackMessage.C2S message);

        /**
         * 对端没有本模组时返回 false —— 调用方据此显示「对端没有 AtomChat」而不是等超时。
         *
         * <p>探测按通道而不是按「有没有连上」：NeoForge/Fabric 上每个载荷各有自己的通道，
         * Forge 上三条共用一条，实现方按自家粒度回答。
         */
        boolean canSendToServer(PackMessage.C2S message);
    }

    /** 服务端那一侧：把消息发给某个玩家。{@code player} 是各家的服务端玩家句柄。 */
    public interface ServerSide {
        void sendToPlayer(Object player, PackMessage.S2C message);
    }

    private static volatile ClientSide client;
    private static volatile ServerSide server;

    private Net() {
    }

    public static void installClientSide(ClientSide impl) {
        client = Objects.requireNonNull(impl, "impl");
    }

    public static void installServerSide(ServerSide impl) {
        server = Objects.requireNonNull(impl, "impl");
    }

    public static void sendToServer(PackMessage.C2S message) {
        requireClient().sendToServer(message);
    }

    public static boolean canSendToServer(PackMessage.C2S message) {
        return requireClient().canSendToServer(message);
    }

    public static void sendToPlayer(Object player, PackMessage.S2C message) {
        requireServer().sendToPlayer(player, message);
    }

    private static ClientSide requireClient() {
        ClientSide impl = client;
        if (impl == null) {
            throw new IllegalStateException(
                    "Net 尚未安装（客户端侧）：客户端入口必须在最早时机调用 "
                    + "Net.installClientSide(...)。\n单测不该走到这里 —— 收发逻辑要测的部分"
                    + "请测纯转换（PackMessage.toPack / manifestOf）。");
        }
        return impl;
    }

    private static ServerSide requireServer() {
        ServerSide impl = server;
        if (impl == null) {
            throw new IllegalStateException(
                    "Net 尚未安装（服务端侧）：公共入口必须在最早时机调用 "
                    + "Net.installServerSide(...)。");
        }
        return impl;
    }
}
