package com.yzm.fireworks.redis.ratelimit;

import com.yzm.fireworks.common.ratelimit.RateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流自动配置：把 Redis 实现注册为 {@link RateLimiter}。
 *
 * <p><b>必须排在 Boot 的 {@code RedisAutoConfiguration} 之后</b>：本类用
 * {@code @ConditionalOnBean(StringRedisTemplate.class)} 判断模板是否存在，
 * 而条件按自动配置的注册顺序评估——排在前面的类看不到后面才注册的 Bean，
 * 结果是限流器静默不创建。
 *
 * <p>业务注册自己的 {@link RateLimiter} 时本实现让位。
 *
 * @author JYuan
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate) {
        return new GcraRateLimiter(redisTemplate);
    }
}
