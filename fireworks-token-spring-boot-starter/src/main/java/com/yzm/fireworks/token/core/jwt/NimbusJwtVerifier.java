package com.yzm.fireworks.token.core.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.yzm.fireworks.token.core.TokenAlgorithm;
import com.yzm.fireworks.token.core.TokenClaims;
import com.yzm.fireworks.token.core.TokenErrorCode;
import com.yzm.fireworks.token.core.TokenValidationException;
import com.yzm.fireworks.token.core.JwtVerifier;
import com.yzm.fireworks.token.core.key.JwtKeyProvider;
import com.yzm.fireworks.token.core.key.VerificationKey;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Nimbus JOSE+JWT 的校验实现。
 * <p>
 * 校验顺序：长度 → 结构 → 算法 → 密钥 → 签名 → 时间 → 主体 → 签发方 → 受众。
 * 任一步失败短路，不做"宽松兜底"。
 *
 * @author JYuan
 */
public final class NimbusJwtVerifier implements JwtVerifier {

    private static final Set<String> REGISTERED_CLAIMS = Set.of("iss", "sub", "aud", "exp", "nbf", "iat", "jti");

    private final JwtKeyProvider keyProvider;
    private final JwtVerificationPolicy policy;
    private final Clock clock;

    public NimbusJwtVerifier(JwtKeyProvider keyProvider, JwtVerificationPolicy policy, Clock clock) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public TokenClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw TokenValidationException.of(TokenErrorCode.MALFORMED, "blank token");
        }
        if (token.length() > policy.maxTokenLength()) {
            throw TokenValidationException.of(TokenErrorCode.TOKEN_TOO_LARGE, "length=" + token.length());
        }

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw TokenValidationException.of(TokenErrorCode.MALFORMED, "unparsable compact serialization", e);
        }

        TokenAlgorithm algorithm = resolveAlgorithm(jwt.getHeader().getAlgorithm());
        if (algorithm == null) {
            throw TokenValidationException.of(TokenErrorCode.UNSUPPORTED_ALGORITHM,
                    "alg=" + jwt.getHeader().getAlgorithm());
        }

        String keyId = jwt.getHeader().getKeyID();
        VerificationKey key = keyProvider.verificationKey(keyId);
        if (key == null) {
            throw TokenValidationException.of(TokenErrorCode.UNKNOWN_KEY,
                    keyId == null ? "missing kid" : "kid=" + keyId);
        }
        if (key.algorithm() != algorithm) {
            // 不暴露"算法与密钥不匹配"，避免被用来探测配置
            throw TokenValidationException.of(TokenErrorCode.SIGNATURE_INVALID, "kid=" + keyId);
        }
        try {
            if (!jwt.verify(SignerFactory.verifier(key))) {
                throw TokenValidationException.of(TokenErrorCode.SIGNATURE_INVALID, "kid=" + keyId);
            }
        } catch (JOSEException e) {
            throw TokenValidationException.of(TokenErrorCode.SIGNATURE_INVALID, "kid=" + keyId, e);
        }

        JWTClaimsSet claims = parseClaims(jwt);
        verifyTime(claims);
        verifySubject(claims);
        verifyIssuer(claims);
        verifyAudience(claims);

        return toClaims(claims);
    }

    private TokenAlgorithm resolveAlgorithm(JWSAlgorithm algorithm) {
        if (algorithm == null) {
            return null;
        }
        try {
            return TokenAlgorithm.valueOf(algorithm.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JWTClaimsSet parseClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw TokenValidationException.of(TokenErrorCode.MALFORMED, "invalid claim set", e);
        }
    }

    private void verifyTime(JWTClaimsSet claims) {
        Instant now = clock.instant();
        long skew = policy.clockSkew().getSeconds();

        Date expiration = claims.getExpirationTime();
        if (expiration == null) {
            throw TokenValidationException.of(TokenErrorCode.MISSING_CLAIM, "exp");
        }
        if (!expiration.toInstant().plusSeconds(skew).isAfter(now)) {
            throw TokenValidationException.of(TokenErrorCode.EXPIRED, "exp=" + expiration.toInstant());
        }

        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.toInstant().minusSeconds(skew).isAfter(now)) {
            throw TokenValidationException.of(TokenErrorCode.NOT_YET_VALID, "nbf=" + notBefore.toInstant());
        }

        Date issuedAt = claims.getIssueTime();
        if (issuedAt == null) {
            throw TokenValidationException.of(TokenErrorCode.MISSING_CLAIM, "iat");
        }
        if (issuedAt.toInstant().minusSeconds(skew).isAfter(now)) {
            throw TokenValidationException.of(TokenErrorCode.INVALID_ISSUED_AT, "iat=" + issuedAt.toInstant());
        }
    }

    /**
     * 主体必填：签发侧 {@code TokenSpec} 强制要求，缺失说明 token 不是本模块签的。
     */
    private void verifySubject(JWTClaimsSet claims) {
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw TokenValidationException.of(TokenErrorCode.MISSING_CLAIM, "sub");
        }
    }

    private void verifyIssuer(JWTClaimsSet claims) {
        if (!policy.issuer().equals(claims.getIssuer())) {
            throw TokenValidationException.of(TokenErrorCode.ISSUER_MISMATCH, "iss=" + claims.getIssuer());
        }
    }

    private void verifyAudience(JWTClaimsSet claims) {
        if (!policy.audience().isEmpty()
                && Collections.disjoint(policy.audience(), claims.getAudience())) {
            throw TokenValidationException.of(TokenErrorCode.AUDIENCE_MISMATCH, "aud=" + claims.getAudience());
        }
    }

    private TokenClaims toClaims(JWTClaimsSet claims) {
        Map<String, Object> attributes = new LinkedHashMap<>(claims.getClaims());
        attributes.keySet().removeAll(REGISTERED_CLAIMS);
        return new TokenClaims(
                claims.getSubject(),
                claims.getIssuer(),
                claims.getAudience(),
                toInstant(claims.getIssueTime()),
                toInstant(claims.getExpirationTime()),
                toInstant(claims.getNotBeforeTime()),
                claims.getJWTID(),
                attributes);
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

}
