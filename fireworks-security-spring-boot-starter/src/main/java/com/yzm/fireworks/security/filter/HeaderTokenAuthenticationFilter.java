package com.yzm.fireworks.security.filter;

import com.yzm.fireworks.common.util.Base64Util;
import com.yzm.fireworks.common.util.JsonUtil;
import com.yzm.fireworks.security.config.SecurityProperties;
import com.yzm.fireworks.security.context.SecurityAuthorityService;
import com.yzm.fireworks.security.context.SecurityUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 请求头 Token 认证过滤器（下游服务使用）
 * <p>
 * 从请求头中提取网关注入的用户信息（URL-Safe Base64 编码的 JSON），解析后构建 Authentication 并放入 SecurityContext。
 * <p>
 * 【设计】本过滤器只做"身份识别"与"授权信息装配"，不负责"数据来源"——角色与权限由业务侧
 * {@link SecurityAuthorityService} 提供。
 * <p>
 * 【授权装配】角色与权限**合并**写入 {@code authorities}，语义边界仍由 {@link SecurityUser} 的
 * {@code roles} / {@code permissions} 两个字段保持，供 {@code @RequirePermission} 分维度判定。
 * <p>
 * 【注册方式】本过滤器**不是 Spring Bean**，由 {@code SecurityAutoConfiguration} 在构建
 * {@code SecurityFilterChain} 时直接创建。原因是 Spring Boot 会把容器中所有 {@code Filter}
 * 类型 Bean 自动注册到 Servlet 容器，而本过滤器只应挂载在 Security 链上。
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class HeaderTokenAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityAuthorityService securityAuthorityService;

    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String base64UserInfo = request.getHeader(securityProperties.getUserContextHeader());

        if (StringUtils.hasText(base64UserInfo)) {
            try {
                String userInfoJson = Base64Util.decodeUrl(base64UserInfo);
                SecurityUser securityUser = JsonUtil.deserialize(userInfoJson, SecurityUser.class);
                if (ObjectUtils.isEmpty(securityUser) || ObjectUtils.isEmpty(securityUser.getUserId())) {
                    // 网关注入的头必然带 userId，缺失说明请求头异常或被伪造，属非预期情况
                    log.warn("用户信息请求头无可用的 userId，忽略认证, header={}", securityProperties.getUserContextHeader());
                } else {
                    authenticate(securityUser);
                }
            } catch (Exception e) {
                // 非预期输入：用 warn 便于观察（不触发告警）；不打印堆栈，避免日志体积被外部输入放大
                log.warn("解析用户信息请求头失败, header={}, reason={}",
                        securityProperties.getUserContextHeader(), e.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理上下文，避免线程复用导致身份串号
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 加载授权信息并注入 SecurityContext
     */
    private void authenticate(SecurityUser securityUser) {
        // 角色与权限从业务侧加载（框架不做来源假设）
        Set<String> roles = securityAuthorityService.loadRoles(securityUser);
        Set<String> permissions = securityAuthorityService.loadPermissions(securityUser);

        securityUser.setRoles(roles);
        securityUser.setPermissions(permissions);

        // 角色与权限统一装配为 authorities（去重，保留顺序）
        Set<String> authorities = new LinkedHashSet<>();
        if (!ObjectUtils.isEmpty(roles)) {
            authorities.addAll(roles);
        }
        if (!ObjectUtils.isEmpty(permissions)) {
            authorities.addAll(permissions);
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                securityUser, null, AuthorityUtils.createAuthorityList(authorities.toArray(new String[0])));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
