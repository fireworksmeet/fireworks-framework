package com.yzm.fireworks.outbox.worker;

import com.yzm.fireworks.common.util.ExceptionUtil;
import com.yzm.fireworks.common.util.StringUtil;
import com.yzm.fireworks.outbox.api.OutboxHandler;
import com.yzm.fireworks.outbox.api.OutboxHandlerRegistry;
import com.yzm.fireworks.outbox.domain.OutboxEvent;
import com.yzm.fireworks.outbox.exception.OutboxNonRetryableException;
import com.yzm.fireworks.outbox.properties.OutboxProperties;
import com.yzm.fireworks.outbox.repository.OutboxEventRepository;
import com.yzm.fireworks.outbox.retry.OutboxRetryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 单条事件投递器：在事务外执行 Handler，并把结果回写为 SUCCEEDED / PENDING（待重试）/ DEAD。
 *
 * <p>执行前会用事件上的 {@code traceId} 恢复 MDC，让「HTTP 请求 → 业务事务 → Outbox → Handler → 远程服务」
 * 串成一条链路；执行后恢复原值，避免污染复用的 Worker 线程。
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxDispatcher {

    /**
     * 失败原因落库的截断阈值：超过后截断并追加 {@code ...[truncated]}。
     * <p>列类型是 TEXT（无硬上限），这里限制的是“别把整段堆栈塞进一行”。
     */
    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxHandlerRegistry handlerRegistry;
    private final OutboxRetryPolicy retryPolicy;
    private final OutboxProperties properties;

    /**
     * 投递一条已领取（status=PROCESSING）的事件。
     */
    public void dispatch(OutboxEvent event) {
        String workerId = event.getClaimedBy();
        String traceIdKey = properties.getTraceIdKey();
        String previousTraceId = MDC.get(traceIdKey);
        if (StringUtils.hasText(event.getTraceId())) {
            MDC.put(traceIdKey, event.getTraceId());
        }
        try {
            OutboxHandler handler = handlerRegistry.get(event.getEventType());
            if (handler == null) {
                markDead(event, workerId, "未注册 OutboxHandler: eventType=" + event.getEventType());
                return;
            }
            handler.handle(event);
            markSucceeded(event, workerId);
        } catch (Throwable e) {
            handleFailure(event, workerId, e);
        } finally {
            if (previousTraceId == null) {
                MDC.remove(traceIdKey);
            } else {
                MDC.put(traceIdKey, previousTraceId);
            }
        }
    }

    private void markSucceeded(OutboxEvent event, String workerId) {
        if (outboxEventRepository.markSucceeded(event.getId(), workerId)) {
            log.debug("[Outbox] 投递成功 eventId={}, eventType={}", event.getEventId(), event.getEventType());
        } else {
            log.warn("[Outbox] 投递成功但状态回写失败（可能已失去持有权）eventId={}, eventType={}",
                    event.getEventId(), event.getEventType());
        }
    }

    private void handleFailure(OutboxEvent event, String workerId, Throwable e) {
        int currentRetryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        int maxRetryCount = event.getMaxRetryCount() == null
                ? properties.getRetry().getMaxRetryCount()
                : event.getMaxRetryCount();
        String error = describe(e);

        boolean nonRetryable = isNonRetryable(e);
        if (nonRetryable || currentRetryCount + 1 >= maxRetryCount) {
            String reason = nonRetryable
                    ? "不可重试异常"
                    : "重试次数耗尽(" + (currentRetryCount + 1) + "/" + maxRetryCount + ")";
            log.error("[Outbox] 投递失败，置为 DEAD（{}）eventId={}, eventType={}",
                    reason, event.getEventId(), event.getEventType(), e);
            markDead(event, workerId, error);
            return;
        }

        Instant nextRetryAt = retryPolicy.nextRetryAt(currentRetryCount);
        if (outboxEventRepository.markRetry(event.getId(), workerId, nextRetryAt, error)) {
            log.warn("[Outbox] 投递失败，将于 {} 重试（第 {} 次）eventId={}, eventType={}, reason={}",
                    nextRetryAt, currentRetryCount + 1, event.getEventId(), event.getEventType(), error);
        } else {
            log.warn("[Outbox] 投递失败但状态回写失败（可能已失去持有权）eventId={}", event.getEventId());
        }
    }

    private void markDead(OutboxEvent event, String workerId, String error) {
        if (!outboxEventRepository.markDead(event.getId(), workerId, error)) {
            log.warn("[Outbox] 置 DEAD 失败（可能已失去持有权）eventId={}", event.getEventId());
        }
    }

    /**
     * 沿异常链判断是否存在「不可重试」标记。
     */
    private boolean isNonRetryable(Throwable e) {
        return ExceptionUtil.hasCause(e, OutboxNonRetryableException.class);
    }

    /**
     * 组装落库的失败描述（异常类型 + 消息 + 根因），超长时截断并标记。
     */
    private String describe(Throwable e) {
        Throwable root = ExceptionUtil.getRootCause(e);
        String description = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (root != e) {
            description += " | 根因 " + root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        return StringUtil.truncate(description, MAX_ERROR_LENGTH);
    }
}
