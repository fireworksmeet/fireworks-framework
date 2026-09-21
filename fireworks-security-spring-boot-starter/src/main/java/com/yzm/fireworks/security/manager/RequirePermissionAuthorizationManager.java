package com.yzm.fireworks.security.manager;

import com.yzm.fireworks.security.annotation.RequirePermission;
import com.yzm.fireworks.security.context.SecurityUser;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link RequirePermission} 的授权管理器
 * <p>
 * 实现 Spring Security 原生扩展点 {@link AuthorizationManager}，
 * 由 {@code AuthorizationManagerBeforeMethodInterceptor} 在方法调用前触发。
 * <p>
 * 【优势】相比 AOP 切面：
 * <ul>
 *   <li>授权拒绝走 Security 原生链路，统一由 {@code AccessDeniedHandler} 输出 403</li>
 *   <li>处于 Security 方法拦截链中，顺序可通过 {@code AuthorizationInterceptorsOrder} 控制</li>
 * </ul>
 * <p>
 * <b>判定顺序</b>：
 * <ol>
 *   <li>无注解 → 放行</li>
 *   <li>未登录 → 拒绝</li>
 *   <li>{@code allowAdmin} 且为超管（角色含 {@code ROLE_ADMIN}）→ 放行</li>
 *   <li>按 {@code logical} 计算权限维度结果</li>
 *   <li>按 {@code logical} 计算角色维度结果</li>
 *   <li>按 {@code relation} 合并两个维度</li>
 *   <li>两个维度均为空 → 仅要求登录，放行</li>
 * </ol>
 *
 * @author JYuan
 */
@Slf4j
public class RequirePermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    /**
     * 超级管理员角色码
     */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * 授权判定（Spring Security 6.4+ 推荐入口）
     */
    @Override
    public AuthorizationResult authorize(Supplier<Authentication> authentication,
                                         MethodInvocation invocation) {
        return decide(authentication, invocation);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 【说明】该抽象方法自 Spring Security 6.4 起已标记 {@code @Deprecated}
     * （推荐入口为 {@link #authorize(Supplier, MethodInvocation)}），但**接口中未提供默认实现**，
     * 因此实现类必须重写；本方法仅作为兼容入口，实际逻辑统一委托至 {@link #decide}。
     * <p>
     * 升级到 Spring Security 7（移除该方法）时，直接删除本重写即可。
     */
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       org.aopalliance.intercept.MethodInvocation invocation) {
        return decide(authentication, invocation);
    }

    /**
     * 授权判定逻辑（唯一实现）
     */
    private AuthorizationDecision decide(Supplier<Authentication> authentication,
                                        MethodInvocation invocation) {
        RequirePermission annotation = findAnnotation(invocation);
        // 无注解（类与方法均未标注）→ 放行
        if (ObjectUtils.isEmpty(annotation)) {
            return new AuthorizationDecision(true);
        }

        SecurityUser user = resolveUser(authentication);
        boolean allowed = matches(user, annotation);

        if (!allowed) {
            log.warn("权限校验未通过, method={}, requiredPermissions={}, requiredRoles={}, userId={}",
                    invocation.getMethod().getName(),
                    annotation.value(),
                    annotation.roles(),
                    ObjectUtils.isEmpty(user) ? null : user.getUserId());
        }
        return new AuthorizationDecision(allowed);
    }

    /**
     * 读取方法或类上的 {@link RequirePermission}（方法优先）
     */
    private RequirePermission findAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Class<?> targetClass = ObjectUtils.isEmpty(invocation.getThis())
                ? method.getDeclaringClass()
                : invocation.getThis().getClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        // 方法优先，其次类（含继承与元注解）
        RequirePermission annotation = AnnotationUtils.findAnnotation(specificMethod, RequirePermission.class);
        if (ObjectUtils.isEmpty(annotation)) {
            annotation = AnnotationUtils.findAnnotation(targetClass, RequirePermission.class);
        }
        return annotation;
    }

    /**
     * 从 Authentication 中解析当前登录用户
     */
    private SecurityUser resolveUser(Supplier<Authentication> authentication) {
        Authentication auth = ObjectUtils.isEmpty(authentication) ? null : authentication.get();
        if (ObjectUtils.isEmpty(auth) || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof SecurityUser securityUser ? securityUser : null;
    }

    /**
     * 判定用户是否满足注解要求
     *
     * @param user       当前登录用户，可为 null（未登录）
     * @param annotation 权限注解
     * @return true 表示允许访问
     */
    private boolean matches(SecurityUser user, RequirePermission annotation) {
        // ① 未登录 → 拒绝
        if (ObjectUtils.isEmpty(user)) {
            return false;
        }

        // ② 超管放行
        if (annotation.allowAdmin() && isAdmin(user)) {
            return true;
        }

        String[] permissions = annotation.value();
        String[] roles = annotation.roles();

        // ⑦ 两个维度均为空 → 仅要求登录
        boolean noPermission = ObjectUtils.isEmpty(permissions);
        boolean noRole = ObjectUtils.isEmpty(roles);
        if (noPermission && noRole) {
            return true;
        }

        boolean logicalAnd = annotation.logical() == RequirePermission.Logical.AND;

        // ③ 权限维度（精确匹配，不支持通配符）
        boolean permissionPass = true;
        if (!noPermission) {
            Set<String> owned = user.getPermissions();
            permissionPass = logicalAnd
                    ? Arrays.stream(permissions).allMatch(p -> contains(owned, p))
                    : Arrays.stream(permissions).anyMatch(p -> contains(owned, p));
        }

        // ④ 角色维度
        boolean rolePass = true;
        if (!noRole) {
            Set<String> owned = user.getRoles();
            rolePass = logicalAnd
                    ? Arrays.stream(roles).allMatch(r -> contains(owned, r))
                    : Arrays.stream(roles).anyMatch(r -> contains(owned, r));
        }

        // ⑤ 合并两个维度
        if (noPermission) {
            return rolePass;
        }
        if (noRole) {
            return permissionPass;
        }
        return annotation.relation() == RequirePermission.Logical.AND
                ? permissionPass && rolePass
                : permissionPass || rolePass;
    }

    /**
     * 判断是否超级管理员（角色含 {@code ROLE_ADMIN}）
     */
    private boolean isAdmin(SecurityUser user) {
        return !ObjectUtils.isEmpty(user) && contains(user.getRoles(), ROLE_ADMIN);
    }

    /**
     * 集合精确匹配（空集合 / 空值统一返回 false）
     */
    private static boolean contains(Collection<String> collection, String value) {
        return !ObjectUtils.isEmpty(collection) && StringUtils.hasText(value) && collection.contains(value);
    }
}
