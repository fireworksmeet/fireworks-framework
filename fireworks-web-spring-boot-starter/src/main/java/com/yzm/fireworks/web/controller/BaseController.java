package com.yzm.fireworks.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;


/**
 * Tomcat 错误页兜底控制器。
 *
 * <p><b>为什么不直接 return Result？</b><br>
 * 当异常由 Tomcat 转发到此端点时，Servlet 容器已处于"错误转发"状态。
 * Spring MVC 消息转换器（Jackson HttpMessageConverter）在序列化响应时，
 * 若检测到 response 已经处于异常状态，会抛出 {@code HttpMessageNotWritableException}，
 * 该异常会被 {@link com.yzm.fireworks.web.handler.GlobalExceptionHandler} 再次捕获，
 * 形成二次异常链，最终进入 {@code handleException} 兜底方法，造成混乱。
 *
 * <p><b>解决方案：</b>绕过 Spring MVC 消息转换器，直接通过
 * {@code response.getWriter().write(...)} 输出 JSON 字符串，
 * 同时手动重置 status 为 200，确保客户端能正常解析响应体。
 *
 * @author JYuan
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
public class BaseController {

    private final ObjectMapper objectMapper;

    /**
     * HTTP status code → 可读描述映射，用于 exception 为 null 时提升日志可读性。
     * （Security sendError、404 等场景下 ERROR_EXCEPTION attribute 不会被设置）
     */
    private static final Map<Integer, String> STATUS_DESC = Map.of(
            400, "Bad Request",
            401, "Unauthorized（未认证，通常由 Spring Security 触发）",
            403, "Forbidden（权限不足，通常由 Spring Security 触发）",
            404, "Not Found（路由不存在）",
            405, "Method Not Allowed",
            415, "Unsupported Media Type",
            429, "Too Many Requests",
            500, "Internal Server Error",
            503, "Service Unavailable"
    );

    /**
     * 处理抛给 Tomcat 的异常（错误页兜底）。
     *
     * <p>直接写入 response，不经过 Spring MVC 消息转换器，避免二次异常。
     */
    @RequestMapping("/tomcat-exception")
    public void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        // sendError(status, message) 时 Tomcat 会写入此 attribute，exception 为 null 时可作为补充信息
        String errorMessage = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        Result<?> result;

        if (!ObjectUtils.isEmpty(exception)) {
            if (exception instanceof BizException bizException) {
                // 业务异常：warn 级别，不需要完整堆栈
                log.warn("Tomcat兜底业务异常: uri={}, httpStatus={}, bizCode={}",
                        requestUri, statusCode, bizException.getStatus());
                result = Result.error(bizException.getStatus());
            } else {
                // 系统异常（NPE、DB 错误等）：error 级别 + 完整堆栈
                log.error("Tomcat兜底系统异常: uri={}, httpStatus={}, exceptionType={}",
                        requestUri, statusCode, exception.getClass().getName(), exception);
                result = Result.fromErrorMessage(exception.getMessage());
            }
        } else {
            // exception 为 null：通常为 Security 401/403、主动 sendError() 或路由 404 等
            // 此时依赖 status code + errorMessage attribute 尽量还原现场
            String statusDesc = STATUS_DESC.getOrDefault(statusCode, "Unknown");
            log.warn("Tomcat错误页(无异常对象): uri={}, httpStatus={}, statusDesc={}, errorMessage={}",
                    requestUri, statusCode, statusDesc, errorMessage);

            if (statusCode != null) {
                result = switch (statusCode) {
                    case 401 -> Result.error(CommonExceptionStatus.UNAUTHORIZED);
                    case 403 -> Result.error(CommonExceptionStatus.FORBIDDEN);
                    // 404 在 Security 场景下可能是路由不存在或资源不存在
                    case 404 -> Result.error(CommonExceptionStatus.NOT_FOUND);
                    default -> Result.error(CommonExceptionStatus.SERVER_ERROR);
                };
            } else {
                result = Result.error(CommonExceptionStatus.SERVER_ERROR);
            }
        }

        writeJson(response, result);
    }

    /**
     * 直接将 Result 序列化为 JSON 写入 response，跳过 Spring MVC 消息转换器。
     *
     * <p>同时将 HTTP status 重置为 200，确保客户端（尤其是 Feign/RestTemplate 等）
     * 不会因 5xx/4xx status 触发额外的错误处理逻辑，由业务层通过 Result.code 判断结果。
     */
    private void writeJson(HttpServletResponse response, Result<?> result) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(result));
        response.getWriter().flush();
    }
}