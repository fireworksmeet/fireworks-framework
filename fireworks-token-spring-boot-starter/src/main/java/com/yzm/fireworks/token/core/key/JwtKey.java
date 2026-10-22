package com.yzm.fireworks.token.core.key;

import com.yzm.fireworks.token.core.TokenAlgorithm;
import com.yzm.fireworks.token.core.TokenConfigurationException;

/**
 * {@link SigningKey} / {@link VerificationKey} 的公共部分：{@code kid}、算法与对称材料。
 * <p>
 * 包内可见，不进公开契约：对外仍然只有两个 {@code final} 类，业务代码看不到这个基类。
 * <p>
 * {@code toString} 定稿在这里、子类不覆盖——只输出 {@code kid} 与算法，材料不随日志泄漏。
 *
 * @author JYuan
 */
sealed abstract class JwtKey permits SigningKey, VerificationKey {

    private final String keyId;
    private final TokenAlgorithm algorithm;
    private final byte[] secret;

    JwtKey(String keyId, TokenAlgorithm algorithm, byte[] secret) {
        if (keyId == null || keyId.isBlank()) {
            throw new TokenConfigurationException("key id (kid) must not be blank");
        }
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.secret = secret;
    }

    public final String keyId() {
        return keyId;
    }

    public final TokenAlgorithm algorithm() {
        return algorithm;
    }

    /**
     * 对称密钥的副本；非对称密钥返回 {@code null}。
     */
    public final byte[] secret() {
        return secret == null ? null : secret.clone();
    }

    /**
     * 校验并复制对称材料：算法必须对称，长度不得低于该算法要求。
     */
    static byte[] requireSecret(String keyId, TokenAlgorithm algorithm, byte[] secret) {
        if (algorithm == null || !algorithm.isSymmetric()) {
            throw new TokenConfigurationException("key '" + keyId + "': algorithm " + algorithm
                    + " is not a symmetric algorithm, cannot use 'secret'");
        }
        if (secret == null || secret.length < algorithm.minSecretBytes()) {
            throw new TokenConfigurationException("key '" + keyId + "': secret must be at least "
                    + algorithm.minSecretBytes() + " bytes for " + algorithm);
        }
        return secret.clone();
    }

    /**
     * 校验算法必须非对称；{@code material} 只用于错误信息（{@code private key} / {@code public key}）。
     */
    static void requireAsymmetric(String keyId, TokenAlgorithm algorithm, String material) {
        if (algorithm == null || algorithm.isSymmetric()) {
            throw new TokenConfigurationException("key '" + keyId + "': algorithm " + algorithm
                    + " is symmetric, cannot use a " + material);
        }
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "{kid=" + keyId + ", algorithm=" + algorithm + "}";
    }
}
