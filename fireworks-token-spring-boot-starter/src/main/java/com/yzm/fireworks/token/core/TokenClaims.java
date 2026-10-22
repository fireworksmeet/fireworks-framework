package com.yzm.fireworks.token.core;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验通过的声明集合（RFC 7519 Claims Set）。
 * <p>
 * 读模型。不可变，唯一正常来源是 {@link JwtVerifier#verify(String)}；框架不提供"只解码不验签"的入口，
 * 因此不存在"未校验但同类型"的对象。
 * <p>
 * 只包含调用方可以据以决策的声明。{@code kid}、{@code typ} 属于 JOSE header，已在校验阶段使用，
 * 不出现在这里（只进日志与指标）。
 *
 * @author JYuan
 */
public record TokenClaims(
        String subject,
        String issuer,
        List<String> audience,
        Instant issuedAt,
        Instant expiresAt,
        Instant notBefore,
        String jwtId,
        Map<String, Object> attributes
) {

    public TokenClaims {
        audience = audience == null ? List.of() : List.copyOf(audience);
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * 读取业务声明，取不到返回 {@code null}；不做类型转换。
     */
    public Object attribute(String name) {
        return attributes.get(name);
    }

    /**
     * 只输出注册声明，不输出业务声明，避免凭证内容随日志泄漏。
     */
    @NotNull
    @Override
    public String toString() {
        return "TokenClaims{subject=" + subject
                + ", issuer=" + issuer
                + ", audience=" + audience
                + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt
                + ", notBefore=" + notBefore
                + ", jwtId=" + jwtId
                + ", attributes=" + attributes.size() + " item(s)}";
    }
}
