package com.yzm.fireworks.redis;

import com.yzm.fireworks.redis.lock.LockService;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源生命周期管理器。
 *
 * <p>职责单一：持有所有数据源的 {@link LettuceConnectionFactory}、{@link RedisContext} 和 {@link RedissonClient}，
 * 在 Spring 容器关闭时统一销毁。
 *
 * <p>不做任何路由：路由通过 {@link RedisUtil#on(String)} 和 {@link LockService#on(String)} 显式指定。
 *
 * <p>三个 Map 在构造时做防御性拷贝并置为只读（保留插入顺序，日志按配置顺序输出），
 * 调用方之后修改原 Map 不会影响已注册的数据源。
 *
 * @param primary     默认数据源名称，必须存在于下面三个 Map 中
 * @param factoryMap  数据源名称 → 连接工厂，仅用于生命周期管理
 * @param contextMap  数据源名称 → 操作上下文，供 {@link RedisUtil#on(String)} 使用
 * @param redissonMap 数据源名称 → RedissonClient，供 {@link LockService#on(String)} 使用
 * @author JYuan
 */
public record MultiRedisManager(String primary,
                                Map<String, LettuceConnectionFactory> factoryMap,
                                Map<String, RedisContext> contextMap,
                                Map<String, RedissonClient> redissonMap) {

    private static final Logger log = LoggerFactory.getLogger(MultiRedisManager.class);

    public MultiRedisManager {
        factoryMap = immutable(factoryMap);
        contextMap = immutable(contextMap);
        redissonMap = immutable(redissonMap);
    }

    /**
     * primary 数据源的连接工厂（注册为 {@code @Primary} Bean）。
     */
    public LettuceConnectionFactory primaryFactory() {
        return RedisLookup.require(factoryMap, primary, "Redis 连接工厂");
    }

    /**
     * primary 数据源的 RedissonClient（注册为 {@code @Primary} Bean）。
     */
    public RedissonClient primaryRedisson() {
        return RedisLookup.require(redissonMap, primary, "RedissonClient");
    }

    /**
     * primary 数据源的操作上下文（模板 Bean 复用它里面的实例，避免同一工厂上出现两份）。
     */
    public RedisContext primaryContext() {
        return RedisLookup.require(contextMap, primary, "RedisContext");
    }

    @PreDestroy
    public void destroy() {
        release(factoryMap, redissonMap, "shutdown");
    }

    /**
     * 释放连接资源：容器关闭与「启动失败回滚」共用同一套逻辑。
     *
     * @param phase 日志前缀，用于区分正常关闭（{@code shutdown}）与回滚（{@code rollback}）
     */
    static void release(Map<String, LettuceConnectionFactory> factories,
                        Map<String, RedissonClient> clients, String phase) {
        clients.forEach((name, client) -> {
            try {
                client.shutdown();
                log.info("[{}] Redisson datasource '{}' shutdown", phase, name);
            } catch (Exception e) {
                log.warn("[{}] Failed to shutdown Redisson datasource '{}'", phase, name, e);
            }
        });
        factories.forEach((name, factory) -> {
            try {
                factory.destroy();
                log.info("[{}] Redis datasource '{}' destroyed", phase, name);
            } catch (Exception e) {
                log.warn("[{}] Failed to destroy Redis datasource '{}'", phase, name, e);
            }
        });
    }

    /**
     * 只输出数据源名，不打印连接工厂与客户端（它们的 toString 没有可用信息）。
     */
    @Override
    public String toString() {
        return "MultiRedisManager[datasources=" + factoryMap.keySet() + "]";
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
