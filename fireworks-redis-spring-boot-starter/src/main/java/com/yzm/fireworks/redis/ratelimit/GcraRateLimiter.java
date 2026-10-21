package com.yzm.fireworks.redis.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * GCRA 限流实现，突发容量 1、严格速率。
 * <p>
 * 每个 key 只存一个「下次允许时间」，O(1) 内存。放行条件只有一个：{@code now >= 下次允许时间}，
 * 放行后把下次允许时间推后一个 {@code interval}。因此任意时刻最多放行 1 个请求，相邻两次放行
 * 至少间隔 {@code window / limit}，长期速率不超过 {@code limit / window}；不存在固定窗口在
 * 窗口交界处放行 2 倍流量的空隙。
 * <p>
 * 时间取各实例本地时钟，依赖 NTP 同步：偏差会让少数请求被提前或推迟放行。
 *
 * @author JYuan
 */
public class GcraRateLimiter implements RateLimiter {

    /**
     * KEYS[1]=key；ARGV[1]=now(ms)；ARGV[2]=interval(ms)。算法说明见脚本内注释。
     */
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            -- GCRA（严格速率、无突发）：key 存「下次允许时间」，0 表示从未放行。
            -- 1) 未到下次允许时间 → 拒绝；到点 → 放行，并把下次允许时间推后一个 interval。
            -- 2) 判据是到达时间，不是窗口内计数：没有固定窗口的窗口边界，也就不会出现
            --    「第 59 秒 100 次 + 第 61 秒 100 次」这种交界处放行 2 倍流量的情况。
            -- 3) 任意时刻最多放行 1 个请求，相邻放行至少间隔 interval，长期速率 limit/window。
            -- 4) TTL 只用于回收空闲 key：超过 interval 没有请求时，旧值已过期或已失效。
            local nextAllowed = tonumber(redis.call('get', KEYS[1]) or 0)
            local now = tonumber(ARGV[1])
            local interval = tonumber(ARGV[2])
            if now < nextAllowed then return 0 end
            redis.call('set', KEYS[1], now + interval, 'PX', math.ceil(interval) + 1000)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public GcraRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(RateLimitRequest request) {
        if (request.limit() <= 0 || request.window() == null || request.window().isZero()) {
            return true;
        }
        long intervalMillis = Math.max(1, request.window().toMillis() / request.limit());
        Long result = redisTemplate.execute(
                SCRIPT,
                List.of(request.key()),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(intervalMillis));
        return result == 1L;
    }
}
