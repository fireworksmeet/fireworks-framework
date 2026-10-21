package com.yzm.fireworks.redis.ratelimit;

import com.yzm.fireworks.redis.RedisUtil;

import java.time.Duration;

/**
 * 限流请求。
 *
 * @param key    业务 key，须符合 {@code namespace:name} 约定（见 {@link RedisUtil#checkKey(String)}）
 * @param limit  窗口内允许的次数
 * @param window 窗口长度
 * @author JYuan
 */
public record RateLimitRequest(String key, long limit, Duration window) {

    public RateLimitRequest {
        RedisUtil.checkKey(key);
    }
}
