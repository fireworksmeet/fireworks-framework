package com.yzm.fireworks.msg.core.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 基于 Redisson 实现的分布式限流器 (令牌桶算法)
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonRateLimiter implements RateLimiter {

    private final RedissonClient redissonClient;

    @Override
    public boolean allowRequest(String key, int limit, int windowSeconds) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

            // 尝试初始化限流配置（幂等操作，如果已存在且配置一致则不修改）
            // 这里使用 OVERALL 类型，表示全局所有节点共用此限制
            rateLimiter.trySetRate(RateType.OVERALL, limit, Duration.ofSeconds(windowSeconds));

            // 也可以设置过期时间，防止冷 Key 堆积
            rateLimiter.expireAsync(Duration.ofDays(1));

            // 尝试获取 1 个令牌
            return rateLimiter.tryAcquire();
        } catch (Exception e) {
            log.error("Redisson rate limit check error, key: {}", key, e);
            // 异常时降级放行
            return true;
        }
    }

    @Override
    public void removeRequest(String key) {
        try {
            redissonClient.getRateLimiter(key).deleteAsync();
        } catch (Exception e) {
            log.error("Redisson rate limit remove error, key: {}", key, e);
        }
    }
}
