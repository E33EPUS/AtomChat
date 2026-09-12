package com.atom.chat.platform;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge 的目录来自 FMLPaths。由本目标入口类的第一件事安装（见 AtomChat）。 */
public final class NeoForgePlatform implements Platform.Provider {

    @Override
    public Path configDir() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get();
            if (dir != null) {
                return dir;
            }
        } catch (Throwable ignored) {
            // 没有可问的加载器启动（单测、工具）：退回工作目录下的常规位置。
            // 这个兜底原先写在 AtomChatServerConfig 里，现在归到「加载器事实」这一侧 ——
            // 共享层只该问路，不该知道谁会答不上来。
        }
        return Path.of("config");
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }
}
