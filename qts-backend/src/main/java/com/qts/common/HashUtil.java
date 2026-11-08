package com.qts.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 关键质量数据哈希工具：
 * 将车架档案 + 全部工序记录 + 全部质检记录按固定规则拼接成规范化字符串后计算 SHA-256。
 * 链下实时计算结果与链上存证哈希比对即可验真。
 */
public class HashUtil {

    public static String sha256Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    /** 规范化：分隔符换行结尾，保证拼接顺序无关歧义 */
    public static String canonical(String... parts) {
        return String.join("|", parts) + "\n";
    }
}
