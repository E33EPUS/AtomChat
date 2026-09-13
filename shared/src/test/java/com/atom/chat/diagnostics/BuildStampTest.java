package com.atom.chat.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 构建戳的读取与降级。重点是**读不到时不许编**：这行日志的全部价值在于它是真的，
 * 一个"看起来像版本号"的占位值比 {@code unknown} 有害得多。
 */
class BuildStampTest {

    private static Properties full() {
        Properties props = new Properties();
        props.setProperty("version", "0.2.11");
        props.setProperty("target", "1.20.1-forge");
        props.setProperty("minecraft", "1.20.1");
        props.setProperty("loader", "Forge");
        props.setProperty("loader_version", "47.4.10");
        props.setProperty("git_commit", "56122d9");
        props.setProperty("git_dirty", "false");
        return props;
    }

    @Test
    void readsEveryField() {
        BuildStamp stamp = BuildStamp.from(full());
        assertEquals("0.2.11", stamp.version());
        assertEquals("1.20.1-forge", stamp.target());
        assertEquals("1.20.1", stamp.minecraft());
        assertEquals("Forge", stamp.loader());
        assertEquals("47.4.10", stamp.loaderVersion());
        assertEquals("56122d9", stamp.gitCommit());
        assertFalse(stamp.gitDirty());
        assertEquals("56122d9", stamp.git());
        assertEquals("Forge 47.4.10 / minecraft 1.20.1", stamp.platform());
    }

    @Test
    void marksADirtyTreeOnlyNextToACommit() {
        Properties props = full();
        props.setProperty("git_dirty", "true");
        assertEquals("56122d9+dirty", BuildStamp.from(props).git());

        // 没有提交号时"+dirty"没有意义：那是"构建源未知且脏"，读的人只会困惑。
        props.setProperty("git_commit", "");
        assertEquals(BuildStamp.UNKNOWN, BuildStamp.from(props).git());
    }

    @Test
    void missingKeysBecomeUnknown() {
        BuildStamp stamp = BuildStamp.from(new Properties());
        assertEquals(BuildStamp.UNKNOWN, stamp.version());
        assertEquals(BuildStamp.UNKNOWN, stamp.minecraft());
        assertEquals(BuildStamp.UNKNOWN, stamp.git());
        assertFalse(stamp.gitDirty(), "缺少 git_dirty 不能被当成脏");
    }

    /**
     * IDE 直接跑测试、或者资源没被处理过时，文件里留着的是没展开的模板值。
     * 放它过去的话，日志里会出现字面的 {@code ${version}}。
     */
    @Test
    void unexpandedTemplateValuesAreNotReportedAsReal() {
        Properties props = new Properties();
        props.setProperty("version", "${version}");
        props.setProperty("minecraft", "  ${minecraft}  ");
        props.setProperty("loader", "   ");
        assertEquals(BuildStamp.UNKNOWN, BuildStamp.from(props).version());
        assertEquals(BuildStamp.UNKNOWN, BuildStamp.from(props).minecraft());
        assertEquals(BuildStamp.UNKNOWN, BuildStamp.from(props).loader());
    }

    /** 真资源在单测 JVM 里要么被处理过、要么缺失，两种都必须能用。 */
    @Test
    void realStampLoadsWithoutThrowing() {
        BuildStamp stamp = BuildStamp.get();
        assertTrue(stamp.version() != null && !stamp.version().isEmpty());
        assertTrue(stamp.platform().contains("minecraft"));
    }
}
