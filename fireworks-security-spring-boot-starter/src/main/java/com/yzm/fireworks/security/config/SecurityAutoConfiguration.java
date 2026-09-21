package com.yzm.fireworks.security.config;

import com.yzm.fireworks.security.context.SecurityAuthorityService;
import com.yzm.fireworks.security.context.SecurityUser;
import com.yzm.fireworks.security.filter.HeaderTokenAuthenticationFilter;
import com.yzm.fireworks.security.handler.RestAccessDeniedHandler;
import com.yzm.fireworks.security.handler.RestAuthenticationEntryPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Spring Security 自动配置
 * <p>
 * 统一提供下游服务的认证与鉴权基础设施：
 * <ul>
 *   <li>从网关注入的请求头解析用户身份（{@link HeaderTokenAuthenticationFilter}）</li>
 *   <li>401 / 403 统一 JSON 响应（{@link RestAuthenticationEntryPoint}、{@link RestAccessDeniedHandler}）</li>
 *   <li>注解式权限校验（{@code @RequirePermission}，见 {@link MethodSecurityConfig}）</li>
 *   <li>无状态会话、CSRF 关闭、CORS 统一处理</li>
 * </ul>
 * <p>
 * 【设计原则】框架提供"机制"，业务提供"策略"：角色与权限数据来源由业务实现 {@link SecurityAuthorityService}。
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
@Import(MethodSecurityConfig.class)
@ConditionalOnProperty(prefix = "fireworks.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {

    /**
     * 授权信息加载扩展点默认实现：无角色、无权限
     * <p>
     * 业务侧提供自己的 {@link SecurityAuthorityService} 实现后，本 Bean 自动失效。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityAuthorityService.class)
    public SecurityAuthorityService securityAuthorityService() {
        log.warn("未检测到业务侧的 SecurityAuthorityService 实现，已启用默认实现（角色与权限均为空）。"
                + "如需权限校验，请提供一个 SecurityAuthorityService 实现类。");
        return new SecurityAuthorityService() {
            @Override
            public Set<String> loadRoles(SecurityUser user) {
                return Collections.emptySet();
            }

            @Override
            public Set<String> loadPermissions(SecurityUser user) {
                return Collections.emptySet();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler restAccessDeniedHandler() {
        return new RestAccessDeniedHandler();
    }

    /**
     * 安全过滤链
     * <p>
     * 【说明】认证过滤器在此直接创建，不声明为 {@code @Bean}：Spring Boot 会把容器中
     * 所有 {@code Filter} 类型 Bean 自动注册到 Servlet 容器，而本过滤器只应挂载在
     * Security 链上。不暴露为 Bean 可从源头避免"双重注册"，也与 {@code SystemLogFilter}
     * 仅在配置类内创建的风格保持一致。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityAuthorityService securityAuthorityService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   SecurityProperties securityProperties,
                                                   ObjectProvider<CorsConfigurationSource> corsConfigurationSourceProvider) throws Exception {
        HeaderTokenAuthenticationFilter authenticationFilter =
                new HeaderTokenAuthenticationFilter(securityAuthorityService, securityProperties);

        http
                // 无状态：网关已完成认证，服务端不维护 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 前后端分离 + 网关鉴权，关闭 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // 关闭默认表单登录与 HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 统一异常响应
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 认证过滤器置于用户名密码认证过滤器之前
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // CORS：配置源必须从容器获取（业务可自定义覆盖）。
        // 本类用 @AutoConfiguration，其元注解为 @Configuration(proxyBeanMethods = false)，
        // 配置类内直接调用 @Bean 方法不会被 CGLIB 拦截，而是每次新建实例。
        // 叠加 @ConditionalOnMissingBean 的排除语义后，业务自定义的 CorsConfigurationSource
        // 会被静默忽略（框架仍用自带的默认配置）。
        CorsConfigurationSource corsConfigurationSource = corsConfigurationSourceProvider.getIfAvailable();
        if (securityProperties.isCorsEnabled() && !ObjectUtils.isEmpty(corsConfigurationSource)) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        // 免认证路径
        List<String> permitAllPaths = securityProperties.getPermitAllPaths();
        http.authorizeHttpRequests(registry -> {
            registry.requestMatchers(HttpMethod.OPTIONS).permitAll();
            if (!ObjectUtils.isEmpty(permitAllPaths)) {
                registry.requestMatchers(permitAllPaths.toArray(new String[0])).permitAll();
            }
            registry.anyRequest().authenticated();
        });

        return http.build();
    }

    /**
     * 默认 CORS 配置（业务侧可自定义 {@link CorsConfigurationSource} 覆盖）
     * <p>细项由 {@code fireworks.security.cors.*} 配置，见 {@link SecurityProperties.Cors}
     */
    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    @ConditionalOnProperty(prefix = "fireworks.security", name = "cors-enabled", havingValue = "true",
            matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties) {
        SecurityProperties.Cors cors = securityProperties.getCors();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(cors.getAllowedOriginPatterns());
        configuration.setAllowedMethods(cors.getAllowedMethods());
        configuration.setAllowedHeaders(cors.getAllowedHeaders());
        configuration.setExposedHeaders(cors.getExposedHeaders());
        configuration.setAllowCredentials(cors.isAllowCredentials());
        configuration.setMaxAge(cors.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
