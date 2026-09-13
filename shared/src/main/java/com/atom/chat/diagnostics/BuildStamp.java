package com.atom.chat.diagnostics;

import java.io.InputStream;
import java.util.Properties;

/**
 * 构建戳：**构建那一刻**的仓库事实（版本号 / 构建目标 / 提交号 / 树是否脏）。
 *
 * <p>产自 {@code gradle/atomchat-layers.gradle} 的 3c 节，落在 jar 根的
 * {@code atomchat-build.properties}。
 *
 * <p><strong>为什么要它</strong>：玩家报 bug 时给的是一份日志，而日志里的
 * {@code v0.2.11} 可能来自任何一次构建。实测发生过「jar 文件名写 0.2.8、里面的版本号
 * 是 0.2.7」这种对不上的情况 —— 光看日志无从判断交上来的是哪一个产物。有了构建戳，
 * 版本号（加载器元数据）+ 提交号（构建源）+ 产物 sha256（文件本体）三者可以互相印证。
 *
 * <p><strong>读不到就退回 {@link #UNKNOWN}，绝不抛</strong>：单测、IDE 直接跑、
 * 或者哪天这个资源没打进 jar，都不该让游戏起不来 —— 这一行日志的价值远低于启动本身。
 */
public final class BuildStamp {
    /** 缺失时的占位值。故意是全小写的普通词，一眼能看出不是真值。 */
    public static final String UNKNOWN = "unknown";

    private static final String RESOURCE = "/atomchat-build.properties";
    private static volatile BuildStamp instance;

    private final String version;
    private final String target;
    private final String minecraft;
    private final String loader;
    private final String loaderVersion;
    private final String gitCommit;
    private final boolean gitDirty;

    private BuildStamp(String version, String target, String minecraft, String loader,
                       String loaderVersion, String gitCommit, boolean gitDirty) {
        this.version = version;
        this.target = target;
        this.minecraft = minecraft;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
        this.gitCommit = gitCommit;
        this.gitDirty = gitDirty;
    }

    /** 进程内缓存一次：资源不会在运行期变，且这一路上会碰类加载器。 */
    public static BuildStamp get() {
        BuildStamp local = instance;
        if (local == null) {
            local = load();
            instance = local;
        }
        return local;
    }

    private static BuildStamp load() {
        try (InputStream in = BuildStamp.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return missing();
            }
            Properties props = new Properties();
            props.load(in);
            return from(props);
        } catch (Throwable t) {
            return missing();
        }
    }

    static BuildStamp from(Properties props) {
        return new BuildStamp(
                value(props, "version"),
                value(props, "target"),
                value(props, "minecraft"),
                value(props, "loader"),
                value(props, "loader_version"),
                value(props, "git_commit"),
                "true".equals(value(props, "git_dirty")));
    }

    private static BuildStamp missing() {
        return new BuildStamp(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, false);
    }

    /**
     * 没展开的模板值（{@code ${version}}）与空值都算没读到。放它过去的话，日志里会
     * 出现一行看起来像真的、其实什么都没说的版本号 —— 那比 {@code unknown} 更坏。
     */
    private static String value(Properties props, String key) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return UNKNOWN;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("${")) {
            return UNKNOWN;
        }
        return trimmed;
    }

    public String version() {
        return version;
    }

    public String target() {
        return target;
    }

    public String minecraft() {
        return minecraft;
    }

    public String loader() {
        return loader;
    }

    public String loaderVersion() {
        return loaderVersion;
    }

    /** 短提交号；读不到是 {@link #UNKNOWN}。 */
    public String gitCommit() {
        return gitCommit;
    }

    /** 构建时工作区是否有【已跟踪文件】被改动。构建产物与本地日志不算。 */
    public boolean gitDirty() {
        return gitDirty;
    }

    /** 形如 {@code 56122d9+dirty}；脏标记只在有提交号时才有意义。 */
    public String git() {
        if (UNKNOWN.equals(gitCommit)) {
            return UNKNOWN;
        }
        return gitDirty ? gitCommit + "+dirty" : gitCommit;
    }

    /** 形如 {@code Forge 47.4.10 / minecraft 1.20.1}，构成目标那一栏。 */
    public String platform() {
        return loader + " " + loaderVersion + " / minecraft " + minecraft;
    }
}
