package com.yzm.fireworks.token.core.jwt;

import com.yzm.fireworks.token.core.TokenConfigurationException;

import java.time.Duration;
import java.util.List;

/**
 * 校验策略：决定"什么样的令牌我才接受"。
 * <p>
 * 只保留"部署方真的会改"的三项：签发方、受众、时间容忍度与长度上限。
 * 其余固定，不做成配置项：
 * <ul>
 *     <li>算法：由每把密钥声明，`kid` 命中后要求 `header.alg` 与之一致</li>
 *     <li>必需声明：`exp` / `iat` / `sub` 硬编码——签发侧本来就会写，缺了说明不是本模块签的</li>
 *     <li>最大存活时长：签发侧的 `max-ttl` 已经拦住，无需在校验侧再配一遍</li>
 * </ul>
 *
 * @param issuer         期望签发方，严格相等比较；必填
 * @param audience       期望受众，非空时要求交集非空
 * @param clockSkew      时间容忍度，作用于 exp / nbf / iat
 * @param maxTokenLength 令牌最大字符数，超过直接拒绝（防 DoS）
 * @author JYuan
 */
public record JwtVerificationPolicy(
        String issuer,
        List<String> audience,
        Duration clockSkew,
        int maxTokenLength
) {

    public JwtVerificationPolicy {
        if (issuer == null || issuer.isBlank()) {
            throw new TokenConfigurationException("'fireworks.token.issuer' is required for verifying tokens");
        }
        audience = audience == null ? List.of() : List.copyOf(audience);
        clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
        if (clockSkew.isNegative()) {
            throw new TokenConfigurationException("verification clock-skew must not be negative: " + clockSkew);
        }
        if (maxTokenLength <= 0) {
            throw new TokenConfigurationException("verification max-token-length must be positive: " + maxTokenLength);
        }
    }
}
