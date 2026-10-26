package com.yzm.fireworks.outbox.worker;

import com.yzm.fireworks.common.util.ThreadPoolUtil;
import com.yzm.fireworks.outbox.properties.OutboxProperties;
import com.yzm.fireworks.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;

/**
 * PROCESSING 超时恢复。
 *
 * <p>Worker 领取事件后进程宕机，事件会永远停在 PROCESSING。本任务定期把「领取时间早于
 * {@code now - recovery.timeout}」的事件重置回 PENDING，交给投递 Worker 重放。
 *
 * <p>这正是 Outbox 必须 At-Least-Once 的原因：<b>重复执行是正常情况，不是异常。</b>
 * 请确保 {@code recovery.timeout} 大于 Handler 的最长执行时间，否则会把执行中的事件误判为超时。
 *
 * @author JYuan
 */
@Slf4j
public class OutboxRecoveryWorker implements SmartLifecycle {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final ThreadPoolTaskScheduler scheduler;
    private volatile boolean running;

    public OutboxRecoveryWorker(OutboxEventRepository outboxEventRepository, OutboxProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.scheduler = ThreadPoolUtil.scheduler("outbox-recovery-", Duration.ofSeconds(5));
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        OutboxProperties.Recovery recovery = properties.getRecovery();
        ThreadPoolUtil.scheduleWithFixedDelay(scheduler, this::recoverSafely,
                recovery.getInitialDelay(), recovery.getInterval());
        log.info("[Outbox] PROCESSING 恢复任务已启动 interval={}ms, timeout={}ms",
                recovery.getInterval().toMillis(), recovery.getTimeout().toMillis());
    }

    private void recoverSafely() {
        try {
            Instant before = Instant.now().minus(properties.getRecovery().getTimeout());
            int recovered = outboxEventRepository.recoverStale(before);
            if (recovered > 0) {
                log.warn("[Outbox] 恢复超时 PROCESSING 事件 {} 条（领取时间早于 {}）：未超限的重投，超限的置 DEAD",
                        recovered, before);
            }
        } catch (Throwable e) {
            log.error("[Outbox] PROCESSING 恢复任务异常，已忽略本次", e);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        // 内部按 awaitTerminationSeconds（5s）等待在途任务，超时后中断
        scheduler.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
