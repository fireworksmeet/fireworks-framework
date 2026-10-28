package com.yzm.fireworks.security.filter;

import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.security.context.Authorities;
import com.yzm.fireworks.security.context.SecurityUser;
import com.yzm.fireworks.security.handler.SecurityErrorWriter;
import com.yzm.fireworks.security.resolver.SecurityUserResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 身份认证过滤器（框架装配，业务只提供 {@link SecurityUserResolver}）。
 *
 * <p>职责：
 * <ol>
 *   <li>调用 resolver 拿到 {@link SecurityUser}；</li>
 *   <li>把 roles / permissions 组装成 {@code GrantedAuthority}（角色加 {@code role-prefix}）；</li>
 *   <li>写入 {@code SecurityContext}，请求结束后清理。</li>
 * </ol>
 *
 * <p>【失败处理】
 * <ul>
 *   <li>无凭证（resolver 返回 {@code null}）：不设置身份，继续放行，由授权层输出 401；</li>
 *   <li>返回非 {@code null}：视为已认证，<b>框架不校验字段组成</b>（是否有 {@code userId}、
 *       是否带角色/权限，都交给业务）——游客可能只带 {@code attributes}，框架无从判断；</li>
 *   <li>resolver 抛异常：认证<b>未完成</b>，记 ERROR 并直接输出 <b>500</b>（fail-closed），
 *       不继续过滤链。异常类型由业务决定，框架不据此判断是 401 还是 500——契约已规定
 *       “客户端凭证问题返回 {@code null}”，走到异常就说明是故障。</li>
 * </ul>
 *
 * <p>【预检请求】CORS 预检（{@code OPTIONS} + {@code Access-Control-Request-Method}）直接放行：
 * 它按规范不带凭证，调用 resolver 没有意义，还会产生无谓的错误日志。
 * 放行<b>不等于</b>允许访问——授权层仍按 {@code permitAll(OPTIONS)} 判断。
 *
 * <p>【注册方式】本过滤器<b>不是 Spring Bean</b>：Spring Boot 会把容器中所有 {@code Filter}
 * 类型 Bean 自动注册到 Servlet 容器，而本过滤器只应挂在 Security 链上。
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class SecurityUserAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityUserResolver securityUserResolver;

    private final String rolePrefix;

    private final SecurityErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 预检请求不带凭证，解析必然无功而返；直接放行，由授权层按 permitAll(OPTIONS) 判断
        if (CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!authenticate(request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理，避免线程复用导致身份串号
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * @return {@code true} 继续过滤链；{@code false} 表示已写出 500
     */
    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) {
        // 已有身份（更早的过滤器完成认证）：尊重先认证者，不覆盖业务自己的 principal
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return true;
        }

        SecurityUser securityUser;
        try {
            securityUser = securityUserResolver.resolve(request);
        } catch (Exception e) {
            // 认证未完成：fail-closed。用 500 而不是 401，避免把依赖故障误报成“没登录”
            log.error("解析用户身份失败，按服务端故障处理: resolver={}",
                    securityUserResolver.getClass().getSimpleName(), e);
            errorWriter.write(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    CommonExceptionStatus.SERVER_ERROR);
            return false;
        }

        if (ObjectUtils.isEmpty(securityUser)) {
            // 无凭证：由 AuthenticationEntryPoint 统一输出 401
            return true;
        }
        Authorities authorities = new Authorities(securityUser.roles(), securityUser.permissions());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        securityUser, null, authorities.toGrantedAuthorities(rolePrefix));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    /**
     * 空壳判断：既没有任何身份标识（{@code userId} / {@code username}），也没有任何角色或权限。
     *
     * <p>空壳对象如果被当作已认证，就能通过 {@code anyRequest().authenticated()}——这是真实越权口子，
     * 所以必须挡掉。但判据不能是「{@code userId} 是否为空」：游客身份可能只有 {@code username}，
     * 或只带一个角色码，框架不该假设业务的身份模型。
     */
}
