package com.yzm.fireworks.common.ratelimit;

/**
 * 分布式限流契约。
 *
 * <p><b>为什么在 common</b>：{@code message} 需要限流能力，但不该为了一个接口引入整个 redis starter。
 * 契约放这里，实现由 {@code fireworks-redis} 提供（Redis + GCRA），业务也可以注册自己的实现
 * （Sentinel、网关限流、内存令牌桶等），message 通过 {@code ObjectProvider} 自动选中。
 *
 * <p>key 由调用方给定并原样使用，实现不加前缀、不做改写：key 属于调用方的命名空间，
 * 框架不应隐藏任何转换，否则排查与清理都得先知道这条规则。
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
