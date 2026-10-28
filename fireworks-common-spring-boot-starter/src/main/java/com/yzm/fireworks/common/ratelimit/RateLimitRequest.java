package com.yzm.fireworks.common.ratelimit;

import java.time.Duration;

/**
 * 限流请求（跨模块契约的数据载体）。
 *
 * <p><b>这里不校验 key 格式</b>：key 的约束属于具体实现——Redis 实现要求 {@code namespace:name}
 * （便于集群里按前缀排查与清理），内存令牌桶、网关限流等实现可以自由定义。
 * 契约层强加格式会让非 Redis 的实现无法使用。
 *
 * @param key    限流 key，由实现解释
 * @param limit  窗口内允许的次数
 * @param window 窗口长度
 * @author JYuan
 */
public record RateLimitRequest(String key, long limit, Duration window) {
}
