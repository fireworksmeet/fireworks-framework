package com.yzm.fireworks.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * <p>
 * 标注在 Controller / Service 方法（或类）上，要求当前登录用户满足指定的权限 / 角色条件。
 * 由 {@code RequirePermissionAuthorizationManager} 通过 Spring Security 原生方法拦截器执行。
 * <p>
 * <b>判定顺序</b>：
 * <ol>
 *   <li>未登录 → 拒绝</li>
 *   <li>{@link #allowAdmin()} 为 true 且当前用户是超管（角色含 {@code ROLE_ADMIN}）→ 放行</li>
 *   <li>按 {@link #logical()} 计算 {@link #value()}（权限）维度结果</li>
 *   <li>按 {@link #logical()} 计算 {@link #roles()}（角色）维度结果</li>
 *   <li>按 {@link #relation()} 合并第 3、4 步结果</li>
 *   <li>{@code value} 与 {@code roles} 均为空 → 仅要求登录，放行</li>
 * </ol>
 *
 * <pre>{@code
 *   // 有 add 权限即可
 *   @RequirePermission("system:user:add")
 *
 *   // 有 add 或 edit 权限
 *   @RequirePermission(value = {"system:user:add", "system:user:edit"})
 *
 *   // 有 add 且 edit 权限
 *   @RequirePermission(value = {"system:user:add", "system:user:edit"}, logical = Logical.AND)
 *
 *   // 是 admin 角色，或拥有 order:export 权限
 *   @RequirePermission(roles = "admin", value = "order:export")
 *
 *   // 既是 finance 角色，又有 order:export 权限
 *   @RequirePermission(roles = "finance", value = "order:export", relation = Logical.AND)
 * }</pre>
 *
 * @author JYuan
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 需要的权限码（如 {@code system:user:add}），精确匹配，不支持通配符
     * <p>为空表示不校验权限维度
     */
    String[] value() default {};

    /**
     * 需要的角色码（如 {@code admin}）
     * <p>为空表示不校验角色维度
     */
    String[] roles() default {};

    /**
     * 【value 数组内部、roles 数组内部】的表达式逻辑
     */
    Logical logical() default Logical.OR;

    /**
     * 【value 维度 与 roles 维度 之间】的逻辑关系
     * <p><b>注意</b>：默认 OR，即"满足任一维度即可"；要求同时满足请显式指定 AND。
     */
    Logical relation() default Logical.OR;

    /**
     * 是否允许超级管理员直接放行（角色含 {@code ROLE_ADMIN}），默认 true
     */
    boolean allowAdmin() default true;

    /**
     * 表达式逻辑
     */
    enum Logical {
        AND,
        OR
    }
}
