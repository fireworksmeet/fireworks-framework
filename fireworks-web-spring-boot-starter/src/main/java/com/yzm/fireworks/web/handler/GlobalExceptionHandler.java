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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static com.yzm.fireworks.common.enums.CommonExceptionStatus.REQUEST_PARAMS_ERROR;


/**
 * 全局异常处理器
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
     * 该类异常由客户端传参不合法引起，属于<b>预期内的交互错误</b>而非系统故障，
     * 因此日志使用 debug 级别且不打印堆栈：
     * <ul>
     *     <li>避免大量客户端参数错误触发告警，淹没真正的系统异常</li>
     *     <li>校验框架的内部调用链堆栈对排查无参考价值</li>
     * </ul>
     * 前端通常已有必填校验，此处仅为服务端兜底，故只返回单条提示信息。
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({BindException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public Result<?> handleValidationException(Exception e) {
        log.debug("参数校验失败: {}", e.getMessage());
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
     * 缺少必需的请求参数：客户端错误，日志降为 debug 且不打印堆栈
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.debug("缺少请求参数: {}", e.getMessage());
        return Result.error(REQUEST_PARAMS_ERROR.getCode(),
                "缺少必须的[" + e.getParameterType() + "]类型的参数[" + e.getParameterName() + "]");
    }

    /**
     * 参数类型不匹配：客户端错误，日志降为 debug 且不打印堆栈
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.debug("参数类型不匹配: {}", e.getMessage());
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

    @ExceptionHandler(ClientAbortException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleIgnoreRequestException(Exception e) {
        log.error("ClientAbortException:", e);
        return Result.fromErrorMessage(e.getMessage());
    }

    /**
     * 请求方法不支持：客户端错误，日志降为 debug 且不打印堆栈
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.debug("请求方法不支持: {}", e.getMessage());
        return Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持该请求方法: " + e.getMethod());
    }

    /**
     * 业务异常：业务规则不满足导致的正常流转分支，返回 HTTP 200
     * <p>
     * 该类异常高频且属于预期内行为，故日志使用 info 级别而非 warn，
     * 避免大量业务失败（如"库存不足""重复提交"）触发告警、淹没真正的系统异常。
     * <p>
     * 日志记录错误码：前端据此做逻辑分支，其价值高于消息文本本身。
     * {@code BizException} 覆写了 {@code fillInStackTrace} 不生成堆栈，
     * 故此处只记录消息，不打印异常对象。
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.info("业务异常: code={}, message={}",
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
     * 请求体无法解析：客户端错误，日志降为 debug 且不打印堆栈
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleHttpMessageNotReadableException(Exception e) {
        log.debug("请求体无法解析: {}", e.getMessage());
        return Result.error(REQUEST_PARAMS_ERROR.getCode(), "请求体格式错误，无法解析");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("Unhandled Exception:", e);
        return Result.fromErrorMessage("系统繁忙，请稍后再试");
    }
}