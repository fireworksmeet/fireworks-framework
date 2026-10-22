package com.yzm.fireworks.token.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 签发请求：描述"要签一个什么样的令牌"，不是令牌本身。
 * <p>
 * 写模型。调用方给出主体、有效期与业务声明；{@code iat} / {@code exp} / {@code nbf} /
 * {@code kid} / {@code iss} 与 header 一律由框架补齐，不接受外部指定（否则可绕过 TTL 上限）。
 * <p>
 * 命名用 {@code Spec} 而不是 {@code Request} / {@code Payload}：它是不可变的期望输入，
 * 由签发方补齐默认值（与 Kubernetes 的 {@code PodSpec} 同一语义）。
 *
 * @author JYuan
 */
public final class TokenSpec {

    /**
     * 由框架管理的保留声明名，业务不得通过 {@code attribute} 写入。
     */
    private static final Set<String> RESERVED_CLAIMS = Set.of(
            "iss", "sub", "aud", "exp", "nbf", "iat", "jti", "typ");

    private final String subject;
    private final Duration ttl;
    private final List<String> audience;
    private final Map<String, Object> attributes;
    private final String jwtId;

    private TokenSpec(Builder builder) {
        this.subject = builder.subject;
        this.ttl = builder.ttl;
        this.audience = List.copyOf(builder.audience);
        // Map.copyOf 不接受 null value，而声明的 JSON null 是合法的
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
        this.jwtId = builder.jwtId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 主体 ID（{@code sub}），必填；框架不解释其格式，多类型主体可用命名空间（如 {@code admin:1001}）。
     */
    public String subject() {
        return subject;
    }

    /**
     * 有效期；{@code null} 表示使用配置中的 default-ttl。
     */
    public Duration ttl() {
        return ttl;
    }

    /**
     * 受众；为空表示使用配置中的 issuance.audience。
     */
    public List<String> audience() {
        return audience;
    }

    /**
     * 业务声明（只读）。
     */
    public Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * 令牌 ID；{@code null} 表示由框架生成。
     */
    public String jwtId() {
        return jwtId;
    }

    /**
     * 签发请求构造器。
     */
    public static final class Builder {

        private String subject;
        private Duration ttl;
        private final List<String> audience = new ArrayList<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private String jwtId;

        private Builder() {
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder audience(String... audience) {
            if (audience != null) {
                for (String item : audience) {
                    if (item != null && !item.isBlank()) {
                        this.audience.add(item);
                    }
                }
            }
            return this;
        }

        public Builder audience(Collection<String> audience) {
            if (audience != null) {
                audience.stream()
                        .filter(item -> item != null && !item.isBlank())
                        .forEach(this.audience::add);
            }
            return this;
        }

        public Builder attribute(String name, Object value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("claim name must not be blank");
            }
            this.attributes.put(name, value);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        public Builder jwtId(String jwtId) {
            this.jwtId = jwtId;
            return this;
        }

        /**
         * @throws IllegalArgumentException 主体为空、TTL 非正、声明名与保留名冲突、声明值不是 JSON 可表达类型
         */
        public TokenSpec build() {
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("token subject must not be blank");
            }
            if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
                throw new IllegalArgumentException("token ttl must be positive: " + ttl);
            }
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String name = entry.getKey();
                if (RESERVED_CLAIMS.contains(name)) {
                    throw new IllegalArgumentException("claim '" + name + "' is reserved by the framework");
                }
                assertJsonValue(name, entry.getValue());
            }
            return new TokenSpec(this);
        }

        private static void assertJsonValue(String name, Object value) {
            if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                return;
            }
            if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
                return;
            }
            throw new IllegalArgumentException("claim '" + name + "' must be a JSON-compatible value, but was "
                    + value.getClass().getName());
        }
    }
}
