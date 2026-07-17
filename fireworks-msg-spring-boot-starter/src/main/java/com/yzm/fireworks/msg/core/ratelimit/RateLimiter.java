package com.yzm.fireworks.msg.core.ratelimit;

/**
 * 分布式限流器接口
 *
 * @author JYuan
 */
public interface RateLimiter {

    /**
     * 尝试获取许可
     *
     * @param key           限流 Key
     * @param limit         限制数量（令牌总数）
     * @param windowSeconds 时间窗口（秒）
     * @return true-允许访问，false-已被限流
     */
    boolean allowRequest(String key, int limit, int windowSeconds);

    /**
     * 移除限流记录（用于发送失败后的回滚）
     *
     * @param key 限流 Key
     */
    void removeRequest(String key);
}
