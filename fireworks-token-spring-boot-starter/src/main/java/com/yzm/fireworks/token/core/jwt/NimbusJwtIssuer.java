package com.yzm.fireworks.token.core.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.yzm.fireworks.token.core.JwtIssuer;
import com.yzm.fireworks.token.core.TokenConfigurationException;
import com.yzm.fireworks.token.core.TokenSpec;
import com.yzm.fireworks.token.core.key.JwtKeyProvider;
import com.yzm.fireworks.token.core.key.SigningKey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 基于 Nimbus JOSE+JWT 的签发实现。
 *
 * @author JYuan
 */
public final class NimbusJwtIssuer implements JwtIssuer {

    private final JwtKeyProvider keyProvider;
    private final JwtIssuancePolicy policy;
    private final Clock clock;
    private final Supplier<String> jwtIdSupplier;

    public NimbusJwtIssuer(JwtKeyProvider keyProvider, JwtIssuancePolicy policy, Clock clock) {
        this(keyProvider, policy, clock, NimbusJwtIssuer::uuidV7);
    }

    public NimbusJwtIssuer(JwtKeyProvider keyProvider, JwtIssuancePolicy policy, Clock clock,
                           Supplier<String> jwtIdSupplier) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jwtIdSupplier = Objects.requireNonNull(jwtIdSupplier, "jwtIdSupplier must not be null");
    }

    @Override
    public String issue(TokenSpec spec) {
        SigningKey key = keyProvider.signingKey();
        Duration ttl = spec.ttl() == null ? policy.defaultTtl() : spec.ttl();
        if (ttl.compareTo(policy.maxTtl()) > 0) {
            throw new IllegalArgumentException("token ttl " + ttl + " exceeds the configured max-ttl " + policy.maxTtl());
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        List<String> audience = spec.audience().isEmpty() ? policy.audience() : spec.audience();
        String jwtId = resolveJwtId(spec);

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(policy.issuer())
                .subject(spec.subject())
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        if (!audience.isEmpty()) {
            claims.audience(audience);
        }
        if (jwtId != null) {
            claims.jwtID(jwtId);
        }
        spec.attributes().forEach(claims::claim);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.parse(key.algorithm().name()))
                .keyID(key.keyId())
                // RFC 9068：访问令牌标注 at+jwt，便于网关与日志系统识别
                .type(new JOSEObjectType("at+jwt"))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(SignerFactory.signer(key));
        } catch (JOSEException e) {
            throw new TokenConfigurationException("unable to sign token with key '" + key.keyId()
                    + "' and algorithm " + key.algorithm(), e);
        }
        return jwt.serialize();
    }

    private String resolveJwtId(TokenSpec spec) {
        if (spec.jwtId() != null) {
            return spec.jwtId();
        }
        return policy.includeJti() ? jwtIdSupplier.get() : null;
    }

    /**
     * UUIDv7：48 位毫秒时间戳 + 版本/变体位 + 随机位。
     * <p>
     * 相比 v4 的好处是按时间有序，便于按签发时间范围检索 {@code jti}。
     */
    private static String uuidV7() {
        long timestamp = System.currentTimeMillis();
        long randomA = ThreadLocalRandom.current().nextLong() & 0x0FFFL;
        long most = ((timestamp & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | randomA;
        long least = (ThreadLocalRandom.current().nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(most, least).toString();
    }
}
