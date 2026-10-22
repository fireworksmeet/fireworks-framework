package com.yzm.fireworks.token.core.key;

import com.yzm.fireworks.token.core.TokenAlgorithm;
import com.yzm.fireworks.token.core.TokenConfigurationException;

import java.security.PublicKey;

/**
 * 验签密钥：{@code kid} + 算法 + 材料（公钥或对称密钥）。
 * <p>
 * 验签方只需要公钥或对称密钥；{@code toString} 只输出 {@code kid} 与算法。
 *
 * @author JYuan
 */
public final class VerificationKey extends JwtKey {

    private final PublicKey publicKey;

    private VerificationKey(String keyId, TokenAlgorithm algorithm, PublicKey publicKey, byte[] secret) {
        super(keyId, algorithm, secret);
        this.publicKey = publicKey;
    }

    /**
     * 构造对称密钥（HS256）。
     */
    public static VerificationKey ofSecret(String keyId, TokenAlgorithm algorithm, byte[] secret) {
        return new VerificationKey(keyId, algorithm, null, requireSecret(keyId, algorithm, secret));
    }

    /**
     * 构造非对称公钥（RSA / EC）。
     */
    public static VerificationKey ofPublicKey(String keyId, TokenAlgorithm algorithm, PublicKey publicKey) {
        requireAsymmetric(keyId, algorithm, "public key");
        if (publicKey == null) {
            throw new TokenConfigurationException("key '" + keyId + "': public key must not be null");
        }
        return new VerificationKey(keyId, algorithm, publicKey, null);
    }

    /**
     * 非对称公钥；对称算法返回 {@code null}。
     */
    public PublicKey publicKey() {
        return publicKey;
    }
}
