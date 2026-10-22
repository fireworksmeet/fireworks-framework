package com.yzm.fireworks.token.core;

/**
 * 支持的 JWS 签名算法。
 * <p>
 * 只保留三种，一种一个理由：
 * <ul>
 *     <li>{@code HS256}：单服务自签自验、联调；实现简单，但验签方具备签发能力</li>
 *     <li>{@code RS256}：兼容性最好，任何网关、任何语言的 JWT 库都支持</li>
 *     <li>{@code ES256}：密钥与签名更短（签名 64 字节，RS256 是 256 字节），作为推荐默认</li>
 * </ul>
 * 不提供 384 / 512 / PS 系列：JWT 里几乎没有收益，只增加配置面与测试矩阵。
 * 算法与密钥类型强绑定，且必须由每把密钥显式声明，框架不提供默认值。
 *
 * @author JYuan
 */
public enum TokenAlgorithm {

    /**
     * HMAC-SHA256，对称密钥（≥ 256 bit）
     */
    HS256,
    /**
     * RSASSA-PKCS1-v1_5 + SHA-256
     */
    RS256,
    /**
     * ECDSA + SHA-256（P-256）
     */
    ES256;

    /**
     * 是否为对称算法（验签方同时具备签发能力）。
     */
    public boolean isSymmetric() {
        return this == HS256;
    }

    /**
     * 是否为非对称算法。
     */
    public boolean isAsymmetric() {
        return !isSymmetric();
    }

    /**
     * 对称算法要求的最小密钥字节数；非对称算法由 JDK 校验密钥长度，返回 0。
     */
    public int minSecretBytes() {
        return this == HS256 ? 32 : 0;
    }
}
