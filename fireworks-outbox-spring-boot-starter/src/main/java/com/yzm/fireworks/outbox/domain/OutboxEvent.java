package com.yzm.fireworks.outbox.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 事务性发件箱事件。
 * <p>
 * <b>它是一个与 ORM 无关的 POJO</b>：不携带任何持久化框架注解，由
 * {@code JdbcOutboxEventRepository} 通过 JDBC 读写。
 * <p>
 * <b>它是「消息实例」而不是「业务数据」</b>：{@link #payload} 只保存最小上下文
 * （如 {@code orderId}），禁止整对象落库，更禁止写入密码 / Token / 密钥 / 证件原文。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 事件实例 ID（消息实例唯一标识，32 位紧凑 UUID）
     */
    private String eventId;

    /**
     * 事件类型，与 {@code OutboxHandler#eventType()} 对应
     */
    private String eventType;

    /**
     * 业务实体类型（如 ORDER / USER），用于按实体排查消息；框架不据此排序
     */
    private String entityType;

    /**
     * 业务实体 ID（如订单号），与 {@link #entityType} 一起定位消息来源
     */
    private String entityId;

    /**
     * 消息载荷（JSON 原文）
     */
    private String payload;

    /**
     * 状态编码，见 {@link OutboxEventStatus}
     */
    private Integer status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 下次可投递时间
     */
    private Instant nextRetryAt;

    /**
     * 领取时间（Worker 把该行领走并置为 PROCESSING 的时刻）；超时恢复据此判断
     */
    private Instant claimedAt;

    /**
     * 领取者标识（Worker ID）；状态回写时用作持有权校验
     */
    private String claimedBy;

    /**
     * 最近一次失败原因
     */
    private String lastError;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 投递成功时间
     */
    private Instant publishedAt;
}
