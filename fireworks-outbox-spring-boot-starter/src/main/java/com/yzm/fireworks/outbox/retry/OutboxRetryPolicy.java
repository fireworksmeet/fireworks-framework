package com.yzm.fireworks.outbox.retry;

import java.time.Instant;

/**
 * Outbox 重试策略：决定一次失败后「下次可投递时间」。
 * <p>
 * 只负责投递层重试（消息是否成功执行 Handler），与编排层节点重试是两回事，不要在这里表达业务语义。
 *
 * @author JYuan
 */
public interface OutboxRetryPolicy {

    /**
     * 计算下次可投递时间。
     *
     * @param currentRetryCount 本次失败前的已重试次数（首次失败传 0）
     * @return 下次可投递时间
     */
    Instant nextRetryAt(int currentRetryCount);
}
