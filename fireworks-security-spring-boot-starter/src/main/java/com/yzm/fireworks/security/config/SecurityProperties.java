package com.yzm.fireworks.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Security 配置属性。
 *
 * <p>认证方式不在这里配置：由业务实现 {@code SecurityUserResolver} 决定
 * （网关头、JWT、Session 等）。框架只负责装配、授权转换与错误响应。
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.security")
@Data
public class SecurityProperties {

    /**
     * 角色前缀：装配 authority 时拼接在角色码前（默认 {@code ROLE_}），
     * 使 {@code hasRole('ADMIN')} 与 {@code hasAuthority('ROLE_ADMIN')} 等价。
     * <p>若业务角色码本身已带前缀，设为空串即可。
     */
    private String rolePrefix = "ROLE_";

    /**
     * 免认证路径，如 {@code /actuator/**}、{@code /health}；仅作用于框架默认链。
     * <p>匹配器由 Spring Security 按环境选择：MVC 环境下是 {@code MvcRequestMatcher}
     * （PathPattern 语义），不是 Ant。{@code /**} 两种写法行为一致，但单段 {@code *} 的语义不同。
     */
    private List<String> permitAllPaths = new ArrayList<>();

    /**
     * 是否启用 CORS（默认 true，由框架默认链处理）；业务自定义链时自行配置。
     * <p>即使为 true，未配置 {@code allowed-origin-patterns} 时也不会启用——默认不放行任何跨域来源。
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
         * <p><b>默认空：不启用 CORS</b>。需要跨域访问时显式配置；不要用 {@code *}
         * 配合 {@link #allowCredentials}，那等于允许任意站点发起带凭证的请求。
         */
        private List<String> allowedOriginPatterns = new ArrayList<>();

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
         * <p>默认关闭：凭证 + 任意源等于把跨域写接口暴露给所有站点。
         */
        private boolean allowCredentials = false;

        /**
         * 预检请求缓存时间（秒）
         */
        private Long maxAge = 3600L;
    }
}
