package com.yzm.fireworks.token.core.jwt;

import com.yzm.fireworks.token.core.TokenConfigurationException;

import java.time.Duration;
import java.util.List;

/**
 * 签发策略：写入令牌的固定声明与有效期约束。
 *
 * @param issuer     签发方，写入 {@code iss}，必填
 * @param defaultTtl 默认有效期，{@link com.yzm.fireworks.token.core.TokenSpec#ttl()} 为空时使用
 * @param maxTtl     有效期上限，超过即拒绝（防止 30 天令牌）
 * @param audience   默认受众，{@link com.yzm.fireworks.token.core.TokenSpec#audience()} 为空时使用
 * @param includeJti 是否自动生成 {@code jti}
 * @author JYuan
 */
public record JwtIssuancePolicy(
        String issuer,
        Duration defaultTtl,
        Duration maxTtl,
        List<String> audience,
        boolean includeJti
) {

    public JwtIssuancePolicy {
        if (issuer == null || issuer.isBlank()) {
            throw new TokenConfigurationException("'fireworks.token.issuer' is required for issuing tokens");
        }
        defaultTtl = defaultTtl == null ? Duration.ofMinutes(15) : defaultTtl;
        maxTtl = maxTtl == null ? Duration.ofHours(24) : maxTtl;
        if (defaultTtl.isNegative() || defaultTtl.isZero()) {
            throw new TokenConfigurationException("issuance default-ttl must be positive: " + defaultTtl);
        }
        if (maxTtl.compareTo(defaultTtl) < 0) {
            throw new TokenConfigurationException("issuance max-ttl (" + maxTtl
                    + ") must not be shorter than default-ttl (" + defaultTtl + ")");
        }
        audience = audience == null ? List.of() : List.copyOf(audience);
    }
}
