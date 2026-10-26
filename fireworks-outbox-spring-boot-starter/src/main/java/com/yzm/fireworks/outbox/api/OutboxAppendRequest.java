package com.yzm.fireworks.outbox.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbox 追加请求。
 * <p>
 * 只有 {@link #eventType} 与 {@link #payload} 是必须的；其余字段按需提供：
 * <ul>
 *   <li>{@link #entityType} / {@link #entityId}：消息对应的业务实体，用于按实体排查；</li>
 *   <li>{@link #maxRetryCount}：覆盖全局默认的最大重试次数；</li>
 *   <li>{@link #traceId}：默认自动从 MDC 读取，一般无需手动指定。</li>
 * </ul>
 *
 * <p>没有"业务幂等键"字段：重复的业务操作会在业务表的唯一约束处失败，同一个事务里的 outbox 写入随之回滚；
 * 而消息重投时 {@code eventId} 不变，消费端直接用它做幂等键即可。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxAppendRequest {

    /**
     * 事件类型（必填），须与某个 {@code OutboxHandler#eventType()} 对应
     */
    private String eventType;

    /**
     * 业务实体类型（如 ORDER / USER），可空
     */
    private String entityType;

    /**
     * 业务实体 ID（如订单号），可空
     */
    private String entityId;

    /**
     * 消息载荷（必填）：任意对象按 JSON 序列化，或直接传入 JSON 字符串
     */
    private Object payload;

    /**
     * 最大重试次数，为空时取全局配置
     */
    private Integer maxRetryCount;

    /**
     * 链路追踪 ID，为空时自动从 MDC 读取
     */
    private String traceId;
}
