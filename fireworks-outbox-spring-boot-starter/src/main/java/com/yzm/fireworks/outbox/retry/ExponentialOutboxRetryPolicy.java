package com.yzm.fireworks.outbox.retry;

import com.yzm.fireworks.common.util.time.BackoffUtil;
import com.yzm.fireworks.outbox.properties.OutboxProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * 默认重试策略：指数退避 + 单向抖动。
 *
 * <pre>
 * delay = min(maxDelay, min(maxDelay, baseDelay × 2^retryCount) + random(0, jitter))
 * </pre>
 *
 * <p>抖动只往后推迟、不提前重试：outbox 的重试时间要落库并由 Worker 轮询，
 * 提前重试只会多消耗一次投递，没有收益。计算逻辑见 {@link BackoffUtil#additiveJitter}。
 *
 * @author JYuan
 */
public class ExponentialOutboxRetryPolicy implements OutboxRetryPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;
    private final Duration jitter;

    public ExponentialOutboxRetryPolicy(OutboxProperties properties) {
        OutboxProperties.Retry retry = properties.getRetry();
        this.baseDelay = retry.getBaseDelay();
        this.maxDelay = retry.getMaxDelay();
        this.jitter = retry.getJitter();
    }

    @Override
    public Instant nextRetryAt(int currentRetryCount) {
        return BackoffUtil.additiveJitter(currentRetryCount, baseDelay, maxDelay, jitter, Instant.now());
    }
}
