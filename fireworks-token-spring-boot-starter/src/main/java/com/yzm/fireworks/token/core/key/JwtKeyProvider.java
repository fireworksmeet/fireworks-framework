package com.yzm.fireworks.token.core.key;

import com.yzm.fireworks.token.core.TokenConfigurationException;

import java.util.List;

/**
 * 密钥来源：签发密钥唯一，验签密钥全量。
 * <p>
 * 需要 KMS / Vault / JWKS 时由业务实现本接口，返回自己的密钥实现即可（框架只通过
 * {@link SigningKey} / {@link VerificationKey} 访问材料）。
 *
 * @author JYuan
 */
public interface JwtKeyProvider {

    /**
     * 当前签发密钥（全局唯一）。
     *
     * @throws TokenConfigurationException 未配置签发密钥
     */
    SigningKey signingKey();

    /**
     * 是否配置了签发密钥。
     */
    boolean hasSigningKey();

    /**
     * 按 {@code kid} 查找验签密钥。
     *
     * @param keyId 令牌 header 中的 {@code kid}，可为 {@code null}
     * @return 命中的密钥；{@code kid} 为空且只有一把验签密钥时返回该密钥，否则返回 {@code null}
     */
    VerificationKey verificationKey(String keyId);

    /**
     * 全部验签密钥（启动日志与诊断使用）。
     */
    List<VerificationKey> verificationKeys();
}
