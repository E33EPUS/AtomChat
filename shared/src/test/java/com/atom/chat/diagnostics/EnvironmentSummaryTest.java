package com.atom.chat.diagnostics;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 环境摘要：一块日志，两条规矩 —— 数字得是真的，拼不出来也不许抛。
 */
class EnvironmentSummaryTest {

    /** sha256("abc")，取前 12 位十六进制。常量写死，不拿被测代码算期望值。 */
    private static final String SHA256_ABC_12 = "ba7816bf8f01";
    /** sha256("")，同上。 */
    private static final String SHA256_EMPTY_12 = "e3b0c44298fc";

    private static Path write(@TempDir Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * The GPU probe is injected on purpose: reading GL strings without a current
     * context does not throw, it takes the whole JVM down inside
     * lwjgl_opengl.dll (a test of ours did exactly that). So the block is built
     * here with a stub probe — and the stub is also how we prove the row is
     * wired rather than hardcoded.
     */
    private static final java.util.function.Supplier<String> STUB_GPU = () -> "Stub GPU | 4.6 | Stub Vendor";

    private static String block(String reportedVersion, Path artifact, AtomChatConfig config) {
        return EnvironmentSummary.debugBlock(reportedVersion, artifact, config, STUB_GPU);
    }

    @Test
    void hashesArtifactContents(@TempDir Path dir) throws Exception {
        assertEquals(SHA256_ABC_12, EnvironmentSummary.sha256Prefix(write(dir, "abc.jar", "abc"), 12));
        assertEquals(SHA256_EMPTY_12, EnvironmentSummary.sha256Prefix(write(dir, "empty.jar", ""), 12));
    }

    /** 名字可以改，哈希不该因为"换个名字"就变。 */
    @Test
    void hashIgnoresTheFileName(@TempDir Path dir) throws Exception {
        Path first = write(dir, "atomchat-0.2.11.jar", "abc");
        Path second = write(dir, "renamed-by-the-user.jar", "abc");
        assertEquals(EnvironmentSummary.sha256Prefix(first, 12),
                EnvironmentSummary.sha256Prefix(second, 12));
    }

    @Test
    void unreadableArtifactYieldsNoHash(@TempDir Path dir) {
        assertNull(EnvironmentSummary.sha256Prefix(null, 12), "没有路径就不该有哈希");
        assertNull(EnvironmentSummary.sha256Prefix(dir.resolve("nope.jar"), 12), "文件不存在");
        assertNull(EnvironmentSummary.sha256Prefix(dir, 12), "目录不是产物");
    }

    @Test
    void identityLineCarriesVersionArtifactAndHash(@TempDir Path dir) throws Exception {
        Path artifact = write(dir, "atomchat-Forge-1.20.1-0.2.11.jar", "abc");
        String line = EnvironmentSummary.identityLine("0.2.11", artifact);
        assertTrue(line.startsWith("AtomChat build 0.2.11 | "), line);
        assertTrue(line.contains("atomchat-Forge-1.20.1-0.2.11.jar"), line);
        assertTrue(line.contains("sha256:" + SHA256_ABC_12), line);
        assertTrue(line.contains("| git "), line);
    }

    /**
     * 加载器报的版本号和构建戳里的版本号是两份独立的证据，**都要打出来** ——
     * "文件名 0.2.8 / 内部 0.2.7"那类报告正是靠它们对不上才被发现的。
     */
    @Test
    void identityLineCarriesTheLoadersVersionToo(@TempDir Path dir) throws Exception {
        Path artifact = write(dir, "atomchat.jar", "abc");
        assertTrue(EnvironmentSummary.identityLine("0.2.7", artifact).startsWith("AtomChat build 0.2.7"));
    }

    @Test
    void identityLineSurvivesAMissingArtifact() {
        String line = EnvironmentSummary.identityLine("0.2.11", null);
        assertTrue(line.startsWith("AtomChat build 0.2.11 | "), line);
        assertFalse(line.contains("sha256:"), "没有文件就不该有哈希: " + line);
        assertFalse(line.contains("null"), "不许把 null 打进日志: " + line);
    }

    @Test
    void debugBlockHasEveryRow(@TempDir Path dir) throws Exception {
        Path artifact = write(dir, "atomchat-Forge-1.20.1-0.2.11.jar", "abc");
        String block = block("0.2.11", artifact, new AtomChatConfig());

        assertTrue(block.startsWith("AtomChat environment summary (debug)"), block);
        for (String key : new String[]{"build", "artifact", "runtime", "laf", "skija", "gpu", "switches"}) {
            assertTrue(block.contains("\n  " + key), "缺少 " + key + " 行: " + block);
        }
        // 换行都在这【一条】记录里：别的模组的日志插不进这块，玩家复制下来是完整的。
        assertTrue(block.lines().count() >= 8, block);
        assertFalse(block.contains("null"), block);
    }

    /**
     * 开关那一行是唯一会随玩家设置变的部分，所以它必须真的跟着设置走 ——
     * "日志说开着、实际关着"比不打这一行更坏。
     */
    @Test
    void switchesRowFollowsTheConfig(@TempDir Path dir) throws Exception {
        AtomChatConfig config = new AtomChatConfig();
        assertTrue(block("0.2.11", write(dir, "a.jar", "x"), config).contains("imageMessages=on"));

        config.imageMessagesEnabled = false;
        config.blurEnabled = false;
        config.debug = true;
        String block = block("0.2.11", write(dir, "a.jar", "x"), config);
        assertTrue(block.contains("imageMessages=off"), block);
        assertTrue(block.contains("blur=off"), block);
        assertTrue(block.contains("debug=on"), block);
    }

    /** GPU 那一行走的是注入进去的探针，不是写死的，也不是在这里真的去碰 GL。 */
    @Test
    void gpuRowComesFromTheInjectedProbe(@TempDir Path dir) throws Exception {
        String block = block("0.2.11", write(dir, "a.jar", "x"), new AtomChatConfig());
        assertNotNull(block);
        assertTrue(block.contains("Stub GPU | 4.6 | Stub Vendor"), block);
    }

    /**
     * 我们自己写死的那部分文本必须是纯 ASCII。
     *
     * <p>这不是洁癖：日志文件按平台默认编码写（中文 Windows 上实测是 GBK），而这份日志
     * 会被贴进 UTF-8 的 issue。第一版里标题带了个破折号，在真机日志里就成了两个字节的
     * GBK —— 贴出去是乱码。玩家自己的路径/设备名带非 ASCII 是另一回事，那不是我们能管的。
     */
    @Test
    void ourOwnLogTextIsAsciiOnly() {
        assertTrue(EnvironmentSummary.SUMMARY_HEADER.chars().allMatch(c -> c < 0x80),
                EnvironmentSummary.SUMMARY_HEADER);
        // 没有产物路径时，身份行整行都由我们自己的文本组成。
        assertTrue(EnvironmentSummary.identityLine("0.2.11", null).chars().allMatch(c -> c < 0x80),
                EnvironmentSummary.identityLine("0.2.11", null));
    }

    /** Skija 自己按这个目录找原生库；三端算出来的值一样。 */
    private static final String DIR = "io/github/humbleui/skija/windows/x64/";

    /**
     * 三端的差异喂不进单测，但**规矩**可以：这一行永远不许自相矛盾。
     *
     * <p>现场：0.2.11 在 NeoForge 上打出过同一行里既说 "no native bundled for
     * io/github/humbleui/skija/windows/x64/"、末尾又写 "natives loaded in 181 ms" ——
     * 前半句是"加载器的资源枚举没找到"被错写成了"没打包"。玩家把这一块贴进 issue，
     * 排查方向就被带到"打包坏了"上去，而那辆车上没有零件坏。
     */
    @Test
    void skijaRowNeverClaimsNoNativeWhenTheNativesLoaded() {
        String line = EnvironmentSummary.skijaSummary(DIR, List.of(), null, true, 51, null);
        assertFalse(line.contains("no native bundled"), line);
        assertFalse(line.contains("no native"), "加载成功就不该出现任何'没有原生库'的说法: " + line);
        assertTrue(line.contains("natives loaded in 51 ms"), line);
        assertTrue(line.contains("io.github.humbleui.skija.windows.x64"), line);
    }

    /** 反过来也要立住：这句话只有在加载**确实失败**时才许出现。 */
    @Test
    void skijaRowSaysNoNativeOnlyWhenTheLoadFailed() {
        String line = EnvironmentSummary.skijaSummary(DIR, List.of(), null, false, 12,
                "UnsatisfiedLinkError: no skija in java.library.path");
        assertTrue(line.contains("no native bundled for " + DIR), line);
        assertTrue(line.contains("natives FAILED"), line);
    }

    /**
     * 资源枚举看不到嵌套资源时的退路：直接翻我们自己的产物，把真版本读出来。
     * NeoForge 的 jarjar union 就是这个现场（Forge 的 JarJar 看得到、Fabric 的 include 摊平）。
     */
    @Test
    void skijaRowFallsBackToOurOwnArtifact(@TempDir Path dir) throws Exception {
        Path artifact = artifactWithNestedSkija(dir, "0.116.8");
        String[] nested = EnvironmentSummary.nestedNativeVersion(artifact, DIR);
        assertNotNull(nested, "嵌套的原生包应该被读到");
        assertEquals("0.116.8", nested[0]);
        assertEquals("META-INF/jarjar/skija-windows-x64-0.116.8.jar", nested[1]);

        String line = EnvironmentSummary.skijaSummary(DIR, List.of(), nested, true, 181, null);
        assertTrue(line.contains("0.116.8"), line);
        assertTrue(line.contains("skija-windows-x64-0.116.8.jar"), line);
        assertFalse(line.contains("no native"), line);
        assertFalse(line.contains("not readable"), line);
    }

    /** 产物里没有嵌套原生包时，不许编一个出来。 */
    @Test
    void nestedLookupFindsNothingInAPlainJar(@TempDir Path dir) throws Exception {
        Path plain = write(dir, "atomchat.jar", "not a zip at all");
        assertNull(EnvironmentSummary.nestedNativeVersion(plain, DIR));
        assertNull(EnvironmentSummary.nestedNativeVersion(null, DIR));
        assertNull(EnvironmentSummary.nestedNativeVersion(plain, null));
    }

    /** 命中 classpath 上的版本文件时走原来那条路，不再去找嵌套包（Forge / Fabric 的现场）。 */
    @Test
    void skijaRowKeepsTheClasspathCopyWhenResolvable(@TempDir Path dir) throws Exception {
        Path version = write(dir, "skija.version", "0.116.8\n");
        String line = EnvironmentSummary.skijaSummary(DIR, List.of(version.toUri().toURL()),
                null, true, 105, null);
        assertTrue(line.contains("0.116.8"), line);
        assertTrue(line.contains("skija.version"), line);
        assertFalse(line.contains("in our own artifact"), line);
        assertFalse(line.contains("no native"), line);
    }

    /** 新加的这几句同样必须是纯 ASCII —— 理由见上一个测试。 */
    @Test
    void everySkijaVariantIsAsciiOnly() {
        for (String line : new String[]{
                EnvironmentSummary.skijaSummary(DIR, List.of(), null, true, 51, null),
                EnvironmentSummary.skijaSummary(DIR, List.of(), null, false, 12, "UnsatisfiedLinkError"),
                EnvironmentSummary.skijaSummary(DIR, List.of(),
                        new String[]{"0.116.8", "META-INF/jarjar/skija-windows-x64-0.116.8.jar"}, true, 9, null),
                EnvironmentSummary.skijaSummary(null, List.of(), null, false, 3, "no library")}) {
            assertTrue(line.chars().allMatch(c -> c < 0x80), line);
        }
    }

    /** 造一个"像我们产物那样"的 jar：META-INF/jarjar/ 下嵌一个带版本文件的原生包。 */
    private static Path artifactWithNestedSkija(Path dir, String version) throws Exception {
        ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
        try (ZipOutputStream nested = new ZipOutputStream(nestedBytes)) {
            nested.putNextEntry(new ZipEntry(DIR + "skija.version"));
            nested.write(version.getBytes(StandardCharsets.UTF_8));
            nested.closeEntry();
        }
        Path jar = dir.resolve("atomchat-Forge-1.20.1-0.2.11.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/jarjar/skija-windows-x64-" + version + ".jar"));
            out.write(nestedBytes.toByteArray());
            out.closeEntry();
        }
        return jar;
    }
}
