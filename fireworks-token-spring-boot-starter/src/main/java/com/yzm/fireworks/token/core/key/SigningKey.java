package com.yzm.fireworks.token.core.key;

import com.yzm.fireworks.token.core.TokenAlgorithm;
import com.yzm.fireworks.token.core.TokenConfigurationException;

import java.security.PrivateKey;

/**
 * 签发密钥：{@code kid} + 算法 + 材料（私钥或对称密钥）。
 * <p>
 * 材料访问器（{@link #privateKey()} / {@link #secret()}）供 {@code core.jwt} 的签名实现使用；
 * 业务代码不应直接读取。{@code toString} 只输出 {@code kid} 与算法。
 *
 * @author JYuan
 */
public final class SigningKey extends JwtKey {

    private final PrivateKey privateKey;

    private SigningKey(String keyId, TokenAlgorithm algorithm, PrivateKey privateKey, byte[] secret) {
        super(keyId, algorithm, secret);
        this.privateKey = privateKey;
    }

    /**
     * 构造对称密钥（HS256）。
     *
     * @param secret 密钥字节，长度不得低于算法要求（HS256 ≥ 32 字节）
     */
    public static SigningKey ofSecret(String keyId, TokenAlgorithm algorithm, byte[] secret) {
        return new SigningKey(keyId, algorithm, null, requireSecret(keyId, algorithm, secret));
    }

    /**
     * 构造非对称私钥（RSA / EC）。
     */
    public static SigningKey ofPrivateKey(String keyId, TokenAlgorithm algorithm, PrivateKey privateKey) {
        requireAsymmetric(keyId, algorithm, "private key");
        if (privateKey == null) {
            throw new TokenConfigurationException("key '" + keyId + "': private key must not be null");
        }
        return new SigningKey(keyId, algorithm, privateKey, null);
    }

    /**
     * 非对称私钥；对称算法返回 {@code null}。
     */
    public PrivateKey privateKey() {
        return privateKey;
    }
}
