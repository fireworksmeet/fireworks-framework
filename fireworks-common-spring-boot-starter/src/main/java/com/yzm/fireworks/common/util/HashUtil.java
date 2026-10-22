package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 哈希工具类（不可逆）
 *
 * <p>根据使用场景选择对应方法，不要混用：
 *
 * <pre>
 * 场景                        方法                         原因
 * ──────────────────────────────────────────────────────────────────────
 * 手机号/邮箱 → 查询登录日志   hmacSha256(value, secret)    固定密钥，支持复现查询
 * 密码                        不要用本类                   请用 BCrypt / Argon2
 * 高熵随机令牌 → 存库          sha256Hex(token)             2^128 空间，加不加密钥无差别
 * 消息去重 / 设备指纹          sha256Hex(content)           无状态，不需要复现
 * 令牌校验                     matches(raw, digest)         常量时间比较，防时序探测
 * </pre>
 *
 * <p>两种哈希的区别是<b>有没有密钥</b>，不是两套算法：{@code hmacSha256} 就是
 * {@code HMAC-SHA256(key, value)}，{@code sha256Hex} 就是 {@code SHA-256(value)}。
 *
 * <p><b>关于固定密钥的注意事项：</b>密钥一旦确定不能修改，改了之后所有历史摘要均无法匹配。
 * 密钥应存储在配置中心（Nacos），与数据库密码同等保管级别，且<b>不要复用 JWT 签名密钥</b>——
 * 轮换签名密钥不应该让所有业务摘要一起失效。
 *
 * @author JYuan
 */
@UtilityClass
public class HashUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    private static final int FINGERPRINT_LENGTH = 8;

    // ── 一、带密钥哈希（HMAC） ─────────────────────────────────────────────

    /**
     * HMAC-SHA256：用固定密钥对内容做哈希，同一输入永远得到同一摘要，可支持精确查询。
     *
     * <p>用于<b>低熵输入</b>（手机号、邮箱、验证码、短码）：取值空间小，无密钥的摘要可被
     * 彩虹表或批量穷举反推，带密钥后攻击者还需要拿到密钥。
     *
     * <p>为什么不写成 {@code sha256(secret + value)}：拼接存在长度扩展攻击与边界歧义，
     * HMAC（RFC 2104）是标准解法，JDK 直接提供 {@link Mac}。
     *
     * <p><b>重要：</b>同一业务场景必须始终使用同一个密钥，变更后历史摘要全部失效无法匹配；
     * 密钥应按密钥级别保管，且不要与 JWT 签名密钥共用（避免一次轮换连带失效）。
     *
     * @param value  原始值（手机号 / 邮箱 / 验证码等）
     * @param secret 固定密钥（从配置中心读取，不可硬编码，不可修改）
     * @return 十六进制摘要，长度固定 64 位
     */
    public static String hmacSha256(String value, String secret) {
        Assert.hasText(value, "value must not be blank");
        Assert.hasText(secret, "secret must not be blank");
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] result = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException("hmacSha256 failed", e);
        }
    }

    // ── 二、无密钥哈希 ─────────────────────────────────────────────────────

    /**
     * SHA-256 哈希（无密钥），用于不需要防预计算的高熵输入。
     *
     * <p>适用场景：
     * <ul>
     *   <li>高熵随机令牌落库（128 bit 以上，不存在预计算攻击）</li>
     *   <li>消息去重 key（相同内容产生相同 key 即可）</li>
     *   <li>设备指纹（IP + UA + Language 的摘要）</li>
     *   <li>文件完整性校验</li>
     * </ul>
     *
     * <p><b>不适用场景：</b>手机号/邮箱、验证码等低熵输入——无密钥可被彩虹表破解，
     * 请改用 {@link #hmacSha256}。
     *
     * @param content 原始内容
     * @return 十六进制哈希字符串，长度固定 64 位
     */
    public static String sha256Hex(String content) {
        Assert.hasText(content, "content must not be blank");
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 哈希，字节数组输入，用于文件/二进制内容的完整性校验。
     *
     * @param bytes 原始字节数组
     * @return 十六进制哈希字符串，长度固定 64 位
     */
    public static String sha256Hex(byte[] bytes) {
        Assert.notNull(bytes, "bytes must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // ── 三、摘要比对与日志指纹 ─────────────────────────────────────────────

    /**
     * 常量时间比较：把原文按 {@link #sha256Hex(String)} 计算摘要，与期望摘要逐位比较。
     *
     * <p><b>不要用 {@code String.equals} 比较摘要</b>：逐字符比较会在第一个不同处返回，
     * 攻击者可以通过耗时差异逐位猜出正确摘要。{@link MessageDigest#isEqual} 的时间与内容无关。
     *
     * @param raw            待校验的原文（令牌、验证码等）
     * @param expectedDigest 已存储的十六进制摘要
     * @return 一致返回 {@code true}；任一参数为 {@code null} 返回 {@code false}
     */
    public static boolean matches(String raw, String expectedDigest) {
        if (raw == null || expectedDigest == null) {
            return false;
        }
        byte[] actual = sha256Hex(raw).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedDigest.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * 摘要前 8 位，仅用于日志、工单与排障定位——不能反推原文，也不能作为校验依据。
     *
     * @param content 原始内容
     * @return 8 位十六进制字符串
     */
    public static String fingerprint(String content) {
        return sha256Hex(content).substring(0, FINGERPRINT_LENGTH);
    }
}
