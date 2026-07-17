package com.yzm.fireworks.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author JYuan
 * 系统公共code
 */
@Getter
@AllArgsConstructor
public enum CommonExceptionStatus implements ExceptionStatus {

    /**
     * 成功
     */
    SUCCESS(0, "ok"),

    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权"),

    /**
     * 没有资源权限
     */
    FORBIDDEN(403, "资源权限不足"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 服务内部异常
     */
    SERVER_ERROR(10000, "Service internal error"),
    /**
     * 参数错误
     */
    REQUEST_PARAMS_ERROR(20000, "Bad Request"),
    /**
     * 系统繁忙
     */
    SERVER_DEGRADE(30000, "服务忙，请稍后重试"),
    /**
     * 重复提交
     */
    REPEAT_SUBMIT(40000, "不允许重复提交，请稍后再试"),


    ;

    private final int code;

    private final String message;

}
