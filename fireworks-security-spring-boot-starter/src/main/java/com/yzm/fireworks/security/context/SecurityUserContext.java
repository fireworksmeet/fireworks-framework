package com.yzm.fireworks.security.context;

import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 当前登录人上下文（只读门面）
 * <p>
 * 统一封装"从 SecurityContext 获取当前登录用户"的逻辑，业务代码禁止直接操作 SecurityContextHolder。
 * <p>
 * 【约定】本类**只提供读取能力**：身份的写入与清理属于请求生命周期职责，
 * 统一由认证过滤器（{@code SecurityUserAuthenticationFilter}）在请求进入 / 结束时完成，
 * 不对外暴露写入口，避免业务侧伪造身份或装配出与认证链路不一致的 authorities。
 *
 * @author JYuan
 */
public final class SecurityUserContext {

    private SecurityUserContext() {
        throw new AssertionError("Cannot instantiate SecurityUserContext class");
    }

    /**
     * 获取当前登录用户，未登录返回 {@code null}
     */
    public static SecurityUser getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (ObjectUtils.isEmpty(authentication) || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser;
        }
        return null;
    }

    /**
     * 获取当前登录用户，未登录抛 {@code UNAUTHORIZED}
     */
    public static SecurityUser requireUser() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user)) {
            throw new BizException(CommonExceptionStatus.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 获取当前登录用户 ID，未登录返回 {@code null}
     */
    public static Long getUserId() {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.userId();
    }

    /**
     * 获取当前登录用户 ID，未登录抛 {@code UNAUTHORIZED}
     */
    public static Long requireUserId() {
        return requireUser().userId();
    }

    /**
     * 获取当前登录用户名，未登录返回 {@code null}
     */
    public static String getUsername() {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.username();
    }

    /**
     * 获取当前登录用户角色列表，未登录返回空集合
     */
    public static Set<String> getRoles() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user) || ObjectUtils.isEmpty(user.roles())) {
            return Collections.emptySet();
        }
        return user.roles();
    }

    /**
     * 获取当前登录用户权限码列表，未登录返回空集合
     */
    public static Set<String> getPermissions() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user) || ObjectUtils.isEmpty(user.permissions())) {
            return Collections.emptySet();
        }
        return user.permissions();
    }

    /**
     * 判断当前用户是否具备指定角色。
     *
     * <p><b>比较的是 {@link SecurityUser#roles()} 里的原始角色码</b>（如 {@code ADMIN}），
     * 不会自动加 {@code role-prefix}。与 Spring Security 的 {@code hasRole('ADMIN')} 不同——
     * 后者实际比对的是 {@code ROLE_ADMIN} 形式的 authority。
     * 因此这里传 {@code "ROLE_ADMIN"} 会返回 {@code false}。
     */
    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    /**
     * 获取当前登录用户业务扩展字段，未登录返回空 Map
     */
    public static Map<String, Object> getAttributes() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user) || ObjectUtils.isEmpty(user.attributes())) {
            return Collections.emptyMap();
        }
        return user.attributes();
    }

    /**
     * 获取当前登录用户业务扩展字段
     */
    public static <T> T getAttribute(String key) {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.getAttribute(key);
    }

    /**
     * 获取当前登录用户业务扩展字段并转换为指定类型
     */
    public static <T> T getAttribute(String key, Class<T> type) {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.getAttribute(key, type);
    }

    /**
     * 判断当前用户是否已登录
     */
    public static boolean isAuthenticated() {
        return !ObjectUtils.isEmpty(getUser());
    }
}
