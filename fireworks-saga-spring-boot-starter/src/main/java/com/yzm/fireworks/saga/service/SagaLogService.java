package com.yzm.fireworks.saga.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.yzm.fireworks.common.util.ObjectMapperUtil;
import com.yzm.fireworks.common.util.TimeUtil;
import com.yzm.fireworks.saga.SagaLog;
import com.yzm.fireworks.saga.SagaProperties;
import com.yzm.fireworks.saga.SagaStatus;
import com.yzm.fireworks.saga.mapper.SagaLogMapper;
import com.yzm.fireworks.saga.step.SagaStep;
import com.yzm.fireworks.saga.step.SagaStepRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * @author JYuan
 */
@Slf4j
@SuppressWarnings("unchecked")
public class SagaLogService {
    private final SagaLogMapper sagaLogMapper;
    private final SagaProperties properties;
    private final SagaStepRegistry registry;

    public SagaLogService(SagaLogMapper sagaLogMapper, SagaProperties properties, SagaStepRegistry registry) {
        this.sagaLogMapper = sagaLogMapper;
        this.properties = properties;
        this.registry = registry;
    }

    public <T> boolean saveExecuting(String sagaId, String stepName, T param) {
        SagaLog sagaLog = SagaLog.builder()
                .sagaId(sagaId)
                .stepName(stepName)
                .status(SagaStatus.EXECUTING)
                .retryCount(0) // 初始重试次数为 0 (主流设计)
                .maxRetries(properties.getMaxRetries())
                .nextRetryTime(LocalDateTime.now().plus(properties.getExecutingTimeout(), ChronoUnit.MILLIS))
                .param(serialize(param))
                .build();
        return SqlHelper.retBool(sagaLogMapper.insert(sagaLog));
    }

    public List<SagaLog> getBySagaId(String sagaId) {
        return sagaLogMapper.selectList(Wrappers.<SagaLog>lambdaQuery().eq(SagaLog::getSagaId, sagaId));
    }

    public LocalDateTime calculateNextRetryTime(int retryCount) {
        return TimeUtil.calculateNextRetryTime(retryCount, properties.getMaxRetries(), properties.getInitialDelayMs(), properties.getMaxDelayMs(), properties.getJitterFactor(), LocalDateTime.now());
    }

