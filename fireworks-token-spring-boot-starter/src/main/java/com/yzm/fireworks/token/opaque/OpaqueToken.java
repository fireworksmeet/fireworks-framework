package com.yzm.fireworks.token.opaque;

import com.yzm.fireworks.common.util.Base64Util;
import com.yzm.fireworks.common.util.HashUtil;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;

/**
 * 不透明令牌：{@code value} 只在签发时返回一次，{@code digest} 用于存储与索引。
 * <p>
 * 默认 32 字节（256 bit）随机值，Base64URL 无填充。{@code prefix} 用于泄露扫描与肉眼区分环境
 * （GitHub {@code ghp_} 模式），参与摘要计算。
 * <p>
 * {@code toString} 不输出 {@code value}，避免随日志泄漏。
 *
 * @author JYuan
 */
public record OpaqueToken(String value, String digest) {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 随机字节数下限：128 bit。
     */
    private static final int MIN_BYTES = 16;

    /**
     * 默认随机字节数：256 bit。
     */
    private static final int DEFAULT_BYTES = 32;

    public OpaqueToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("token value must not be blank");
        }
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("token digest must not be blank");
        }
    }

    /**
     * 生成 32 字节随机令牌。
     */
    public static OpaqueToken generate() {
        return generate(DEFAULT_BYTES, null);
    }

    /**
     * 生成指定字节数的随机令牌。
     *
     * @param bytes  随机字节数，不得小于 16
     * @param prefix 明文前缀，可为 {@code null}
     */
    public static OpaqueToken generate(int bytes, String prefix) {
        if (bytes < MIN_BYTES) {
            throw new IllegalArgumentException("token length must be at least " + MIN_BYTES + " bytes, but was " + bytes);
        }
        byte[] random = new byte[bytes];
        SECURE_RANDOM.nextBytes(random);
        String value = (prefix == null ? "" : prefix) + Base64Util.encodeUrl(random);
        return new OpaqueToken(value, HashUtil.sha256Hex(value));
    }

    /**
     * 常量时间比对：拿用户提交的原文与存储的摘要比较。
     */
    public static boolean matches(String presented, String expectedDigest) {
        return HashUtil.matches(presented, expectedDigest);
    }

    @NotNull
    @Override
    public String toString() {
        return "OpaqueToken{digest=" + HashUtil.fingerprint(value) + "...}";
    }
}
