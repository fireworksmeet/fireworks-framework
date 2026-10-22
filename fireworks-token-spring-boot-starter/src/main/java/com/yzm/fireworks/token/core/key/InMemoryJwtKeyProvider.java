package com.yzm.fireworks.token.core.key;

import com.yzm.fireworks.token.core.TokenConfigurationException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由配置构建的密钥集合，启动后不可变。
 *
 * @author JYuan
 */
public final class InMemoryJwtKeyProvider implements JwtKeyProvider {

    private final SigningKey signingKey;
    private final Map<String, VerificationKey> verificationKeys;

    public InMemoryJwtKeyProvider(SigningKey signingKey, Collection<VerificationKey> verificationKeys) {
        this.signingKey = signingKey;
        Map<String, VerificationKey> keys = new LinkedHashMap<>();
        if (verificationKeys != null) {
            for (VerificationKey key : verificationKeys) {
                VerificationKey previous = keys.putIfAbsent(key.keyId(), key);
                if (previous != null) {
                    throw new TokenConfigurationException("duplicate kid '" + key.keyId() + "' in key configuration");
                }
            }
        }
        this.verificationKeys = keys;
    }

    @Override
    public SigningKey signingKey() {
        if (signingKey == null) {
            throw new TokenConfigurationException("no signing key configured: set 'signing: true' on one key "
                    + "with a private key or secret");
        }
        return signingKey;
    }

    @Override
    public boolean hasSigningKey() {
        return signingKey != null;
    }

    @Override
    public VerificationKey verificationKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            // 无 kid 时不做逐个尝试：只有在唯一一把密钥时才可确定
            return verificationKeys.size() == 1 ? verificationKeys.values().iterator().next() : null;
        }
        return verificationKeys.get(keyId);
    }

    @Override
    public List<VerificationKey> verificationKeys() {
        return List.copyOf(verificationKeys.values());
    }
}
