package com.yzm.fireworks.saga;

/**
 * @author JYuan
 *
 * Saga状态
 */
public enum SagaStatus {
    /**
     * 开启
     */
    STARTED,
    /**
     * 进行中
     */
    EXECUTING,
    /**
     * 成功
     */
    SUCCEEDED,
    /**
     * 失败
     */
    FAILED,
    /**
     * 补偿中
     */
    COMPENSATING,
    /**
     * 已补偿
     */
    COMPENSATED
}