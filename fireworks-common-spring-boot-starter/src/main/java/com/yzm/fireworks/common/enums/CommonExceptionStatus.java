package com.yzm.fireworks.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统公共错误码
 * <p>
 * 【编码约定】框架 / 通用层统一使用 <b>4 位</b> 编码，按业务语义分段：
 * <ul>
 *   <li>{@code 0}    —— 成功</li>
 *   <li>{@code 1xxx} —— 认证与授权</li>
 *   <li>{@code 2xxx} —— 系统级</li>
 *   <li>{@code 3xxx} —— 请求与参数</li>
 *   <li>{@code 4xxx} —— 幂等与重复提交</li>
 * </ul>
 * <p>
 * 【分层约定】用<b>位数</b>区分错误码层级，避免与 HTTP 状态码混淆：
 * <ul>
 *   <li>4 位 —— 框架 / 通用错误码（本枚举）</li>
 *   <li>7 位 —— 各业务服务错误码，前 2 位标识服务
 *       （如 {@code 10xxxxx} 认证服务、{@code 20xxxxx} 业务服务）</li>
 * </ul>
 * <p>
 * 【重要】业务码与 HTTP 状态码是<b>两套体系，数值不共享</b>：业务码表达<b>精确原因</b>，
 * HTTP 状态码表达<b>这次调用是否被正确处理完成</b>。业务失败一律返回 {@code 200}，
 * 由响应体中的 {@code code} 分流（见 {@link ExceptionStatus}），因此这里刻意避开
 * 401 / 403 / 404 等 HTTP 状态码取值。
 *
 * @author JYuan
 */
@Getter
@AllArgsConstructor
public enum CommonExceptionStatus implements ExceptionStatus {

    /**
     * 成功
     */
    SUCCESS(0, "成功"),

    // ──────────────────────────── 1xxx 认证与授权 ────────────────────────────

    /**
     * 未认证 / 登录状态失效
     * <p>
     * 【约定】不建议由业务 {@code throw}：HTTP 401 由 Spring Security 的
     * {@code AuthenticationEntryPoint} 统一返回，这样前端的登录态拦截器才能生效。
     */
    UNAUTHORIZED(1001, "登录状态已失效，请重新登录"),

    /**
     * 已认证但无操作权限
     * <p>
     * 【约定】同 {@link #UNAUTHORIZED}：HTTP 403 由 {@code AccessDeniedHandler} 统一返回。
     */
    FORBIDDEN(1002, "没有操作权限"),

    /**
     * 资源不存在
     * <p>
     * 【注意】此码用于<b>业务语义</b>的"查不到"（返回 200）。路由 / 静态资源不存在属协议层，
     * 由容器返回 HTTP 404，两者不共用。
     */
    NOT_FOUND(1003, "请求的资源不存在"),

    // ──────────────────────────── 2xxx 系统级 ────────────────────────────────

    /**
     * 服务内部异常
     */
    SERVER_ERROR(2001, "系统异常，请稍后重试"),

    /**
     * 服务降级 / 繁忙
     */
    SERVER_DEGRADE(2002, "服务繁忙，请稍后重试"),

    // ──────────────────────────── 3xxx 请求与参数 ────────────────────────────

    /**
     * 请求参数错误
     */
    REQUEST_PARAMS_ERROR(3001, "请求参数有误"),

    /**
     * 请求方法不支持
     */
    METHOD_NOT_SUPPORTED(3002, "不支持的请求方法"),

    /**
     * 请求内容类型不支持（Content-Type 不匹配）
     */
    MEDIA_TYPE_NOT_SUPPORTED(3003, "请求内容类型不支持"),

    /**
     * 无法返回客户端期望的内容类型（Accept 协商失败）
     */
    MEDIA_TYPE_NOT_ACCEPTABLE(3004, "无法返回请求期望的内容类型"),

    // ──────────────────────────── 4xxx 幂等与重复提交 ────────────────────────

    /**
     * 重复提交
     */
    REPEAT_SUBMIT(4001, "请勿重复提交"),

    ;

    private final int code;

    private final String message;
}
