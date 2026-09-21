package com.yzm.fireworks.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Security 配置属性
 * <p>
 * 已在 {@link SecurityAutoConfiguration} 中通过
 * {@code @EnableConfigurationProperties(SecurityProperties.class)} 正确激活。
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.security")
@Data
public class SecurityProperties {

    /**
     * 是否启用自动配置的安全过滤链（默认 true）
     */
    private boolean enabled = true;

    /**
     * 网关透传用户信息的请求头名（默认 X-User-Context）
     */
    private String userContextHeader = "X-User-Context";

    /**
     * 免认证路径（Ant 风格），如 /actuator/**、/health
     */
    private List<String> permitAllPaths = new ArrayList<>();

    /**
     * 是否启用 CORS（默认 true，由 SecurityFilterChain 统一处理）
     */
    private boolean corsEnabled = true;

    /**
     * CORS 细项配置（仅 {@code corsEnabled=true} 且业务未自定义
     * {@code CorsConfigurationSource} 时生效）
     */
    private Cors cors = new Cors();

    /**
     * CORS 配置
     */
    @Data
    public static class Cors {

        /**
         * 允许的源模式（如 {@code https://a.com}、{@code https://*.a.com}）
         * <p>【安全提示】默认 {@code *} 表示放行任意源。当 {@link #allowCredentials} 为 true 时，
         * 意味着任意站点均可携带凭证跨域访问，生产环境建议按需收窄为具体域名。
         */
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

        /**
         * 允许的请求方法
         */
        private List<String> allowedMethods = new ArrayList<>(List.of("*"));

        /**
         * 允许的请求头
         */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /**
         * 允许浏览器读取的响应头（默认空，即只暴露简单响应头）
         */
        private List<String> exposedHeaders = new ArrayList<>();

        /**
         * 是否允许携带凭证（Cookie / Authorization 等）
         */
        private boolean allowCredentials = true;

        /**
         * 预检请求缓存时间（秒）
         */
        private Long maxAge = 3600L;
    }
}
