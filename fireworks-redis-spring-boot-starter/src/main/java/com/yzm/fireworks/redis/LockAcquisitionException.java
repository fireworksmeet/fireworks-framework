package com.yzm.fireworks.redis;

import lombok.Getter;

/**
 * 分布式锁获取失败异常
 *
 * @author JYuan
 */
@Getter
public class LockAcquisitionException extends RuntimeException {

    private final String lockKey;

    public LockAcquisitionException(String lockKey) {
        super("Failed to acquire distributed lock: " + lockKey);
        this.lockKey = lockKey;
    }

    public LockAcquisitionException(String lockKey, String message, Throwable cause) {
        super(message, cause);
        this.lockKey = lockKey;
    }

}