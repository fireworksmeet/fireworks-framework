package com.yzm.fireworks.web.handler;

import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static com.yzm.fireworks.common.enums.CommonExceptionStatus.MEDIA_TYPE_NOT_ACCEPTABLE;
import static com.yzm.fireworks.common.enums.CommonExceptionStatus.MEDIA_TYPE_NOT_SUPPORTED;
import static com.yzm.fireworks.common.enums.CommonExceptionStatus.METHOD_NOT_SUPPORTED;
import static com.yzm.fireworks.common.enums.CommonExceptionStatus.NOT_FOUND;
import static com.yzm.fireworks.common.enums.CommonExceptionStatus.REQUEST_PARAMS_ERROR;
import static com.yzm.fireworks.common.enums.CommonExceptionStatus.SERVER_DEGRADE;


/**
 * 全局异常处理器
 * <p>
 * 【日志分级】遵循 `docs/conventions/observability.md` §一：**告警阈值只设在 ERROR**，
 * 因此"非预期但可容忍/已兜底"的情况一律用 WARN——WARN 不触发告警，仅用于观察与趋势分析。
 * 只有"流程已失败、功能受损、需要人工介入"才用 ERROR。
 * <p>
 * 【降噪】高频异常（参数校验失败、404 等）如需控制日志量，应通过采样或聚合统计解决，
 * 而不是降低日志级别——降级会让异常信号淹没在正常流水里。
 *
 * @author JYuan
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 校验失败且无法提取具体原因时的兜底提示
     */
    private static final String PARAM_VALIDATION_FAILED = "参数校验失败";

    /**
     * 参数校验异常：请求参数不满足校验约束
     * <p>
     * 属客户端传参不合法引起的**非预期**交互错误，按规范用 WARN（WARN 不触发告警），
     * 且不打印堆栈——校验框架的内部调用链对排查无参考价值，只会放大日志体积。
     * <p>
     * 前端通常已有必填校验，此处仅为服务端兜底，故只返回单条提示信息。
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({BindException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public Result<?> handleValidationException(Exception e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return Result.error(REQUEST_PARAMS_ERROR.getCode(), resolveValidationMessage(e));
    }

    /**
     * 从校验异常中提取面向用户的提示信息
     * <p>
     * 三种异常均只取第一条错误：快速失败模式下每条请求只会产生一个约束违规，
     * 因此无需逐个汇总。
     */
    private String resolveValidationMessage(Exception e) {
        if (e instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(PARAM_VALIDATION_FAILED);
        }
        BindingResult bindingResult = e instanceof BindException bindException
                ? bindException.getBindingResult()
                : ((MethodArgumentNotValidException) e).getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        return fieldError != null && StringUtils.hasText(fieldError.getDefaultMessage())
                ? fieldError.getDefaultMessage()
                : PARAM_VALIDATION_FAILED;
    }

    /**
     * 缺少必需的请求参数：客户端错误，WARN 且不打印堆栈
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getMessage());
        return Result.error(REQUEST_PARAMS_ERROR.getCode(),
                "缺少必须的[" + e.getParameterType() + "]类型的参数[" + e.getParameterName() + "]");
    }

    /**
     * 参数类型不匹配：客户端错误，WARN 且不打印堆栈
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        Class<?> requiredType = e.getRequiredType();
        String typeName = !ObjectUtils.isEmpty(requiredType) ? requiredType.getName() : "未知类型";
        String msg = "参数：[" + e.getName() +
                "]的传入值：[" + e.getValue() +
                "]与预期的字段类型：[" + typeName + "]不匹配";
        return Result.error(REQUEST_PARAMS_ERROR.getCode(), msg);
    }

    /**
     * IllegalArgumentException 可能携带 Redis key 等内部实现细节，
     * 不直接将 e.getMessage() 返回给前端，改为日志记录原始信息，前端返回通用提示。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage(), e);
        return Result.fromErrorMessage("参数错误，请检查请求参数");
    }

    /**
     * 同 IllegalArgumentException，内部状态异常不对外暴露细节。
     */
    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleIllegalStateException(IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage(), e);
        return Result.fromErrorMessage("系统内部错误，请稍后再试");
    }

    /**
     * 客户端主动断开连接（用户取消、网络中断）：非服务端故障，用 WARN 观察
     */
    @ExceptionHandler(ClientAbortException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleIgnoreRequestException(Exception e) {
        log.warn("ClientAbortException:", e);
        return Result.fromErrorMessage(e.getMessage());
    }

    /**
     * 请求方法不支持：客户端错误，WARN 且不打印堆栈
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error(METHOD_NOT_SUPPORTED.getCode(), "不支持该请求方法: " + e.getMethod());
    }

    /**
     * 路由 / 静态资源不存在：返回 404 而非 500
     * <p>
     * Spring Framework 6.1 起，静态资源未命中会抛 {@link NoResourceFoundException}（此前是
     * {@code sendError(404)}）。若不显式处理，该运行时异常会落入 {@link #handleException} 被误判为 500。
     * 关闭静态资源映射（{@code spring.web.resources.add-mappings=false}）时对应异常为
     * {@link NoHandlerFoundException}，故两者一并处理。
     * <p>
     * 【日志】404 多由扫描流量产生，高频且无行动价值，故保持 DEBUG。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFoundException(Exception e) {
        log.debug("请求的资源或路由不存在: {}", e.getMessage());
        return Result.error(NOT_FOUND);
    }

    /**
     * 请求内容类型不支持：客户端错误，WARN 且不打印堆栈
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<?> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("请求内容类型不支持: {}", e.getMessage());
        return Result.error(MEDIA_TYPE_NOT_SUPPORTED);
    }

    /**
     * 响应内容类型协商失败（Accept 不被支持）：客户端错误，WARN 且不打印堆栈
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public Result<?> handleMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException e) {
        log.warn("响应内容类型协商失败: {}", e.getMessage());
        return Result.error(MEDIA_TYPE_NOT_ACCEPTABLE);
    }

    /**
     * 异步请求超时（DeferredResult / Callable 未在超时时间内完成）
     * <p>
     * 属服务端处理能力信号而非客户端错误，用 WARN 便于观察，但不在日志中暴露内部细节。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<?> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        log.warn("异步请求超时: {}", e.getMessage());
        return Result.error(SERVER_DEGRADE);
    }

    /**
     * 业务异常：业务规则不满足导致的请求失败，返回 HTTP 200
     * <p>
     * 属**非预期但可容忍**的情况，按规范用 WARN（WARN 不触发告警，仅用于观察）。
     * 若某类业务失败高频，应通过采样或聚合统计降噪，而不是降低日志级别。
     * <p>
     * 日志记录错误码：前端据此做逻辑分支，其价值高于消息文本本身。
     * {@code BizException} 覆写了 {@code fillInStackTrace} 不生成堆栈，
     * 故此处只记录消息，不打印异常对象。
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}",
                e.getStatus() == null ? null : e.getStatus().getCode(), e.getMessage());
        if (null != e.getResult()) {
            return e.getResult();
        } else if (null != e.getStatus()) {
            ExceptionStatus resultStatus = e.getStatus();
            if (StringUtils.hasText(e.getMessage())) {
                return Result.error(resultStatus.getCode(), e.getMessage());
            }
            return Result.error(resultStatus);
        } else {
            return Result.fromErrorMessage(e.getMessage());
        }
    }

    /**
     * 请求体无法解析：客户端错误，WARN 且不打印堆栈
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleHttpMessageNotReadableException(Exception e) {
        log.warn("请求体无法解析: {}", e.getMessage());
        return Result.error(REQUEST_PARAMS_ERROR.getCode(), "请求体格式错误，无法解析");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("Unhandled Exception:", e);
        return Result.fromErrorMessage("系统繁忙，请稍后再试");
    }
}
