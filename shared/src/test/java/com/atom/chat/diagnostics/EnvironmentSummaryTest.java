package com.atom.chat.diagnostics;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