    public boolean updateSucceeded(String sagaId) {
        return SqlHelper.retBool(sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getSagaId, sagaId)
                .set(SagaLog::getStatus, SagaStatus.SUCCEEDED)));
    }

    public boolean updateStepSucceededAtomic(String sagaId, String stepName) {
        return SqlHelper.retBool(sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getSagaId, sagaId)
                .eq(SagaLog::getStepName, stepName)
                .eq(SagaLog::getStatus, SagaStatus.EXECUTING)
                .set(SagaLog::getStatus, SagaStatus.SUCCEEDED)
                .set(SagaLog::getUpdatedAt, LocalDateTime.now())));
    }

    public boolean updateById(SagaLog sagaLog) {
        return SqlHelper.retBool(sagaLogMapper.updateById(sagaLog));
    }

    /**
     * 原子更新单条步骤状态（针对补偿阶段抢占）
     */
    public boolean updateStatusAtomic(Long id, SagaStatus fromStatus, SagaStatus toStatus) {
        return SqlHelper.retBool(sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getId, id)
                .eq(SagaLog::getStatus, fromStatus)
                .set(SagaLog::getStatus, toStatus)
                .setSql(toStatus == SagaStatus.COMPENSATING, "retry_count = retry_count + 1")
                .set(toStatus == SagaStatus.COMPENSATING, SagaLog::getNextRetryTime, LocalDateTime.now().plus(properties.getExecutingTimeout(), ChronoUnit.MILLIS))
                .set(SagaLog::getUpdatedAt, LocalDateTime.now())));
    }

    /**
     * 原子更新单条步骤状态（允许从多个状态转移）
     */
    public boolean updateStatusAtomic(Long id, List<SagaStatus> fromStatuses, SagaStatus toStatus) {
        return SqlHelper.retBool(sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getId, id)
                .in(SagaLog::getStatus, fromStatuses)
                .set(SagaLog::getStatus, toStatus)
                .setSql(toStatus == SagaStatus.COMPENSATING, "retry_count = retry_count + 1")
                .set(toStatus == SagaStatus.COMPENSATING, SagaLog::getNextRetryTime, LocalDateTime.now().plus(properties.getExecutingTimeout(), ChronoUnit.MILLIS))
                .set(SagaLog::getUpdatedAt, LocalDateTime.now())));
    }

    /**
     * 执行失败时即时更新状态为 FAILED
     */
    public void markStepFailed(String sagaId, String stepName, String errorMsg) {
        sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getSagaId, sagaId)
                .eq(SagaLog::getStepName, stepName)
                .set(SagaLog::getStatus, SagaStatus.FAILED)
                .set(SagaLog::getErrorMsg, errorMsg)
                .set(SagaLog::getNextRetryTime, LocalDateTime.now().plus(properties.getExecutingTimeout(), ChronoUnit.MILLIS))
                .set(SagaLog::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 补偿执行失败时记录并推迟下一次的扫描延时
     */
    public void markCompensationFailed(Long logId, String errorMsg, int retryAttemptForDelay) {
        sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getId, logId)
                .set(SagaLog::getStatus, SagaStatus.FAILED)
                .set(SagaLog::getErrorMsg, errorMsg)
                .set(SagaLog::getNextRetryTime, calculateNextRetryTime(retryAttemptForDelay))
                .set(SagaLog::getUpdatedAt, LocalDateTime.now()));
    }

    public void recoveryCompensate() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 查找存在异常步骤（FAILED / EXECUTING / COMPENSATING 且已超时）的 sagaId 列表
        List<SagaLog> staledLogs = sagaLogMapper.selectList(Wrappers.<SagaLog>lambdaQuery()
                .select(SagaLog::getSagaId)
                .in(SagaLog::getStatus, SagaStatus.EXECUTING, SagaStatus.FAILED, SagaStatus.COMPENSATING)
                .apply("retry_count <= max_retries")
                .le(SagaLog::getNextRetryTime, now)
                .last("limit " + properties.getBatchSize()));

        if (ObjectUtils.isEmpty(staledLogs)) {
            return;
        }

        staledLogs.stream()
                .map(SagaLog::getSagaId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::recoverSingleSaga);
    }

    private void recoverSingleSaga(String sagaId) {
        // 原子抢占状态：锁定整个 Saga 的相关日志（将 FAILED/EXECUTING/COMPENSATING 的失效状态变为最新的 COMPENSATING 并重置超时）
        boolean locked = SqlHelper.retBool(sagaLogMapper.update(Wrappers.<SagaLog>lambdaUpdate()
                .eq(SagaLog::getSagaId, sagaId)
                .in(SagaLog::getStatus, SagaStatus.EXECUTING, SagaStatus.FAILED, SagaStatus.COMPENSATING)
                .le(SagaLog::getNextRetryTime, LocalDateTime.now()) // 二次 CAS 验证依然超时
                .set(SagaLog::getStatus, SagaStatus.COMPENSATING)
                .setSql("retry_count = retry_count + 1") // 发生重试时增加次数
                .set(SagaLog::getNextRetryTime, LocalDateTime.now().plus(properties.getExecutingTimeout(), ChronoUnit.MILLIS))
                .set(SagaLog::getUpdatedAt, LocalDateTime.now())));

        if (!locked) {
            return;
        }

        // 获取该事务所有步骤，按顺序逆序执行
        List<SagaLog> sagaLogs = sagaLogMapper.selectList(Wrappers.<SagaLog>lambdaQuery()
                .eq(SagaLog::getSagaId, sagaId)
                .orderByDesc(SagaLog::getId));

        for (SagaLog sagaLog : sagaLogs) {
            SagaStep<?> step = registry.getStep(sagaLog.getStepName());
            if (step == null) {
                log.warn("Step not found in registry: {}, sagaId={}", sagaLog.getStepName(), sagaId);
                continue;
            }
            try {
                processCompensateStep(step, sagaLog);
            } catch (Exception e) {
                log.error("Compensate failed for step: {}, sagaId={}", sagaLog.getStepName(), sagaId, e);
                // 此时 sagaLog.getRetryCount() 已经是锁查询之后带上 +1 增量的值了
                int retryAttempt = sagaLog.getRetryCount() != null ? sagaLog.getRetryCount() : 1;
                markCompensationFailed(sagaLog.getId(), e.getMessage(), retryAttempt);
                break; // 逆序中断，保持后续步骤的一致性
            }
        }
    }

    private <T> void processCompensateStep(SagaStep<T> step, SagaLog sagaLog) {
        T param = deserialize(sagaLog.getParam(), step);
        step.compensate(param);
        
        updateStatusAtomic(sagaLog.getId(), SagaStatus.COMPENSATING, SagaStatus.COMPENSATED);
    }

    public <T> String serialize(T param) {
        if (ObjectUtils.isEmpty(param)) {
            return null;
        }
        return ObjectMapperUtil.stringify(param);
    }

    public <T> T deserialize(String param, SagaStep<T> step) {
        if (StringUtils.hasText(param)) {
            Class<T> genericType = (Class<T>) ResolvableType.forClass(step.getClass()).as(SagaStep.class).getGeneric(0).resolve();
            return ObjectMapperUtil.deserialize(param, genericType);
        }
        return null;
    }
}
