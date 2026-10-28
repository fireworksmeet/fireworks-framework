package com.yzm.fireworks.token.autoconfigure;

import com.yzm.fireworks.token.core.TokenAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Token 配置属性。
 *
 * <pre>{@code
 * fireworks:
 *   token:
 *     enabled: true
 *     issuer: https://auth.example.com
 *     keys:
 *       - kid: auth-2026-10
 *         algorithm: ES256
 *         private-key-value: |
 *           -----BEGIN PRIVATE KEY-----
 *           ...
 *           -----END PRIVATE KEY-----
 *         public-key-value: |
 *           -----BEGIN PUBLIC KEY-----
 *           ...
 *           -----END PUBLIC KEY-----
 *         signing: true
 * }</pre>
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.token")
public class TokenProperties {

    /**
     * 签发方；写入并校验 JWT 的 iss 声明。使用 JWT 时必填
     */
    private String issuer;

    /**
     * 密钥列表；每把密钥自带算法，因此同一部署可同时验签不同算法
     */
    private List<Key> keys = new ArrayList<>();

    /**
     * 签发策略
     */
    private Issuance issuance = new Issuance();

    /**
     * 校验策略
     */
    private Verification verification = new Verification();

    /**
     * 单把密钥配置。
     * <p>
     * 每种材料有两个字段：{@code -value} 直接写内容，{@code -file} 写 Spring {@link Resource}
     * （{@code file:} / 裸绝对路径 / {@code classpath:}），二者只能设其一。
     * 用字段名区分"值"与"文件"，不需要任何前缀语法。
     * <p>
     * {@code classpath:} 只允许用于公钥：私钥打进 jar 会随制品进入镜像仓库。
     */
    @Data
    public static class Key {

        /**
         * 密钥标识，写入 JWT header 的 kid；必填且唯一
         */
        private String kid;

        /**
         * 该密钥使用的算法；必填
         */
        private TokenAlgorithm algorithm;

        /**
         * PKCS#8 私钥 PEM 原文；与 {@link #privateKeyFile} 二选一
         */
        private String privateKeyValue;

        /**
         * PKCS#8 私钥文件；内容必须是 PEM
         */
        private Resource privateKeyFile;

        /**
         * X.509 公钥 PEM 原文；与 {@link #publicKeyFile} 二选一
         */
        private String publicKeyValue;

        /**
         * X.509 公钥文件；内容必须是 PEM
         */
        private Resource publicKeyFile;

        /**
         * 对称密钥原文；与 {@link #secretFile} 二选一
         */
        private String secretValue;

        /**
         * 对称密钥文件；按原始字节读取，去掉首尾 ASCII 空白
         */
        private Resource secretFile;

        /**
         * 是否为当前签发密钥；全局最多一把
         */
        private boolean signing;
    }

    /**
     * 签发策略。
     */
    @Data
    public static class Issuance {

        /**
         * 默认有效期；TokenSpec 未指定 ttl 时使用
         */
        private Duration defaultTtl = Duration.ofMinutes(15);

        /**
         * 有效期上限，超过即拒绝
         */
        private Duration maxTtl = Duration.ofHours(24);

        /**
         * 默认受众；TokenSpec 未指定 audience 时使用
         */
        private List<String> audience = new ArrayList<>();

        /**
         * 是否自动生成 jti
         */
        private boolean includeJti = true;
    }

    /**
     * 校验策略。
     */
    @Data
    public static class Verification {

        /**
         * exp / nbf / iat 的时间容忍度
         */
        private Duration clockSkew = Duration.ofSeconds(30);

        /**
         * 期望受众；非空时要求交集非空
         */
        private List<String> audience = new ArrayList<>();

        /**
         * 令牌最大长度，超过直接拒绝
         */
        private DataSize maxTokenLength = DataSize.ofKilobytes(8);
    }
}
