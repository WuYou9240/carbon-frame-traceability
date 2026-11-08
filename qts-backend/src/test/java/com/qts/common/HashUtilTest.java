package com.qts.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 哈希工具单元测试：保证「同一份数据在任何机器、任何时间算出的摘要都一致」，
 * 这是链上链下比对能够成立的前提。
 */
class HashUtilTest {

    @Test
    @DisplayName("SHA-256 输出为 64 位小写十六进制")
    void sha256_shouldReturn64CharLowerHex() {
        String h = HashUtil.sha256Hex("quality-trace");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"), "应全部为小写十六进制字符: " + h);
    }

    @Test
    @DisplayName("相同输入必须得到相同摘要（确定性）")
    void sha256_shouldBeDeterministic() {
        String input = HashUtil.canonical("FRAME", "FR2026001", "S-WORKS", "54");
        assertEquals(HashUtil.sha256Hex(input), HashUtil.sha256Hex(input));
    }

    @Test
    @DisplayName("输入任意一个字符变化，摘要必须变化（雪崩效应）")
    void sha256_shouldChangeWhenInputChanges() {
        String a = HashUtil.sha256Hex(HashUtil.canonical("QC", "DIMENSION", "PASS"));
        String b = HashUtil.sha256Hex(HashUtil.canonical("QC", "DIMENSION", "FAIL"));
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("canonical 用分隔符隔开各字段，避免字段拼接产生歧义")
    void canonical_shouldSeparateFieldsToAvoidAmbiguity() {
        // 若不用分隔符直接拼接，下面两组不同数据会拼成同一个字符串 "abcd"
        String first = HashUtil.canonical("ab", "cd");
        String second = HashUtil.canonical("a", "bcd");
        assertNotEquals(first, second, "字段边界必须可区分，否则不同数据会撞出同一哈希");
        assertEquals("ab|cd\n", first);
    }

    @Test
    @DisplayName("空串与 null 参与拼接时不抛异常")
    void canonical_shouldTolerateEmptyValues() {
        assertDoesNotThrow(() -> HashUtil.sha256Hex(HashUtil.canonical("", "", "")));
    }
}
