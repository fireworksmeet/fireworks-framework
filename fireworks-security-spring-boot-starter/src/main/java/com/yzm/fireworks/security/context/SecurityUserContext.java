package com.yzm.fireworks.security.context;

import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 当前登录人上下文（只读门面）
 * <p>
 * 统一封装"从 SecurityContext 获取当前登录用户"的逻辑，业务代码禁止直接操作 SecurityContextHolder。
 * <p>
 * 【约定】本类**只提供读取能力**：身份的写入与清理属于请求生命周期职责，
 * 统一由认证过滤器（{@code HeaderTokenAuthenticationFilter}）在请求进入 / 结束时完成，
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
     * 获取当前登录用户，以 Optional 返回
     */
    public static Optional<SecurityUser> getUserOptional() {
        return Optional.ofNullable(getUser());
    }

    /**
     * 获取当前登录用户 ID，未登录返回 {@code null}
     */
    public static Long getUserId() {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.getUserId();
    }

    /**
     * 获取当前登录用户 ID，未登录抛 {@code UNAUTHORIZED}
     */
    public static Long requireUserId() {
        return requireUser().getUserId();
    }

    /**
     * 获取当前登录用户名，未登录返回 {@code null}
     */
    public static String getUsername() {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.getUsername();
    }

    /**
     * 获取租户 ID，未登录返回 {@code null}
     */
    public static String getTenantId() {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.getTenantId();
    }

    /**
     * 获取当前登录用户角色列表，未登录返回空集合
     */
    public static Set<String> getRoles() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user) || ObjectUtils.isEmpty(user.getRoles())) {
            return Collections.emptySet();
        }
        return user.getRoles();
    }

    /**
     * 获取当前登录用户权限码列表，未登录返回空集合
     */
    public static Set<String> getPermissions() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user) || ObjectUtils.isEmpty(user.getPermissions())) {
            return Collections.emptySet();
        }
        return user.getPermissions();
    }

    /**
     * 判断当前用户是否具备指定角色
     */
    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    /**
     * 获取当前登录用户业务扩展字段，未登录返回空 Map
     */
    public static Map<String, Object> getAttributes() {
        SecurityUser user = getUser();
        if (ObjectUtils.isEmpty(user) || ObjectUtils.isEmpty(user.getAttributes())) {
            return Collections.emptyMap();
        }
        return user.getAttributes();
    }

    /**
     * 获取当前登录用户业务扩展字段
     */
    public static <T> T getAttribute(String key) {
        SecurityUser user = getUser();
        return ObjectUtils.isEmpty(user) ? null : user.getAttribute(key);
    }

    /**
     * 判断当前用户是否已登录
     */
    public static boolean isAuthenticated() {
        return !ObjectUtils.isEmpty(getUser());
    }
}
