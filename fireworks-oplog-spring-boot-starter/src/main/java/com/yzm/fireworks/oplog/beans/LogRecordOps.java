package com.yzm.fireworks.oplog.beans;

import lombok.Builder;
import lombok.Data;

/**
 * 操作日志注解解析后的元数据
 * <p>
 * 由 {@code @LogRecord} 注解解析而来，各字段保存注解上的原始模板字符串。
 * 注解中的模板可以是静态文案，也可以是 SpEL 表达式，
 * 在目标方法执行时再结合上下文求值为最终内容。
 *
 * @author muzhantong
 */
@Data
@Builder
public class LogRecordOps {

    /**
     * 成功时的日志文案模板
     * <p>
     * 对应 {@code @LogRecord#success()}，方法正常返回时使用。
     * 静态文案与动态值可混排，动态部分用 {@code #{SpEL 表达式}} 占位，如：
     * {@code "修改用户：#{#user.name}"}
     */
    private String successLogTemplate;

    /**
     * 失败时的日志文案模板
     * <p>
     * 对应 {@code @LogRecord#fail()}，方法抛出异常时使用。
     * 为空时表示失败不记录日志，支持 {@code #{}} 占位符
     */
    private String failLogTemplate;

    /**
     * 操作人表达式模板
     * <p>
     * 对应 {@code @LogRecord#operator()}。表达式<b>必须求值为
     * {@code Operator} 对象</b>，如 {@code "#{T(com.xxx.UserContext).getUser()}"}。
     * <p>
     * 为空时回退到 {@code IOperatorGetService} 获取当前登录人；
     * 非空且求值成功时<b>不再调用</b> {@code IOperatorGetService}。
     */
    private String operator;

    /**
     * 操作类型
     * <p>
     * 对应 {@code @LogRecord#type()}，如：订单类型、商品类型。
     * 为必填项，支持 {@code #{}} 占位符
     */
    private String type;

    /**
     * 业务标识
     * <p>
     * 对应 {@code @LogRecord#bizNo()}，用于串联同一业务对象的操作链路，
     * 如订单号、用户 ID。支持 {@code #{}} 占位符
     */
    private String bizNo;

    /**
     * 操作子类型
     * <p>
     * 对应 {@code @LogRecord#subType()}。
     * 用于在同一 type 下进一步区分场景，
     * 如订单的 C 端日志与 B 端日志 type 相同但 subType 不同
     */
    private String subType;

    /**
     * 额外扩展信息
     * <p>
     * 对应 {@code @LogRecord#extra()}，用于存放业务自定义内容，
     * 如请求 IP、UA、URI 等。支持 {@code #{}} 占位符
     */
    private String extra;

    /**
     * 是否记录日志的条件
     * <p>
     * 对应 {@code @LogRecord#condition()}，求值结果为 false 时跳过记录。
     * 如：{@code "#{#_result != null}"}，为空时默认记录。
     * 表达式按布尔语义求值，非布尔结果会尝试转换为布尔
     */
    private String condition;

    /**
     * 成功判定条件
     * <p>
     * 对应 {@code @LogRecord#successCondition()}。为空时以方法是否抛异常
     * 判定成功；非空时以该表达式的结果判定，据此选择成功或失败文案模板
     */
    private String isSuccess;
}
