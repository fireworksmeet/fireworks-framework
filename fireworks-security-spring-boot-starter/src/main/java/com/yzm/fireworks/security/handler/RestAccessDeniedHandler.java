package com.yzm.fireworks.security.handler;

import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.security.context.SecurityUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 403 无权限统一响应。
 *
 * <p>已登录但权限不足（{@code @PreAuthorize} 校验失败）时返回统一 JSON。
 * 日志只记方法、路径与 userId，不输出权限表达式明细，避免日志被外部输入放大。
 *
 * <p><b>级别为 DEBUG</b>：403 与 401 一样属于客户端常态（点到了没权限的菜单、UI 未隐藏按钮），
 * 用 WARN 会在后台类应用里刷屏。需要统计或告警时用 HTTP 指标（按 status 过滤），
 * 需要安全审计时写入独立审计日志——不要靠提升应用日志级别来实现。
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.debug("无权限访问: {} {}, userId={}", request.getMethod(), request.getRequestURI(),
                SecurityUserContext.getUserId());
        errorWriter.write(response, HttpStatus.FORBIDDEN.value(), CommonExceptionStatus.FORBIDDEN);
    }
}
