package com.yzm.fireworks.security.config;

import com.yzm.fireworks.security.annotation.RequirePermission;
import com.yzm.fireworks.security.manager.RequirePermissionAuthorizationManager;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * 方法级安全配置
 * <p>
 * 将 {@link RequirePermission} 接入 Spring Security 原生方法拦截链（而非普通 AOP 切面）：
 * 授权拒绝统一由 {@code AccessDeniedHandler} 输出 403，且拦截顺序可控。
 * <p>
 * 【说明】保留 {@code @EnableMethodSecurity} 默认能力（{@code prePostEnabled = true}），
 * 因此 {@code @PreAuthorize} 等原生注解与 {@code @RequirePermission} **可共存**。
 *
 * @author JYuan
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class MethodSecurityConfig {

    /**
     * 注册 {@link RequirePermission} 的方法前置授权拦截器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "requirePermissionInterceptor")
    public AuthorizationManagerBeforeMethodInterceptor requirePermissionInterceptor(RequirePermissionAuthorizationManager authorizationManager) {

        // 匹配"类上 或 方法上"标注 @RequirePermission 的目标（checkInherited 支持元注解）
        Pointcut pointcut = Pointcuts.union(new AnnotationMatchingPointcut(null, RequirePermission.class, true), new AnnotationMatchingPointcut(RequirePermission.class, true));

        AuthorizationManagerBeforeMethodInterceptor interceptor =
                new AuthorizationManagerBeforeMethodInterceptor(pointcut, authorizationManager);

        // 基于官方 PRE_AUTHORIZE 的数值做相对偏移（200 - 10 = 190）
        // 既保证语义清晰，又确保在 @PreAuthorize 之前触发（Fast-Fail）
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() - 10);
        return interceptor;
    }

    /**
     * {@link RequirePermission} 授权管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public RequirePermissionAuthorizationManager requirePermissionAuthorizationManager() {
        return new RequirePermissionAuthorizationManager();
    }
}
