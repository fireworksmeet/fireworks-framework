package com.yzm.fireworks.redis;

import lombok.experimental.UtilityClass;

/**
 * Redis 模块常量。
 *
 * <p>用 {@code @UtilityClass} 而不是 interface：常量接口会让实现类继承一堆无关常量
 * （常量接口反模式）；同时自动获得 {@code final} 与私有构造，省掉手写样板。
 *
 * @author JYuan
 */
@UtilityClass
public class RedisConstants {

    /**
     * redis 协议前缀
     */
    public static final String REDIS_PROTOCOL_PREFIX = "redis://";

    /**
     * rediss 协议前缀（进行了 SSL 加密）
     */
    public static final String REDISS_PROTOCOL_PREFIX = "rediss://";

    /**
     * 锁的默认等待时间（秒）
     */
    public static final int LOCK_WAIT_TIME = 60;

    /**
     * 默认连接超时时间（毫秒）
     */
    public static final int CONNECT_TIME_OUT = 10000;

    /**
     * SCAN 批处理条数
     */
    public static final int DEFAULT_BATCH_COUNT = 1000;
}
