package com.yzm.fireworks.redis;

/**
 * @author JYuan
 */
public interface Constants {
    /**
     * redis协议
     */
    String REDIS_PROTOCOL = "redis";
    /**
     * redis协议前缀
     */
    String REDIS_PROTOCOL_PREFIX = "redis://";
    /**
     * rediss协议(进行了ssl加密)
     */
    String REDISS_PROTOCOL = "rediss";
    /**
     * rediss协议前缀(进行了ssl加密)
     */
    String REDISS_PROTOCOL_PREFIX = "rediss://";
    /**
     * 锁等待时间
     */
    int LOCK_WAIT_TIME = 60;
    /**
     * 默认连接超时时间
     */
    int CONNECT_TIME_OUT = 10000;
    /**
     * 批处理的条数
     */
    int DEFAULT_BATCH_COUNT = 1000;
}
