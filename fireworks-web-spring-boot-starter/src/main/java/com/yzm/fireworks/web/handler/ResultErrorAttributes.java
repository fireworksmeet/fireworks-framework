package com.yzm.fireworks.web.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * 统一错误响应装配
 * <p>
 * 实现 Spring Boot 原生扩展点 {@link org.springframework.boot.web.servlet.error.ErrorAttributes}，
 * 由容器默认的 {@code BasicErrorController} 在 {@code /error} 上调用，把错误响应体组织成与业务接口
 * 一致的 {@link Result} 结构（{@code code / message / system}）。
 * <p>
 * <b>为什么不自定义 ErrorPage + Controller</b>：Spring Boot 已内置完整的 {@code /error} 链路
 * （{@code ErrorPageRegistrar} → {@code BasicErrorController} → {@code ErrorAttributes}）。
 * 自定义 ErrorPage 会覆盖该链路，导致 {@code BasicErrorController} 成为永远不会被触发的"僵尸" Bean，
 * 并引入私有路径命名、手工写 JSON、HTTP 状态被强制重置为 200 等副作用。此处只替换
 * {@code ErrorAttributes}，其余全部交由官方机制处理。
 * <p>
 * <b>HTTP 状态</b>由 {@code BasicErrorController} 按容器记录的真实状态码返回（404 / 405 / 500 等），
 * 与 {@code GlobalExceptionHandler} 的策略保持一致——HTTP 状态表达协议层语义，业务码表达业务语义。
 * <p>
 * <b>日志分级</b>：业务异常用 WARN（非预期但可容忍，不触发告警）；系统异常用 ERROR 并保留堆栈；
 * 无异常对象的容器错误中，404 用 DEBUG 以免被扫描流量刷屏、容器层 5xx 用 ERROR、其余（401/403 等）用 WARN。
 *
 * @author JYuan
 */
@Slf4j
public class ResultErrorAttributes extends DefaultErrorAttributes {

    /**
     * 同一请求只记录一次日志（HTML 与 JSON 视图可能各触发一次），存于请求作用域
     */
    private static final String LOGGED_ATTRIBUTE = ResultErrorAttributes.class.getName() + ".LOGGED";

    private final ObjectMapper objectMapper;

    public ResultErrorAttributes(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        HttpServletRequest request = resolveRequest(webRequest);
        Throwable error = getError(webRequest);

        ExceptionStatus status = resolveStatus(request, error);
        logOnce(webRequest, request, error, status);

        // 复用 Result 的装配逻辑，保证响应结构与业务接口完全一致（含 system 字段）；
        // Result 上的 @JsonInclude(NON_NULL) 会让空的 data / error 字段自动省略
        return objectMapper.convertValue(Result.error(status), new TypeReference<>() {});
    }

    /**
     * 取出底层 {@link HttpServletRequest}（非 Servlet 环境下为 null）
     */
    private HttpServletRequest resolveRequest(WebRequest webRequest) {
        return webRequest instanceof ServletWebRequest servletWebRequest ? servletWebRequest.getRequest() : null;
    }

    /**
     * 解析业务错误码：业务异常以其自身状态为准，其余按容器记录的 HTTP 状态映射
     */
    private ExceptionStatus resolveStatus(HttpServletRequest request, Throwable error) {
        if (error instanceof BizException bizException && !ObjectUtils.isEmpty(bizException.getStatus())) {
            return bizException.getStatus();
        }
        Integer statusCode = resolveStatusCode(request);
        if (ObjectUtils.isEmpty(statusCode)) {
            return CommonExceptionStatus.SERVER_ERROR;
        }
        return switch (statusCode) {
            case 401 -> CommonExceptionStatus.UNAUTHORIZED;
            case 403 -> CommonExceptionStatus.FORBIDDEN;
            case 404 -> CommonExceptionStatus.NOT_FOUND;
            case 405 -> CommonExceptionStatus.METHOD_NOT_SUPPORTED;
            case 429 -> CommonExceptionStatus.SERVER_DEGRADE;
            case 400, 415 -> CommonExceptionStatus.REQUEST_PARAMS_ERROR;
            default -> statusCode >= 500 ? CommonExceptionStatus.SERVER_ERROR : CommonExceptionStatus.REQUEST_PARAMS_ERROR;
        };
    }

    /**
     * 读取容器记录的 HTTP 状态码
     */
    private Integer resolveStatusCode(HttpServletRequest request) {
        if (ObjectUtils.isEmpty(request)) {
            return null;
        }
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return statusCode instanceof Integer value ? value : null;
    }

    /**
     * 记录错误日志（同一请求仅一次）
     */
    private void logOnce(WebRequest webRequest, HttpServletRequest request, Throwable error, ExceptionStatus status) {
        if (!ObjectUtils.isEmpty(webRequest.getAttribute(LOGGED_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST))) {
            return;
        }
        webRequest.setAttribute(LOGGED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);

        String uri = ObjectUtils.isEmpty(request) ? null : request.getRequestURI();
        if (error instanceof BizException) {
            // 业务异常：非预期但可容忍，用 WARN（不触发告警），不打印堆栈
            log.warn("容器兜底业务异常: uri={}, bizCode={}", uri, status.getCode());
            return;
        }
        if (!ObjectUtils.isEmpty(error)) {
            log.error("容器兜底系统异常: uri={}, exceptionType={}", uri, error.getClass().getName(), error);
            return;
        }

        Integer statusCode = resolveStatusCode(request);
        if (statusCode != null && statusCode == 404) {
            // 404 多由扫描流量产生，高频且无行动价值，故用 debug
            log.debug("容器错误(无异常对象): uri={}, httpStatus={}", uri, statusCode);
        } else if (statusCode != null && statusCode >= 500) {
            // 容器层 5xx：本身就是故障，需人工介入
            log.error("容器错误(无异常对象): uri={}, httpStatus={}", uri, statusCode);
        } else {
            // 其余 4xx（401 / 403 等）：非预期但可容忍
            log.warn("容器错误(无异常对象): uri={}, httpStatus={}", uri, statusCode);
        }
    }
}
