package com.yzm.fireworks.oplog.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;

/**
 * @author muzhantong
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class LogRecord {
    /**
     * 主键
     */
    private Long id;
    /**
     * 租户
     */
    private String tenant;

    /**
     * 保存的操作日志的类型，比如：订单类型、商品类型
     */
    private String type;
    /**
     * 日志的子类型，比如订单的C端日志，和订单的B端日志，type都是订单类型，但是子类型不一样
     */
    private String subType;

    /**
     * 日志绑定的业务标识
     */
    private String bizNo;

    /**
     * 操作人（含 ID 与姓名快照）
     * <p>
     * 与 {@code @LogRecord#operator()} 表达式返回的 {@link Operator} 类型一致，
     * 故链路中无需拆装。类型本身的字段说明见 {@link Operator}。
     * <p>
     * 注意：本类为领域对象，若业务直接将其落库，需将 {@code operator}
     * 展开为 {@code operator_id} / {@code operator_name} 两列。
     */
    private Operator operator;

    /**
     * 日志内容
     */
    private String action;
    /**
     * 记录是否是操作失败的日志
     */
    private boolean fail;
    /**
     * 日志的创建时间
     */
    private Instant createdAt;
    /**
     * 日志的额外信息
     */
    private String extra;

    /**
     * 打印日志的代码信息
     * CodeVariableType 日志记录的ClassName、MethodName
     */
    private Map<CodeVariableType, Object> codeVariable;
}
