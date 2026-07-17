package com.yzm.fireworks.redis;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.redis.Constants.LOCK_WAIT_TIME;


/**
 * @author JYuan
 * 用于添加分布式锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface DistributedLock {
    /**
     * key的前缀,默认取方法全限定名，除非我们在不同方法上对同一个资源做分布式锁，就自己指定
     */
    String prefixKey() default "";

    /**
     * springEl 表达式，可以使用 #方法的参数名 ,最终 lock的key = prefixKey:解析出来的值
     */
    String key() default "";

    /**
     * 等待锁的时间，默认60s，期间获取不到锁，则报错
     */
    int waitTime() default LOCK_WAIT_TIME;

    /**
     * 等待锁的时间单位，默认秒
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 指定锁所在的 Redis 数据源名称。
     *
     * <p>在多数据源场景下，不同服务可能 primary 数据源不同，
     * 但需要对同一个 Redis 实例加锁（如订单库）。
     * 通过此属性指定目标数据源，确保所有服务锁在同一个 Redis 实例上。
     *
     * <p>留空则使用当前服务的 primary 数据源。
     */
    String datasource() default "";
}
