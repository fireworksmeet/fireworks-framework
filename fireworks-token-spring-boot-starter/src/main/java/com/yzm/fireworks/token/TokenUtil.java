package com.yzm.fireworks.token;


import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.yzm.fireworks.common.util.ObjectMapperUtil;
import com.yzm.fireworks.common.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static com.yzm.fireworks.common.constants.StringPool.SPACE;

/**
 * JWT Token 工具类
 * <p>
 * 提供 JWT Token 的生成、解析和验证功能。
 *
 * @author JYuan
 */
@Slf4j
public class TokenUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder();

    /**
     * token应该有足够的长度，以抵抗暴力破解，建议至少32字节
     * 推荐:
     * access_token 32字节
     * refresh_token 64字节
     */
    private static final int DEFAULT_TOKEN_BYTES = 32;

    /**
     * 使用Bearer形式
     */
    public static final String TOKEN_PREFIX = "Bearer";

    /**
     * 从请求中提取Token
     *
     * @param request HTTP请求
     * @return Token字符串，如果不存在则返回null
     */
    public static String extractToken(HttpServletRequest request) {
        Assert.notNull(request, "request must not be null");
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        return removePrefixBearer(authorization);
    }

    /**
     * 从请求中提取Token，如果不存在则抛出异常
     *
     * @param request HTTP请求
     * @return Token字符串
     * @throws TokenException 如果Token不存在
     */
    public static String extractTokenOrThrow(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            throw new TokenException("Token not found in request header");
        }
        return token;
    }

    private final JWSSigner signer;
    private final JWSHeader jwsHeader;
    private final JWSVerifier verifier;
    private final JWTProperties jwtProperties;

    public TokenUtil(JWTProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        try {
            this.verifier = new MACVerifier(jwtProperties.getSecret());
            this.signer = new MACSigner(jwtProperties.getSecret());
        } catch (Exception e) {
            throw new TokenException("Failed to initialize TokenUtil", e);
        }
        this.jwsHeader = new JWSHeader(JWSAlgorithm.HS256);
    }

    public static String removePrefixBearer(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return StrUtil.removePrefix(token, TOKEN_PREFIX + SPACE).trim();
    }

    public static String appendPrefixBearer(String token) {
        Assert.hasText(token, "token must not be blank");
        return TOKEN_PREFIX + SPACE + token;
    }

    /**
     * 生成安全的随机token
     */
    public static String generateSecureToken() {
        return generateSecureToken(DEFAULT_TOKEN_BYTES);
    }

    public static String generateSecureToken(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(randomBytes);
        return BASE64_ENCODER.withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 生成JWT类型Token
     */
    public String generateJwtToken(JWTClaim jwtClaim) {
        Assert.notNull(jwtClaim, "jwtClaim must not be null");
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim(jwtProperties.getUserIdKey(), jwtClaim.getUserId())
                .claim(jwtProperties.getExtKey(), jwtClaim.getExt())
                .subject(jwtClaim.getSub()).issuer(jwtProperties.getIss()).audience(jwtProperties.getAud())
                .expirationTime(jwtClaim.getExp()).issueTime(jwtClaim.getIat()).notBeforeTime(jwtClaim.getIat())
                .build();
        SignedJWT jwt = new SignedJWT(this.jwsHeader, claimsSet);
        try {
            jwt.sign(this.signer);
        } catch (JOSEException e) {
            throw new TokenException("Failed to sign JWT token", e);
        }
        return jwt.serialize();
    }

    public JWTClaim parseJwtToken(String jwtToken) {
        Assert.hasText(jwtToken, "jwtToken must not be blank");
        JWTClaimsSet jwtClaimsSet;
        try {
            SignedJWT jwt = SignedJWT.parse(jwtToken);
            Assert.isTrue(jwt.verify(this.verifier), "The jwtToken verification fails");
            jwtClaimsSet = jwt.getJWTClaimsSet();
        } catch (Exception e) {
            throw new TokenException("Failed to parse JWT token", e);
        }
        Map<String, Object> object = jwtClaimsSet.getClaims();
        return ObjectMapperUtil.convertValue(object, JWTClaim.class);
    }
}
