package com.atom.chat.diagnostics;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.wallpaper.WallpaperStore;
import org.lwjgl.opengl.GL11C;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动时的环境摘要：**一行构建身份**永远打，**一整块环境事实**只在 {@code debug=true} 时打。
 *
 * <p><strong>为什么不能只有版本号</strong>：玩家交上来的日志里，
 * {@code "AtomChat client initialized"} 一个版本号都没带；即使带上 {@code 0.2.11}，
 * 也不知道那是哪一次构建。而"实际加载到的那份 FlatLaf 是从哪个 jar 里来的"、
 * "Skija 的原生库到底加载成功没有"、"GPU 是什么"这些恰恰是最常被追问、又最难从
 * 日志里推出来的三条。
 *
 * <p><strong>为什么身份行不受 debug 开关约束</strong>：需要它的正是
 * "玩家没开 debug 就报了个 bug" 的场景 —— 把唯一的定位手段藏在开关后面，等于没有。
 * 它只有一行，代价是每次启动一行日志。
 *
 * <p><strong>为什么整块一次打完（而不是一行一条日志记录）</strong>：别的模组的日志会插在
 * 两条记录之间，玩家复制出来就是散的。一条带换行的记录复制下来是完整的一块。
 *
 * <p><strong>它是旁观者</strong>：所有探测都在 try/catch 里，失败只影响这一行日志。
 * 诊断代码把游戏搞崩是绝对不能接受的 —— 那比没有诊断还坏。
 */
public final class EnvironmentSummary {

    /** 我们内嵌的那份 FlatLaf（重定位后）。见 third_party/ 与 atomchat-layers.gradle 的 3b 节。 */
    private static final String SHADED_LAF = "com.atom.chat.shaded.flatlaf.FlatLightLaf";
    /** 公共包名。别的模组内嵌的 FlatLaf 住在这里 —— 重定位就是为了不再和它撞车。 */
    private static final String PUBLIC_LAF = "com.formdev.flatlaf.FlatLightLaf";
    private static final String SKIJA_LIBRARY = "io.github.humbleui.skija.impl.Library";
    private static final String SKIJA_OS_ENUM = "io.github.humbleui.skija.impl.OperatingSystem";
    private static final String SKIJA_ARCH_ENUM = "io.github.humbleui.skija.impl.Architecture";

    /** 身份行打一次。 */
    private static final AtomicBoolean IDENTITY_DONE = new AtomicBoolean();
    /**
     * 详细块打一次。与身份行分开计数：玩家在游戏里把 debug 打开（配置实例当场生效）
     * 之后，下一 tick 就能拿到这块，不必重启 —— 而重启一次对排查者是实打实的成本。
     */
    private static final AtomicBoolean DEBUG_BLOCK_DONE = new AtomicBoolean();

    /**
     * <strong>只用 ASCII</strong>。日志文件是按平台默认编码写的（中文 Windows 上实测是
     * GBK），而这份日志会被贴进 UTF-8 的 issue 里 —— 一个破折号过去就变成乱码。
     * 这些字符串要一起交给别人读，就不能带任何本地字符集里的东西。
     */
    static final String SUMMARY_HEADER =
            "AtomChat environment summary (debug) - paste this whole block into a bug report";

    private EnvironmentSummary() {
    }

