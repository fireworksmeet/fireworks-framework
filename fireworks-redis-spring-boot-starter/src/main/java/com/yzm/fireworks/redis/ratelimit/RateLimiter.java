package com.yzm.fireworks.redis.ratelimit;

/**
 * 分布式限流器。
 * <p>
 * 只有 Redis 实现：限流的语义就是跨实例共享配额。
 * <p>
 * key 由调用方给定并原样使用，实现不加前缀、不做改写：key 属于调用方的命名空间，框架不应隐藏任何
 * 转换，否则排查与清理都得先知道这条规则。
 *
 * @author JYuan
 */
public interface RateLimiter {

    /**
     * 尝试获取一次许可。
     *
     * @param request 限流请求
     * @return true 表示允许
     */
    boolean tryAcquire(RateLimitRequest request);
}
