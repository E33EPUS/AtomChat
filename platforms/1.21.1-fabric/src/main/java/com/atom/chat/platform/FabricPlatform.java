package com.atom.chat.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Fabric 的目录来自 FabricLoader。由本目标入口类的第一件事安装（见 AtomChat）。 */
public final class FabricPlatform implements Platform.Provider {

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }
}
