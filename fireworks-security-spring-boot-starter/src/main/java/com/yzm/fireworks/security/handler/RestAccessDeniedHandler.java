package com.yzm.fireworks.security.handler;

import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.common.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 403 无权限统一响应
 * <p>
 * 已登录但权限不足（如 {@code @RequirePermission} 校验失败）时，返回统一 JSON 格式（{@link Result}）。
 * <p>
 * 【日志】权限不足属**非预期**情况（可能是越权尝试），用 WARN 记录观察（WARN 不触发告警）；
 * 不输出异常堆栈与权限表达式明细，避免日志体积被外部输入放大。
 *
 * @author JYuan
 */
@Slf4j
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("无权限访问: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonUtil.stringify(Result.error(CommonExceptionStatus.FORBIDDEN)));
    }
}
