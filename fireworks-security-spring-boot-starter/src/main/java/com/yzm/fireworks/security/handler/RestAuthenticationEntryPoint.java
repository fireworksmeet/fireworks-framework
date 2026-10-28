package com.yzm.fireworks.security.handler;

import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 401 未认证统一响应。
 *
 * <p><b>不发送 {@code WWW-Authenticate}</b>：框架不拥有认证方式，声明具体的质询方案
 * （如 {@code Bearer}）会诱导客户端做无效重试，也容易让人误以为"配了头就等于配了认证"。
 * 需要严格遵循 RFC 9110 或对外声明方案时，业务自行声明 {@code AuthenticationEntryPoint}
 * 即可覆盖本实现。
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 401 是常态流量（token 过期、匿名探测），不在 WARN 记录；需要审计时看访问日志或指标。
        // 「为什么没有身份」由认证过滤器在 resolver 异常时记录，避免同一失败出现两条日志。
        log.debug("未认证访问: {} {}", request.getMethod(), request.getRequestURI());
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), CommonExceptionStatus.UNAUTHORIZED);
    }
}
