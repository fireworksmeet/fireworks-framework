package com.yzm.fireworks.security.context;

import java.util.Set;

/**
 * 授权信息加载扩展点（SPI）
 * <p>
 * 框架负责"从请求头解析身份并放入 SecurityContext"，**角色与权限从哪来由业务实现**。
 * 业务侧只需提供一个实现类（Spring Bean），框架会在每次请求认证时调用。
 *
 * <pre>{@code
 *   @Service
 *   @RequiredArgsConstructor
 *   public class SecurityAuthorityServiceImpl implements SecurityAuthorityService {
 *       @Override
 *       public Set<String> loadRoles(SecurityUser user) {
 *           // 从数据库 / 缓存获取角色，如 Set.of("admin")
 *       }
 *
 *       @Override
 *       public Set<String> loadPermissions(SecurityUser user) {
 *           // 从数据库 / 缓存获取权限码，如 Set.of("system:user:add")
 *       }
 *   }
 * }</pre>
 *
 * 【说明】若业务未提供实现，框架使用默认实现（角色与权限均为空集）。
 * 【注意】本方法在**每次请求**认证时调用，实现内部应做缓存，避免高频查库。
 * <p>
 * 【约定】角色码建议统一带 {@code ROLE_} 前缀（如 {@code ROLE_ADMIN}），
 * 以便与 Spring Security 的 {@code hasRole} 语义对齐。
 *
 * @author JYuan
 */
public interface SecurityAuthorityService {

    /**
     * 加载指定用户的角色列表
     *
     * @param user 已从请求头解析出的用户身份（含 userId 等），不为 {@code null}
     * @return 角色码集合；无角色时返回空集合（不应返回 {@code null}）
     */
    Set<String> loadRoles(SecurityUser user);

    /**
     * 加载指定用户的权限码列表
     *
     * @param user 已从请求头解析出的用户身份（含 userId 等），不为 {@code null}
     * @return 权限码集合；无权限时返回空集合（不应返回 {@code null}）
     */
    Set<String> loadPermissions(SecurityUser user);
}
