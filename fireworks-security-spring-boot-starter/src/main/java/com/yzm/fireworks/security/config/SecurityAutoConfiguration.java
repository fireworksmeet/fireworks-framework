package com.yzm.fireworks.security.config;

import com.yzm.fireworks.security.filter.SecurityUserAuthenticationFilter;
import com.yzm.fireworks.security.handler.RestAccessDeniedHandler;
import com.yzm.fireworks.security.handler.RestAuthenticationEntryPoint;
import com.yzm.fireworks.security.handler.SecurityErrorWriter;
import com.yzm.fireworks.security.resolver.SecurityUserResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 自动配置。
 *
 * <p>职责划分：<b>业务回答"是谁"，框架负责"怎么放进 Spring Security"</b>。
 *
 * <ul>
 *   <li>业务：实现 {@link SecurityUserResolver}，解析身份并给出最终的角色与权限；</li>
 *   <li>框架：装配过滤器（{@link SecurityUserAuthenticationFilter}）、角色加前缀转成 authorities、
 *       写入 / 清理 SecurityContext、输出 401 / 403 / 500。</li>
 * </ul>
 *
 * <p>【默认链】仅在容器中<b>没有</b>任何 {@code SecurityFilterChain} 时装配，与 Spring Boot
 * 的默认链同规则；业务一旦声明自己的链，本链连同过滤器一起让位。默认链不含认证机制，
 * 因此"引了依赖但没提供 resolver"时所有请求返回 401，而不是落到表单登录。
 *
 * <p>【业务自定义链】需要在链中复用框架能力时，手动加入过滤器：
 * <pre>{@code
 * http.addFilterBefore(
 *         new SecurityUserAuthenticationFilter(userResolver, properties.getRolePrefix(), errorWriter),
 *         UsernamePasswordAuthenticationFilter.class);
 * }</pre>
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
// 显式排在 Boot 的 security 自动配置之前：两边都按"无 SecurityFilterChain 就装配默认链"判断，
// 只靠类名字母序会随改名/改包静默退回 Boot 默认链（表单登录 + Basic + 随机密码）
@AutoConfigureBefore({
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "fireworks.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityErrorWriter securityErrorWriter() {
        return new SecurityErrorWriter();
    }

    /**
     * 占位 {@link UserDetailsService}：阻止 Spring Boot 的 {@code UserDetailsServiceAutoConfiguration}
     * 生成默认用户。
     *
     * <p>不提供的话，启动日志会打印 {@code Using generated security password: xxx}（明文口令进日志），
     * 且业务若自建链开启表单登录，这组账号会变成可用后门。
     * <p>本框架的认证由 {@link SecurityUserResolver} 完成，不经过 UserDetailsService。
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService noOpUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("本框架不使用 UserDetailsService 认证: " + username);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
        return new RestAuthenticationEntryPoint(errorWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler restAccessDeniedHandler(SecurityErrorWriter errorWriter) {
        return new RestAccessDeniedHandler(errorWriter);
    }

    /**
     * 默认安全过滤链：无认证机制，只做收口（401 / 403 / CORS / 免认证路径）。
     * <p>业务声明自己的 {@code SecurityFilterChain} 后本方法不再生效。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<SecurityUserResolver> resolverProvider,
                                                   SecurityErrorWriter errorWriter,
                                                   SecurityProperties properties,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   ObjectProvider<CorsConfigurationSource> corsConfigurationSourceProvider) throws Exception {
        http
                // 无状态：身份随每次请求携带，服务端不维护 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 无状态调用不依赖 Cookie，关闭 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // 关闭默认表单登录与 HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 统一异常响应
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        SecurityUserResolver resolver = singleOrNull(resolverProvider, "SecurityUserResolver");
        if (resolver == null) {
            log.warn("[Security] 未检测到 SecurityUserResolver，认证不会生效：所有请求将被拒绝（401）。"
                    + "请提供一个实现来解析当前用户。");
        } else {
            SecurityUserAuthenticationFilter authenticationFilter =
                    new SecurityUserAuthenticationFilter(resolver, properties.getRolePrefix(), errorWriter);
            http.addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        // CORS：默认不开启。业务自定义 CorsConfigurationSource 时优先；否则仅在显式配置了
        // allowed-origin-patterns 时构建一份局部的配置源，不把它注册成全局 Bean。
        CorsConfigurationSource corsConfigurationSource =
                singleOrNull(corsConfigurationSourceProvider, "CorsConfigurationSource");
        if (!properties.isCorsEnabled()) {
            http.cors(AbstractHttpConfigurer::disable);
        } else if (corsConfigurationSource != null) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        } else if (!ObjectUtils.isEmpty(properties.getCors().getAllowedOriginPatterns())) {
            CorsConfigurationSource defaultSource = buildCorsConfigurationSource(properties.getCors());
            http.cors(cors -> cors.configurationSource(defaultSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        List<String> permitAllPaths = properties.getPermitAllPaths();
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
     * 取容器中唯一的 Bean；0 个返回 {@code null}，多个直接失败并列出实现类。
     *
     * <p>不用 {@code ObjectProvider#getIfAvailable()}：它在“多个实现”时抛
     * {@code NoUniqueBeanDefinitionException}，栈里只看到自动配置类，排查成本高。
     */
    private static <T> T singleOrNull(ObjectProvider<T> provider, String beanType) {
        List<T> beans = provider.orderedStream().toList();
        if (beans.size() > 1) {
            throw new IllegalStateException("[Security] 检测到 " + beans.size() + " 个 " + beanType
                    + "，请只保留一个：" + beans.stream().map(bean -> bean.getClass().getName()).toList());
        }
        return beans.isEmpty() ? null : beans.getFirst();
    }

    /**
     * 按配置构建 CORS 源（仅框架默认链内部使用，不注册为 Bean）。
     */
    private CorsConfigurationSource buildCorsConfigurationSource(SecurityProperties.Cors cors) {
        if (cors.isAllowCredentials() && cors.getAllowedOriginPatterns().contains("*")) {
            log.warn("CORS 配置为「允许任意来源 + 携带凭证」，任何站点都能发起带凭证的跨域请求。"
                    + "请将 fireworks.security.cors.allowed-origin-patterns 收窄为具体域名。");
        }

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