    /**
     * 客户端每 tick 调一次，内部只放行一次。
     *
     * <p>为什么挂在 tick 上而不是客户端初始化：GPU 那三行要一个【当前的 GL 上下文】，
     * 只有渲染线程上有；而 tick 恰好跑在渲染线程上、又已经晚于窗口创建。放在更早的
     * 初始化钩子里，三个加载器的时机各不相同，Fabric 的客户端入口更是在窗口出现之前。
     *
     * @param reportedVersion 加载器元数据里的版本号（各平台 {@code AtomChat.version()}）
     * @param artifact        本模组的 jar 路径，取不到就给 null
     */
    public static void logOnce(String reportedVersion, Path artifact) {
        try {
            if (IDENTITY_DONE.compareAndSet(false, true)) {
                AtomChat.LOGGER.info(identityLine(reportedVersion, artifact));
            }
            AtomChatConfig config = AtomChatConfig.get();
            if (config.debug && DEBUG_BLOCK_DONE.compareAndSet(false, true)) {
                AtomChat.LOGGER.info(debugBlock(reportedVersion, artifact, config, EnvironmentSummary::gpuLine));
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn("AtomChat environment summary failed", t);
        }
    }

    /** 一行构建身份。版本号（加载器）+ 产物名 + 内容哈希 + 提交号，四者互相印证。 */
    static String identityLine(String reportedVersion, Path artifact) {
        StringBuilder sb = new StringBuilder("AtomChat build ");
        sb.append(text(reportedVersion)).append(" | ").append(BuildStamp.get().platform());
        sb.append(" | ").append(artifactName(artifact));
        String hash = sha256Prefix(artifact, 12);
        if (hash != null) {
            sb.append(" | sha256:").append(hash);
        }
        sb.append(" | git ").append(BuildStamp.get().git());
        return sb.toString();
    }

    /**
     * 详细环境块。刻意逐条独立取：任何一条取不到都只让它自己变成
     * {@code unavailable(...)}，其余照打 —— 一片"取不到"的日志没有价值。
     *
     * <p>配置与 GPU 探针都是**传进来的**而不是在里面取：这个类在单测里也要能整块拼出来
     * （{@code AtomChatConfig.get()} 要走平台门面拿配置目录，单测里没有加载器可问；
     * GPU 探针更硬 —— 见 {@link #gpuLine()}）。一个要装全局状态、或者会带走测试 JVM
     * 的诊断类，最后就是没人测。
     */
    static String debugBlock(String reportedVersion, Path artifact, AtomChatConfig config,
                             java.util.function.Supplier<String> gpu) {
        BuildStamp stamp = BuildStamp.get();
        ClassLoader loader = EnvironmentSummary.class.getClassLoader();
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("build", stamp.version() + " | " + stamp.platform() + " | git " + stamp.git());
        rows.put("artifact", artifactLine(artifact, reportedVersion));
        rows.put("runtime", runtimeLine());
        rows.put("laf", lafLine(loader));
        rows.put("skija", skijaLine(loader));
        rows.put("gpu", gpu.get());
        rows.put("switches", switchesLine(config));

        StringBuilder sb = new StringBuilder(SUMMARY_HEADER);
        int width = keyWidth(rows.keySet());
        for (Map.Entry<String, String> row : rows.entrySet()) {
            sb.append('\n').append("  ").append(padRight(row.getKey(), width)).append(" ")
                    .append(row.getValue());
        }
        return sb.toString();
    }

    private static String artifactLine(Path artifact, String reportedVersion) {
        if (artifact == null) {
            return "unavailable (the loader did not report a path) | loader reports "
                    + text(reportedVersion);
        }
        String name = artifactName(artifact);
        String hash = sha256Prefix(artifact, 12);
        return artifact + " | " + name + (hash == null ? "" : " | sha256:" + hash)
                + " | loader reports " + text(reportedVersion);
    }

    private static String runtimeLine() {
        return "Java " + prop("java.version") + " (" + prop("java.vendor") + ") | "
                + prop("os.name") + " " + prop("os.version") + " | " + prop("os.arch");
    }

    /**
     * 我们这份 FlatLaf 能不能从自己的类加载器里解析出来、来自哪个文件。
     *
     * <p><strong>只查解析，不实例化</strong>：{@code UIManager} 是进程级全局状态，
     * 让它在这里被碰到，就等于把"谁先装 LAF"这件事提前到了启动期 —— 那是另一个
     * 模组的窗口和我们抢外观的开始，不是这一版要做的事。真正的安装与它打出的类名
     * 在 {@code FilePicker.installLookAndFeel()} 里，用到的时候自然会有。
     *
     * <p>反过来，公共包名在这里是**查得到也照样报**：重定位之后我们不再引用它，
     * 但它可见与否直接解释了"隔壁模组的内嵌 FlatLaf 会不会再影响我们"，是排查
     * 这类撞车时最该先看到的一行。
     */
    private static String lafLine(ClassLoader loader) {
        StringBuilder sb = new StringBuilder();
        Class<?> own = tryLoad(SHADED_LAF, loader);
        if (own == null) {
            // 这是真的异常：重定位产物没进来，选择器会退成系统外观。
            AtomChat.LOGGER.warn("The bundled {}(relocated) FlatLaf is not on the classpath; "
                    + "the file picker will fall back to the system look and feel", SHADED_LAF);
            sb.append(SHADED_LAF).append(" NOT RESOLVABLE");
        } else {
            sb.append(SHADED_LAF).append(" <- ").append(originOf(own));
        }
        Class<?> foreign = tryLoad(PUBLIC_LAF, loader);
        if (foreign == null) {
            sb.append(" | public com.formdev.flatlaf not visible");
        } else {
            sb.append(" | public com.formdev.flatlaf visible from ").append(originOf(foreign))
                    .append(" (unused by us)");
        }
        return sb.toString();
    }

    /**
     * Skija 原生库：命中哪一个原生包、以及**真的加载成功没有**。
     *
     * <p>加载是被主动触发的（{@code Library.staticLoad()}），不是"本来就会发生"——
     * 原生库平时要到第一次开面板才加载，而排查需要的是"这次启动到底行不行"。
     * 失败在这里被抓住并降级成一行日志，游戏照常起：和玩家开面板时才失败相比，
     * 只是把同一件事提前说清楚，没有别的行为变化。
     */
    private static String skijaLine(ClassLoader loader) {
        StringBuilder sb = new StringBuilder();
        String dir = skijaResourceDir(loader);
        if (dir == null) {
            sb.append("platform unknown (Skija platform classes not resolvable)");
        } else {
            List<URL> copies = allResources(loader, dir + "skija.version");
            if (copies.isEmpty()) {
                sb.append("no native bundled for ").append(dir);
            } else {
                sb.append(text(readResource(copies.get(0)))).append(' ')
                        .append(dir.substring(0, dir.length() - 1).replace('/', '.'))
                        .append(" <- ").append(originOfUrl(copies.get(0)));
                if (copies.size() > 1) {
                    sb.append(" (").append(copies.size()).append(" copies on the classpath)");
                }
            }
        }

        long startNs = System.nanoTime();
        try {
            Class<?> library = Class.forName(SKIJA_LIBRARY, true, loader);
            library.getMethod("staticLoad").invoke(null);
            boolean loaded = (Boolean) library.getField("_loaded").get(null);
            long ms = (System.nanoTime() - startNs) / 1_000_000L;
            sb.append(" | natives ").append(loaded ? "loaded" : "NOT loaded").append(" in ")
                    .append(ms).append(" ms");
            if (!loaded) {
                AtomChat.LOGGER.warn("Skija native library did not load; the chat panel cannot "
                        + "render on this platform. The bundled native supports Windows x64 "
                        + "(Linux / macOS builds are not shipped yet).");
            }
        } catch (Throwable t) {
            sb.append(" | natives FAILED: ").append(describe(t));
            AtomChat.LOGGER.warn("Skija native library failed to load; the chat panel cannot "
                    + "render on this platform. The bundled native supports Windows x64 "
                    + "(Linux / macOS builds are not shipped yet).", t);
        }
        return sb.toString();
    }

    /** Skija 自己按 {@code io.github.humbleui.skija.<os>.<arch>/} 找原生库，这里照抄它的规则。 */
    private static String skijaResourceDir(ClassLoader loader) {
        String os = enumName(SKIJA_OS_ENUM, loader);
        String arch = enumName(SKIJA_ARCH_ENUM, loader);
        if (os == null || arch == null) {
            return null;
        }
        return "io/github/humbleui/skija/" + os + "/" + arch + "/";
    }

    /**
     * GPU 三串（渲染器 / 驱动版本 / 厂商）。
     *
     * <p><strong>只能在有当前 GL 上下文的线程上调用</strong>（客户端 tick 就是那个线程：
     * 三个加载器的 tick 都跑在渲染线程上）。这不是"最好这样"，是硬约束：
     * 没有上下文时 {@code glGetString} 不是抛异常，而是**把整个 JVM 带走** ——
     * 实测在单测里调它，直接在 {@code lwjgl_opengl.dll} 里 SIGSEGV，
     * 连 hs_err 都写出来了。{@code try/catch} 挡不住段错误，所以这里只留
     * "调用者保证"这一道，并把探针做成可注入的（{@link #debugBlock}），
     * 让单测根本走不到它。
     */
    private static String gpuLine() {
        try {
            return text(GL11C.glGetString(GL11C.GL_RENDERER)) + " | "
                    + text(GL11C.glGetString(GL11C.GL_VERSION)) + " | "
                    + text(GL11C.glGetString(GL11C.GL_VENDOR));
        } catch (Throwable t) {
            return "unavailable (" + describe(t) + ")";
        }
    }

    private static String switchesLine(AtomChatConfig config) {
        return "blur=" + onOff(config.blurEnabled) + " (opacity " + config.panelOpacity + ")"
                + " | wallpaper=" + (WallpaperStore.isSet() ? "set" : "none")
                + " | imageMessages=" + onOff(config.imageMessagesEnabled)
                + " | animations=" + onOff(config.animationEnabled)
                + " | panel=" + (int) config.panelWidth + "x" + (int) config.panelHeight
                + " @" + config.uiScale + "x"
                + " | debug=" + onOff(config.debug);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static Class<?> tryLoad(String name, ClassLoader loader) {
        try {
            // initialize=false：只问"能不能解析到、来自哪里"。跑静态初始化会碰 AWT，
            // 见 lafLine 的说明。
            return Class.forName(name, false, loader);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 类的来源文件；解析不到就给类加载器的名字，总比什么都不给强。 */
    private static String originOf(Class<?> type) {
        try {
            java.security.CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                return source.getLocation().toString();
            }
        } catch (Throwable ignored) {
        }
        ClassLoader loader = type.getClassLoader();
        return loader == null ? "bootstrap" : String.valueOf(loader);
    }

    /** 资源 URL 两种形态（{@code jar:file:/...!/path}、{@code file:/.../path}）都还原成文件。 */
    private static String originOfUrl(URL url) {
        try {
            return new java.io.File(url.toURI()).toString();
        } catch (Throwable ignored) {
        }
        return String.valueOf(url);
    }

    private static String enumName(String className, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(className, true, loader);
            Object current = type.getField("CURRENT").get(null);
            if (current instanceof Enum<?> value) {
                return value.name().toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<URL> allResources(ClassLoader loader, String path) {
        List<URL> found = new ArrayList<>();
        try {
            Enumeration<URL> urls = loader.getResources(path);
            while (urls.hasMoreElements()) {
                found.add(urls.nextElement());
            }
        } catch (Throwable ignored) {
        }
        return found;
    }

    private static String readResource(URL url) {
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return "unreadable";
        }
    }

    private static String artifactName(Path artifact) {
        if (artifact == null) {
            return "artifact unknown";
        }
        Path name = artifact.getFileName();
        return name == null ? artifact.toString() : name.toString();
    }

    /**
     * 产物的内容哈希（前若干位十六进制）。这是"文件本体"的身份：文件名可以改、可以被
     * 玩家重命名成任何样子，内容改不了。读不到（不存在、无权限、是目录）就给 null。
     */
    static String sha256Prefix(Path artifact, int chars) {
        if (artifact == null) {
            return null;
        }
        try {
            if (!Files.isRegularFile(artifact)) {
                return null;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            try (InputStream in = Files.newInputStream(artifact)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(chars);
            for (int i = 0; i < chars && i < hash.length * 2; i++) {
                hex.append(Character.forDigit((hash[i / 2] >> (4 * (1 - i % 2))) & 0xF, 16));
            }
            return hex.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String prop(String key) {
        return text(System.getProperty(key));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank()
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;
    }

    /** 结论值左对齐用的列宽，由最长的键决定，不写死。 */
    private static int keyWidth(Iterable<String> keys) {
        int width = 0;
        for (String key : keys) {
            width = Math.max(width, key.length());
        }
        return width;
    }

    private static String padRight(String value, int width) {
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
