package com.atom.chat.net;

import java.util.Objects;
import java.util.UUID;

/**
 * 宿主门面：共享层的收发逻辑要问「本机」的那些事情。
 *
 * <p>与 {@link Net} 同一套理由分两侧安装。客户端侧要的是线程与当前世界（客户端类型）；
 * 服务端侧要的是玩家表、MOTD 与环境（公共类型）。缺一侧就在调用点抛错。
 *
 * <p>句柄一律 {@code Object}：玩家与服务端对象的类型名在官方名 / Yarn 两套映射下不同，
 * 共享层的签名里一个都不能出现。实现方强转，调用方只管原样传回去。
 *
 * <p><strong>只开真有调用者的口子。</strong>这里每个方法都对应一处真实的平台差异；
 * 需要新事实时再加，不预造。
 */
public final class Host {

    /** 客户端侧。 */
    public interface ClientSide {
        /**
         * 回到游戏线程执行。
         *
         * <p>网络回调不在游戏线程上，后台线程（读盘、算哈希）更不在 —— 凡是碰游戏状态的
         * 收尾都要回这一趟。
         */
        void execute(Runnable work);

        /** 当前多人服务器地址；不在多人服务器上时返回 null。 */
        String serverAddress();

        /** 当前单人存档名；没有集成服务端时返回 null。 */
        String levelName();
    }

    /** 服务端侧。 */
    public interface ServerSide {
        /**
         * 服务端玩家句柄的 UUID；传来的不是服务端玩家时返回 null。
         *
         * <p>返回 null 的语义是「这条消息没有可归属的玩家，忽略掉」—— 对应搬之前那句
         * {@code player instanceof ServerPlayer ? ... : null}。
         */
        UUID playerId(Object player);

        /** 玩家名，只用来写日志。 */
        String playerName(Object player);

        /** 按 UUID 找服务端玩家句柄；人已经不在就返回 null。 */
        Object playerById(Object server, UUID id);

        /**
         * 玩家所属的服务端句柄（集成服务端也算）。
         *
         * <p>收消息时总有玩家、也总有他所在的服务端，但「玩家 → 服务端」这一步要用到各家
         * 的类型（{@code getServer()}），所以只能在实现方做一次，而不是让九个接收点各写一遍。
         */
        Object serverOf(Object player);

        /** 服务端 MOTD；没有时返回空串（装进包身份里，所以不许返回 null）。 */
        String motd(Object server);

        /**
         * 是不是专用服务端。
         *
         * <p>决定旧的 {@code config/atomchat/emotes/} 要不要搬进服务端自己那一份：
         * 专用服务端上那些文件是服务器的，别处是玩家自己的，必须保持私有。
         */
        boolean dedicatedServer();
    }

    private static volatile ClientSide client;
    private static volatile ServerSide server;

    private Host() {
    }

    public static void installClientSide(ClientSide impl) {
        client = Objects.requireNonNull(impl, "impl");
    }

    public static void installServerSide(ServerSide impl) {
        server = Objects.requireNonNull(impl, "impl");
    }

    public static void execute(Runnable work) {
        requireClient().execute(work);
    }

    public static String serverAddress() {
        return requireClient().serverAddress();
    }

    public static String levelName() {
        return requireClient().levelName();
    }

    public static UUID playerId(Object player) {
        return requireServer().playerId(player);
    }

    public static String playerName(Object player) {
        return requireServer().playerName(player);
    }

    public static Object playerById(Object server, UUID id) {
        return requireServer().playerById(server, id);
    }

    public static Object serverOf(Object player) {
        return requireServer().serverOf(player);
    }

    public static String motd(Object server) {
        return requireServer().motd(server);
    }

    public static boolean dedicatedServer() {
        return requireServer().dedicatedServer();
    }

    private static ClientSide requireClient() {
        ClientSide impl = client;
        if (impl == null) {
            throw new IllegalStateException(
                    "Host 尚未安装（客户端侧）：客户端入口必须在最早时机调用 "
                    + "Host.installClientSide(...)。");
        }
        return impl;
    }

    private static ServerSide requireServer() {
        ServerSide impl = server;
        if (impl == null) {
            throw new IllegalStateException(
                    "Host 尚未安装（服务端侧）：公共入口必须在最早时机调用 "
                    + "Host.installServerSide(...)。");
        }
        return impl;
    }
}
