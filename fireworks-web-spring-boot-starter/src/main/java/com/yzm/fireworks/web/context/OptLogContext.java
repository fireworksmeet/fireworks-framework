package com.yzm.fireworks.web.context;

import com.yzm.fireworks.api.annotation.OptLog;
import com.yzm.fireworks.api.enums.OptLogType;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志上下文
 *
 * <p>由 {@link com.yzm.fireworks.web.aspectj.OptLogAspect} 在方法执行后同步构建，
 * 包含业务层所需的全部信息，业务实现可直接取用，无需再从 Request/SecurityContext 中提取。
 *
 * <p><b>设计说明</b>：
 * <ul>
 *   <li>IP、UA 等请求信息在切面中同步采集（Request 在异步线程中不可用）</li>
 *   <li>操作人信息（userId、username）在切面中从 SecurityContext 采集</li>
 *   <li>业务层实现 {@link com.yzm.fireworks.web.service.OptLogService} 只负责持久化</li>
 * </ul>
 *
 * @author JYuan
 */
@Data
@Builder
public class OptLogContext {

    // ── 注解元数据 ────────────────────────────────────────────────────────────

    /** 所属模块，来自 @OptLog#module */
    private String module;

    /** 操作类型，来自 @OptLog#type */
    private OptLogType type;

    /** 操作描述，来自 @OptLog#description */
    private String description;

    // ── 方法信息 ──────────────────────────────────────────────────────────────

    /** 目标类全限定名 */
    private String className;

    /** 方法名 */
    private String methodName;

    /** 请求参数（JSON），@OptLog#recordArgs=false 时为 null */
    private String argsJson;

    /** 返回值（JSON），@OptLog#recordResult=true 时才有值 */
    private String resultJson;

    // ── 执行结果 ──────────────────────────────────────────────────────────────

    /** true=成功，false=抛出异常 */
    private boolean success;

    /** 异常信息（success=false 时） */
    private String errorMsg;

    /** 异常类型全限定名 */
    private String errorType;

    /** 方法执行耗时(带单位) */
    private String costTime;

    /** 操作发生时间 */
    private LocalDateTime operateTime;

    // ── 操作人信息 ────────────────────────────────────────────────────────────

    /** 操作人用户 ID */
    private Long operatorId;

    /** 操作人用户名（工号） */
    private String operatorName;

    // ── 请求信息（切面同步采集，异步持久化时不可再从 Request 取） ─────────────

    /** 客户端 IP */
    private String ip;

    /** 完整 User-Agent 字符串 */
    private String userAgent;

    /** 浏览器 + 版本（如 Chrome 130） */
    private String browser;

    /** 操作系统（如 Windows 11） */
    private String os;

    /** 请求 URI（不含域名，如 /system/user/v1/add） */
    private String requestUri;

    /** HTTP 方法（POST） */
    private String requestMethod;

    // ── 原始对象（供高级实现使用，如差异比对） ───────────────────────────────

    /** 原始方法反射对象 */
    private transient Method method;

    /** 原始参数数组 */
    private transient Object[] methodArgs;

    /** 原始返回值 */
    private transient Object result;

    /** 原始异常 */
    private transient Throwable throwable;

    /** 原始注解 */
    private transient OptLog annotation;
}
