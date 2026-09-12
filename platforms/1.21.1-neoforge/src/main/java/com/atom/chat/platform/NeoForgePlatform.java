package com.atom.chat.platform;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge 的目录来自 FMLPaths。由本目标入口类的第一件事安装（见 AtomChat）。 */
public final class NeoForgePlatform implements Platform.Provider {

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }
}
