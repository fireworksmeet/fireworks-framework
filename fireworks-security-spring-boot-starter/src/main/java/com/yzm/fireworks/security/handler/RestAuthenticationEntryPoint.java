package com.yzm.fireworks.security.handler;

import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.common.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 未认证统一响应
 * <p>
 * 未登录 / Token 无效 / Token 过期时，返回统一 JSON 格式（{@link Result}）。
 * <p>
 * 【日志】下游服务的身份由网关保证，出现未认证说明网关头缺失或异常，属**非预期**情况，故用 WARN
 * （WARN 不触发告警，仅用于观察与趋势分析）。高频场景下如需降噪，应通过采样解决，而非降低级别。
 *
 * @author JYuan
 */
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("未认证访问: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonUtil.stringify(Result.error(CommonExceptionStatus.UNAUTHORIZED)));
    }
}
