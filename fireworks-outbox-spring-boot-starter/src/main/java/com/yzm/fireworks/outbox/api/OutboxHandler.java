package com.yzm.fireworks.outbox.api;

import com.yzm.fireworks.outbox.domain.OutboxEvent;

/**
 * Outbox 事件处理器。
 * <p>
 * Outbox 只负责「可靠执行 Handler」，不写死任何业务逻辑：一个 {@code eventType} 对应一个实现，
 * 由 Spring 容器收集后注册进 {@link OutboxHandlerRegistry}。
 *
 * <p><b>幂等是硬约束</b>：Outbox 语义是 At-Least-Once，Handler 可能被重复执行（重试、PROCESSING 超时恢复、
 * 进程宕机后重放）。请用 {@link OutboxEvent#getEventId()} 作为跨服务幂等键——同一条消息重投时它不变，
 * 让对端以唯一约束兜底，而不是假设「只会执行一次」。
 *
 * <p><b>不要在 Handler 里持有数据库事务执行远程调用</b>：Dispatcher 在事务外调用本方法。
 *
 * @author JYuan
 */
public interface OutboxHandler {

    /**
     * 处理的事件类型，须全局唯一。
     *
     * @return 事件类型，如 {@code FILE_REFERENCE_ADD}
     */
    String eventType();

    /**
     * 执行事件。
     * <p>
     * 抛出普通异常 → 按重试策略退避重试，超限后置 {@code DEAD}；
     * 抛出 {@code OutboxNonRetryableException} → 直接置 {@code DEAD}。
     *
     * @param event 事件（含 {@code eventId}、{@code payload} 原文、{@code entityType} / {@code entityId}、{@code traceId}）
     * @throws Exception 执行失败
     */
    void handle(OutboxEvent event) throws Exception;
}
