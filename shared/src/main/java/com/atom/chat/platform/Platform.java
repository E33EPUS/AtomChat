package com.atom.chat.platform;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 平台门面：把「只有加载器知道」的值收在一处，供共享层使用。
 *
 * <p>共享层不许 import 加载器 API（那属于 {@code layers/} 与 {@code platforms/}），
 * 所以凡是要问加载器的事情都走这里。每个目标在 <strong>入口类的第一件事</strong>里
 * 安装自己的实现；没装就抛错，不给默认值 —— 默认值会让「某个目标漏装」变成跑起来才发现。
 *
 * <p><strong>只开真有调用者的口子。</strong>需要新值的时候再加，不预造方法：
 * 门面每多一个方法，实现它的几个平台类就多一份要维护的东西。
 *
 * <p>为什么是静态门面而不是传参：调用点散在共享层的静态方法与类初始化里
 * （{@link com.atom.chat.util.CacheDirs} 的每个方法、配置加载），没有可传参的地方。
 * mcphone 的 {@code ModPresence} / {@code Slots} 用的是同一种形状。
 *
 * <p>注意这里接不住<strong>覆写</strong>（override）的签名差异 —— 子类的方法签名必须与父类
 * 一致，没有中间层可插；那种情况要用平台侧的抽象基类。
 */
public final class Platform {

    /** 各目标实现这一个接口。 */
    public interface Provider {
        /** 配置目录（{@code config/}）。 */
        Path configDir();

        /** 游戏目录（服务端就是服务端目录）。 */
        Path gameDir();

        /** 某个模组是否已加载（联动模组的桥接要问这一句）。 */
        boolean isModLoaded(String modId);
    }

    private static volatile Provider provider;

    private Platform() {
    }

    /**
     * 由各目标的入口类在最早时机调用。重复调用直接覆盖，所以 common 与 client 两个入口
     * 各调一次是安全的（客户端上两者都会跑，谁先谁后不必关心）。
     */
    public static void install(Provider impl) {
        provider = Objects.requireNonNull(impl, "provider");
    }

    public static boolean isInstalled() {
        return provider != null;
    }

    private static Provider require() {
        Provider impl = provider;
        if (impl == null) {
            throw new IllegalStateException(
                    "Platform 尚未安装：各目标的入口类必须在最早时机调用 Platform.install(...)。\n"
                    + "单测不该走到这里 —— 需要路径的测试请自建临时目录。");
        }
        return impl;
    }

    /** 配置目录（{@code config/}）。 */
    public static Path configDir() {
        return require().configDir();
    }

    /** 游戏目录（服务端就是服务端目录）。 */
    public static Path gameDir() {
        return require().gameDir();
    }

    /** 某个模组是否已加载。 */
    public static boolean isModLoaded(String modId) {
        return require().isModLoaded(modId);
    }
}
