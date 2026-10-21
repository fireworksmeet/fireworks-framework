package com.yzm.fireworks.redis.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流自动配置。
 *
 * @author JYuan
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate) {
        return new GcraRateLimiter(redisTemplate);
    }
}
