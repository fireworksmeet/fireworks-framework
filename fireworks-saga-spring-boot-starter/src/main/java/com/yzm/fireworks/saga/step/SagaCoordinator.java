package com.yzm.fireworks.saga.step;

import com.yzm.fireworks.saga.SagaLog;
import com.yzm.fireworks.saga.SagaStatus;
import com.yzm.fireworks.saga.service.SagaLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saga 协调器
 *
 * @author JYuan
 */
@Slf4j
public class SagaCoordinator {

    private final SagaLogService sagaLogService;

    public SagaCoordinator(SagaLogService sagaLogService) {
        this.sagaLogService = sagaLogService;
    }

    /**
     * 执行 Saga
     */
    public <T> void execute(SagaContext<T> context) {
        Assert.notNull(context, "SagaContext can not be null");
        try {
            Map<Integer, List<SagaStep<T>>> sortSteps = context.getSortSteps();
            Executor executor = context.getExecutor();
            // 顺序执行各个 SagaStep
            sortSteps.keySet().stream().sorted().forEach(order -> {
                List<SagaStep<T>> values = sortSteps.get(order);
                if (values.size() > 1) {
                    CompletableFuture<Void> all = CompletableFuture.allOf(
                            values.stream()
                                    .map(step -> CompletableFuture
                                            .runAsync(() -> executeStep(context, step), executor)
                                            .exceptionally(ex -> {
                                                log.warn("Step {} failed ", step.getName(), ex);
                                                throw new CompletionException(ex);
                                            }))
                                    .toArray(CompletableFuture[]::new));
                    all.join();
                } else {
                    executeStep(context, values.getFirst());
                }
            });
            sagaLogService.updateSucceeded(context.getSagaId());
        } catch (Exception e) {
            // 补偿已经成功执行的 SagaStep
            compensate(context);
            throw e;
        }
    }

    /**
     * 执行补偿
     */
    protected <T> void compensate(SagaContext<T> context) {
        // 逆向执行补偿操作（创建副本避免修改原始列表）
        List<String> executed = new ArrayList<>(context.getExecutedSteps());
        Collections.reverse(executed);
        Map<String, SagaStep<T>> nameSteps = context.getNameSteps();
        String sagaId = context.getSagaId();
        List<SagaLog> sagaLogs = sagaLogService.getBySagaId(sagaId);
        if (ObjectUtils.isEmpty(sagaLogs)) {
            return;
        }
        Map<String, SagaLog> logMap = sagaLogs.stream()
                .collect(Collectors.toMap(SagaLog::getStepName, Function.identity()));
        for (String stepName : executed) {
            SagaStep<T> step = nameSteps.get(stepName);
            SagaLog sagaLog = logMap.get(stepName);
            if (!ObjectUtils.isEmpty(step) && !ObjectUtils.isEmpty(sagaLog)) {
                compensate(step, sagaLog);
            }
        }
    }

    private <T> void executeStep(SagaContext<T> context, SagaStep<T> step) {
        String sagaId = context.getSagaId();
        T data = context.getData();
        String stepName = step.getName();

        // 1. 尝试写入执行日志（利用 uk_sagaid_stepname 实现原子拦截）
        try {
            sagaLogService.saveExecuting(sagaId, stepName, data);
        } catch (DuplicateKeyException e) {
            log.warn("Saga step already executing or completed: sagaId={}, step={}", sagaId, stepName);
            return;
        } catch (Exception e) {
            log.error("Failed to persist saga log: sagaId={}, step={}", sagaId, stepName, e);
            throw e; 
        }

        // 2. 核心执行逻辑直接进行，并依靠执行后的 CAS 原子更新提供并发安全保障

        // 3. 执行核心业务逻辑
        try {
            step.execute(data);
            
            // 4. 执行后利用 CAS 原子更新校验（确权步骤执行成功并且未被定时任务抢占）
            boolean success = sagaLogService.updateStepSucceededAtomic(sagaId, stepName);
            if (success) {
                context.getExecutedSteps().add(stepName);
            } else {
                log.error("CRITICAL: Step executed but was compensated due to timeout! sagaId={}, step={}", sagaId, stepName);
                throw new IllegalStateException("Saga step was compensated during execution");
            }
        } catch (Exception e) {
            log.error("Error during saga step execution: sagaId={}, step={}", sagaId, stepName, e);
            sagaLogService.markStepFailed(sagaId, stepName, e.getMessage());
            throw e; 
        }
    }


    private <T> void compensate(SagaStep<T> step, SagaLog sagaLog) {
        // 原子抢占：尝试将状态从 EXECUTING 或 FAILED 变更为 COMPENSATING
        // 如果 CAS 失败，说明异步恢复任务已经抢先处理，当前线程应放弃重复补偿
        boolean locked = sagaLogService.updateStatusAtomic(sagaLog.getId(), 
                List.of(SagaStatus.EXECUTING, SagaStatus.FAILED),
                SagaStatus.COMPENSATING);

        if (!locked) {
            log.info("Saga step compensation already in progress or completed by another task: sagaId={}, step={}", 
                    sagaLog.getSagaId(), sagaLog.getStepName());
            return;
        }

        String paramStr = sagaLog.getParam();
        try {
            step.compensate(sagaLogService.deserialize(paramStr, step));
            
            // 补偿成功，原子更新为 COMPENSATED
            sagaLogService.updateStatusAtomic(sagaLog.getId(), SagaStatus.COMPENSATING, SagaStatus.COMPENSATED);
        } catch (Exception e) {
            log.warn("Step {} compensate failed ", step.getName(), e);
            
            // sagaLog 对象是在原子修改状态【前】从上下文读取到的旧对象，因此它此时的 RetryCount 等效于修改前的记录
            int failedRetryAttempt = (sagaLog.getRetryCount() != null ? sagaLog.getRetryCount() : 0) + 1;
            sagaLogService.markCompensationFailed(sagaLog.getId(), e.getMessage(), failedRetryAttempt);
        }
    }
}