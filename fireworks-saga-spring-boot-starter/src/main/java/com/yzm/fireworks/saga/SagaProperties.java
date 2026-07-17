package com.yzm.fireworks.saga;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.saga")
@Data
public class SagaProperties {

    /**
     * 最大重试次数
     */
    private int maxRetries = 15;

    /**
     * 初始延迟毫秒数(默认1s)
     */
    private long initialDelayMs = 1000;

    /**
     * 最大延迟毫秒数(默认2小时)
     */
    private long maxDelayMs = 7200000;

    /**
     * 随机抖动因子
     */
    private double jitterFactor = 0.3;

    /**
     * 定时扫描SagaLog的批次大小
     */
    private int batchSize = 500;

    /**
     * 无法判断EXECUTING状态的SagaLog是正在执行saga还是执行失败，因为补偿失败时，当修改状态时出现宕机则无法正常修改状态
     * 当创建15分钟后，依然处于EXECUTING状态的视为补偿失败
     */
    private long executingTimeout = 900000;

}