package com.yzm.fireworks.token.autoconfigure;

import com.yzm.fireworks.token.core.JwtIssuer;
import com.yzm.fireworks.token.core.JwtVerifier;
import com.yzm.fireworks.token.core.TokenConfigurationException;
import com.yzm.fireworks.token.core.TokenSpec;
import com.yzm.fireworks.token.core.jwt.JwtIssuancePolicy;
import com.yzm.fireworks.token.core.jwt.JwtVerificationPolicy;
import com.yzm.fireworks.token.core.jwt.NimbusJwtIssuer;
import com.yzm.fireworks.token.core.jwt.NimbusJwtVerifier;
import com.yzm.fireworks.token.core.key.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Token 自动配置。
 * <p>
 * 密钥为空时不装配任何 JWT Bean，只保留一个空密钥集合 Bean：本模块可能经传递依赖进入
 * 并不使用 JWT 的工程，不应让"没用它"变成"启动失败"。
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(TokenProperties.class)
@ConditionalOnProperty(prefix = "fireworks.token", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtKeyProvider jwtKeyProvider(TokenProperties properties) {
        ConfiguredKeys keys = ConfiguredKeys.from(properties);
        InMemoryJwtKeyProvider provider = new InMemoryJwtKeyProvider(keys.signingKey(), keys.verificationKeys());
        log.info("fireworks.token: {} verification key(s), signing key: {}", provider.verificationKeys().size(),
                provider.hasSigningKey() ? provider.signingKey().keyId() : "(none)");
        return provider;
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnSigningKeyCondition.class)
    public JwtIssuer jwtIssuer(TokenProperties properties, JwtKeyProvider keyProvider,
                               ObjectProvider<Clock> clocks) {
        return new NimbusJwtIssuer(keyProvider, issuancePolicy(properties), clock(clocks));
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnVerificationKeyCondition.class)
    public JwtVerifier jwtVerifier(TokenProperties properties, JwtKeyProvider keyProvider,
                                   ObjectProvider<Clock> clocks) {
        Clock clock = clock(clocks);
        JwtVerificationPolicy policy = verificationPolicy(properties);
        NimbusJwtVerifier verifier = new NimbusJwtVerifier(keyProvider, policy, clock);
        selfCheck(properties, keyProvider, verifier, clock);
        return verifier;
    }

    private JwtIssuancePolicy issuancePolicy(TokenProperties properties) {
        TokenProperties.Issuance issuance = properties.getIssuance();
        return new JwtIssuancePolicy(properties.getIssuer(), issuance.getDefaultTtl(), issuance.getMaxTtl(),
                issuance.getAudience(), issuance.isIncludeJti());
    }

    private JwtVerificationPolicy verificationPolicy(TokenProperties properties) {
        TokenProperties.Verification verification = properties.getVerification();
        long maxLength = verification.getMaxTokenLength().toBytes();
        if (maxLength <= 0 || maxLength > Integer.MAX_VALUE) {
            throw new TokenConfigurationException("verification.max-token-length is out of range: " + maxLength);
        }
        return new JwtVerificationPolicy(properties.getIssuer(), verification.getAudience(),
                verification.getClockSkew(), (int) maxLength);
    }

    private Clock clock(ObjectProvider<Clock> clocks) {
        return clocks.getIfAvailable(() -> Clock.systemUTC());
    }

    /**
     * 启动自检：签发一个 1 秒有效期的探针令牌并立即校验，提前暴露算法错配与公私钥不配对。
     * 只有私钥、没有公钥时无法本地验签，跳过并提示。
     */
    private void selfCheck(TokenProperties properties, JwtKeyProvider keyProvider, JwtVerifier verifier,
                           Clock clock) {
        if (!keyProvider.hasSigningKey()) {
            return;
        }
        SigningKey signingKey = keyProvider.signingKey();
        boolean verifiable = keyProvider.verificationKeys().stream()
                .anyMatch(key -> key.keyId().equals(signingKey.keyId()));
        if (!verifiable) {
            log.warn("fireworks.token: self-check skipped, key '{}' has no public key for local verification",
                    signingKey.keyId());
            return;
        }
        JwtIssuer issuer = new NimbusJwtIssuer(keyProvider, issuancePolicy(properties), clock);
        try {
            List<String> audience = properties.getVerification().getAudience();
            TokenSpec.Builder spec = TokenSpec.builder().subject("self-check").ttl(Duration.ofSeconds(1));
            if (audience != null && !audience.isEmpty()) {
                spec.audience(audience);
            }
            verifier.verify(issuer.issue(spec.build()));
        } catch (RuntimeException e) {
            throw new TokenConfigurationException("token self-check failed: signing and verification "
                    + "key/algorithm do not match", e);
        }
    }
}
