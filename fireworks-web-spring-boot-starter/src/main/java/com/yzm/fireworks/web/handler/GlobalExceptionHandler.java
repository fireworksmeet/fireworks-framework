package com.yzm.fireworks.web.handler;

import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
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

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({BindException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public Result<?> handlerException(Exception e) {
        log.warn("{}:", e.getClass().getName(), e);
        String message;
        if (e instanceof BindException bindException) {
            var fieldError = bindException.getBindingResult().getFieldError();
            message = !ObjectUtils.isEmpty(fieldError) ? fieldError.getDefaultMessage() : "参数绑定错误";
        } else if (e instanceof ConstraintViolationException constraintViolationException) {
            var violations = constraintViolationException.getConstraintViolations();
            message = violations.isEmpty() ? "参数校验失败" : violations.iterator().next().getMessage();
        } else {
            MethodArgumentNotValidException methodArgumentNotValidException = (MethodArgumentNotValidException) e;
            var fieldError = methodArgumentNotValidException.getBindingResult().getFieldError();
            message = !ObjectUtils.isEmpty(fieldError) ? fieldError.getDefaultMessage() : "参数校验失败";
        }
        return Result.error(REQUEST_PARAMS_ERROR.getCode(), message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> missingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException:", e);
        return Result.error(REQUEST_PARAMS_ERROR.getCode(),
                "缺少必须的[" + e.getParameterType() + "]类型的参数[" + e.getParameterName() + "]");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException:", e);
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
    public Result<?> illegalArgumentException(IllegalArgumentException e) {
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        return Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持该请求方法: " + e.getMethod());
    }

    /**
     * 业务异常，返回 200
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleResultStatusException(BizException e) {
        log.warn("BizException:", e);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleHttpMessageNotReadableException(Exception e) {
        log.warn("HttpMessageNotReadableException:", e);
        return Result.error(REQUEST_PARAMS_ERROR.getCode(), "请求体格式错误，无法解析");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("Unhandled Exception:", e);
        return Result.fromErrorMessage("系统繁忙，请稍后再试");
    }
}