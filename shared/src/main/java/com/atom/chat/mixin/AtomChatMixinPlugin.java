package com.atom.chat.mixin;

import com.atom.chat.util.AwtDisplay;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Exists to run one line as early as a mod possibly can: {@link AwtDisplay#claim()}.
 *
 * <p>Mixin instantiates config plugins while it prepares configurations, which
 * is before any mod's classes are initialised and before Minecraft's own
 * {@code Main} runs its static initialiser - both of which end up writing
 * {@code java.awt.headless=true}. Whoever touches AWT first decides,
 * permanently, whether Swing windows can be created in this process, so the
 * hook is this class's static initialiser. The rest of the plugin is inert.
 *
 * <p>No behaviour here is conditional: the picker simply degrades to a logged
 * error on a machine that genuinely has no display.
 */
public class AtomChatMixinPlugin implements IMixinConfigPlugin {
    static {
        AwtDisplay.claim();
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
