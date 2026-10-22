package com.yzm.fireworks.token.core;

/**
 * JWT 校验。
 * <p>
 * 校验顺序固定为：长度 → 结构 → 算法 → 密钥 → 签名 → 时间 → 签发方 → 受众 → 用途 → 业务扩展，
 * 任一步失败立即短路。纯函数语义，实现必须线程安全。
 *
 * @author JYuan
 */
public interface JwtVerifier {

    /**
     * 校验令牌。
     *
     * @return 校验通过的不可变声明集合
     * @throws TokenValidationException 任一校验失败，可通过 {@link TokenValidationException#code()} 区分原因
     */
    TokenClaims verify(String token);
}
