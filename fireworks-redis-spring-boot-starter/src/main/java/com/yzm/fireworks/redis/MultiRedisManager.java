package com.yzm.fireworks.redis;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Map;

/**
 * 多数据源生命周期管理器
 *
 * <p>职责单一：持有所有数据源的 {@link LettuceConnectionFactory}、{@link RedisContext} 和 {@link RedissonClient}，
 * 在 Spring 容器关闭时统一销毁。
 *
 * <p>不再承担任何路由职责，路由逻辑通过 {@link RedisUtil#on(String)} 和 {@link LockService#on(String)} 的显式调用完成。
 *
 * @author JYuan
 */
@Slf4j
@Getter
public class MultiRedisManager implements DisposableBean {

    /**
     * 数据源名称 → 连接工厂，仅用于生命周期管理
     */
    private final Map<String, LettuceConnectionFactory> factoryMap;

    /**
     * 数据源名称 → 操作上下文，供 {@link RedisUtil#on(String)} 使用
     */
    private final Map<String, RedisContext> contextMap;

    /**
     * 数据源名称 → RedissonClient，供 {@link LockService#on(String)} 使用
     */
    private final Map<String, RedissonClient> redissonMap;

    public MultiRedisManager(Map<String, LettuceConnectionFactory> factoryMap,
                             Map<String, RedisContext> contextMap,
                             Map<String, RedissonClient> redissonMap) {
        this.factoryMap = factoryMap;
        this.contextMap = contextMap;
        this.redissonMap = redissonMap;
    }

    @Override
    public void destroy() {
        redissonMap.forEach((name, client) -> {
            try {
                client.shutdown();
                log.info("Redisson datasource '{}' shutdown", name);
            } catch (Exception e) {
                log.warn("Failed to shutdown Redisson datasource '{}'", name, e);
            }
        });
        factoryMap.forEach((name, factory) -> {
            try {
                factory.destroy();
                log.info("Redis datasource '{}' destroyed", name);
            } catch (Exception e) {
                log.warn("Failed to destroy Redis datasource '{}'", name, e);
            }
        });
    }
}
