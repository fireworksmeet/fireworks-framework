package com.yzm.fireworks.token.core;

/**
 * 令牌校验失败原因。
 * <p>
 * 全部原因都表示"这个 token 不被接受"，上层统一映射 401；枚举本身用于日志、指标与排障，
 * 不承担"401 还是 503"的分类职责——那属于 security 层（例如 JwtKeyProvider 取密钥失败）。
 *
 * @author JYuan
 */
public enum TokenErrorCode {

    /**
     * 结构、编码或 JSON 不合法
     */
    MALFORMED,
    /**
     * 超过 max-token-length，未进入解析
     */
    TOKEN_TOO_LARGE,
    /**
     * header.alg 不在允许集合内
     */
    UNSUPPORTED_ALGORITHM,
    /**
     * kid 找不到，或多密钥但缺少 kid
     */
    UNKNOWN_KEY,
    /**
     * 签名不匹配
     */
    SIGNATURE_INVALID,
    /**
     * 缺少必需声明（默认 exp / iat / sub）
     */
    MISSING_CLAIM,
    /**
     * 已过期
     */
    EXPIRED,
    /**
     * nbf 未到
     */
    NOT_YET_VALID,
    /**
     * iat 在未来（超出时钟偏移容忍）
     */
    INVALID_ISSUED_AT,
    /**
     * iss 与配置不一致
     */
    ISSUER_MISMATCH,
    /**
     * aud 与配置无交集
     */
    AUDIENCE_MISMATCH;
}
