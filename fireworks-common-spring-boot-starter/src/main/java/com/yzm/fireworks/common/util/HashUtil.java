package com.yzm.fireworks.common.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.Assert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 哈希工具类（不可逆）
 *
 * <p>根据使用场景选择对应方法，不要混用：
 *
 * <pre>
 * 场景                        方法                         原因
 * ──────────────────────────────────────────────────────────────────────
 * 手机号/邮箱 → 查询登录日志   hashWithSalt(value, salt)    固定盐，支持复现查询
 * Token → 存入 Redis          hashWithSalt(token, salt)  固定盐，支持复现查询
 * 消息去重 / 设备指纹          sha256(content)              无状态，不需要复现
 * </pre>
 *
 * <p><b>关于固定盐的注意事项：</b>盐值一旦确定不能修改，改了之后所有历史哈希均无法匹配。
 * 盐值应存储在配置中心（Nacos），与数据库密码、JWT 密钥同等保管级别。
 *
 * @author JYuan
 */
public final class HashUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HashUtil() {
        throw new AssertionError("Utility class");
    }

    // ── 一、固定盐哈希 ─────────────────────────────────────────────────────

    /**
     * 使用固定盐对内容做 HmacSHA256 哈希，支持精确查询复现。
     *
     * <p><b>两种典型用法：</b>
     * <ul>
     *   <li>手机号/邮箱查询：salt 传系统级固定盐（从 Nacos 读取）</li>
     *   <li>Token 存储：salt 传 jwtSecret，原理相同，jwtSecret 本质就是盐</li>
     * </ul>
     *
     * <p><b>重要：</b>同一业务场景必须始终使用同一个 salt，
     * salt 变更后历史哈希全部失效无法查询。
     *
     * @param value 原始值（手机号 / 邮箱 / Token 等）
     * @param salt  固定盐（从配置中心读取，不可硬编码，不可修改）
     * @return 十六进制哈希字符串，长度固定 64 位
     */
    public static String hashWithSalt(String value, String salt) {
        Assert.hasText(value, "value must not be blank");
        Assert.hasText(salt, "salt must not be blank");
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] result = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException("hashWithSalt failed", e);
        }
    }

    // ── 二、无盐哈希 ───────────────────────────────────────────────────────

    /**
     * SHA-256 哈希，无盐，用于不需要「输入 → 复现查询」的场景。
     *
     * <p>适用场景：
     * <ul>
     *   <li>消息去重 key（相同内容产生相同 key 即可）</li>
     *   <li>设备指纹（IP + UA + Language 的摘要）</li>
     *   <li>文件完整性校验</li>
     * </ul>
     *
     * <p><b>不适用场景：</b>手机号/邮箱哈希——无盐可被彩虹表破解，
     * 请改用 {@link #hashWithSalt}。
     *
     * @param content 原始内容
     * @return 十六进制哈希字符串，长度固定 64 位
     */
    public static String sha256(String content) {
        Assert.hasText(content, "content must not be blank");
        return DigestUtils.sha256Hex(content);
    }

    /**
     * SHA-256 哈希，字节数组输入，用于文件/二进制内容的完整性校验。
     *
     * @param bytes 原始字节数组
     * @return 十六进制哈希字符串，长度固定 64 位
     */
    public static String sha256(byte[] bytes) {
        Assert.notNull(bytes, "bytes must not be null");
        return DigestUtils.sha256Hex(bytes);
    }
}