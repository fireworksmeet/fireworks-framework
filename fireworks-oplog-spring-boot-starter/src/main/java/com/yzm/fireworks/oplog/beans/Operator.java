package com.yzm.fireworks.oplog.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作人
 * <p>
 * 由业务方实现 {@code IOperatorGetService} 返回，通常对接内部鉴权体系。
 *
 * @author muzhantong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Operator {

    /**
     * 操作人用户 ID（主键）
     * <p>
     * 用于精确关联当前用户表，类型与项目 {@code BaseOperatorEntity#createdBy} 保持一致
     */
    private Long operatorId;

    /**
     * 操作人名称快照
     * <p>
     * 记录操作发生时的姓名，属于快照信息：
     * <ul>
     *     <li>用户改名后，历史日志仍展示当时的姓名，不会被篡改</li>
     *     <li>用户被删除后，日志仍可自证操作人，不影响审计</li>
     *     <li>按姓名检索日志无需回查用户表，避免 {@code IN} 子查询导致索引失效</li>
     * </ul>
     */
    private String operatorName;
}
