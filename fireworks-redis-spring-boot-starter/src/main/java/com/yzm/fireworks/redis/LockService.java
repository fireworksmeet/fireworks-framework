package com.yzm.fireworks.redis;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务
 *
 * <p>支持多数据源：通过 {@link #on(String)} 切换到指定数据源的 {@link RedissonClient}，
 * 用法与 {@link RedisUtil#on(String)} 保持一致。
 *
 * <p>单数据源模式下，直接注入使用即可；多数据源模式下，默认使用 primary 数据源，
 * 需要操作其他数据源时调用 {@code lockService.on("datasource")}。
 *
 * @author JYuan
 */
@Slf4j
public class LockService {

    private final RedissonClient defaultClient;
    private final Map<String, RedissonClient> clientMap;

    /**
     * 单数据源构造器
     */
    public LockService(RedissonClient redissonClient) {
        this.defaultClient = redissonClient;
        this.clientMap = Collections.emptyMap();
    }

    /**
     * 多数据源构造器
     *
     * @param defaultClient primary 数据源的 RedissonClient
     * @param clientMap     数据源名称 → RedissonClient
     */
    public LockService(RedissonClient defaultClient, Map<String, RedissonClient> clientMap) {
        this.defaultClient = defaultClient;
        this.clientMap = clientMap != null ? clientMap : Collections.emptyMap();
    }

    /**
     * 切换到指定数据源，返回绑定该数据源的 LockService 视图。
     *
     * <p>返回的 LockService 与原始实例共享 {@link #clientMap}，不会创建额外对象。
     *
     * @param datasource 数据源名称
     * @return 绑定到指定数据源的 LockService
     * @throws IllegalArgumentException 数据源不存在时抛出
     */
    public LockService on(String datasource) {
        RedissonClient client = clientMap.get(datasource);
        if (client == null) {
            throw new IllegalArgumentException(
                    "Unknown Redis datasource for lock: '" + datasource + "'. Available: " + clientMap.keySet());
        }
        return new LockService(client, clientMap);
    }

    /**
     * 尝试获取分布式锁，不抛异常
     *
     * @param key      锁的 key
     * @param waitTime 获取锁的最大等待时间
     * @param unit     时间单位
     * @return 是否成功获取锁
     */
    public boolean tryLock(String key, long waitTime, TimeUnit unit) {
        RLock lock = defaultClient.getLock(key);
        try {
            return lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 获取分布式锁，失败时抛出异常
     *
     * @param key      锁的 key
     * @param waitTime 获取锁的最大等待时间
     * @param unit     时间单位
     * @throws LockAcquisitionException 获取锁失败时抛出
     */
    public void lockOrThrow(String key, long waitTime, TimeUnit unit) {
        if (!tryLock(key, waitTime, unit)) {
            throw new LockAcquisitionException(key);
        }
    }

    /**
     * 在分布式锁保护下执行业务逻辑
     *
     * <p>注意：方法签名保留 {@code throws Throwable}，因为 {@link SupplierThrow#get()}
     * 的典型传入值是 {@code invocation::proceed}，其接口声明就是 {@code throws Throwable}，
     * 缩窄签名会导致调用处编译失败。
     *
     * @throws LockAcquisitionException 获取锁失败（含被中断）时抛出
     * @throws Throwable                业务逻辑本身抛出的异常
     */
    public <T> T executeWithLock(String key, int waitTime, TimeUnit unit, SupplierThrow<T> supplier) throws Throwable {
        RLock lock = defaultClient.getLock(key);
        boolean lockSuccess;
        try {
            lockSuccess = lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(key, "Thread interrupted while acquiring lock: " + key, e);
        }
        if (!lockSuccess) {
            throw new LockAcquisitionException(key);
        }
        try {
            return supplier.get();
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 使用默认等待时间执行分布式锁保护的业务逻辑
     */
    public <T> T executeWithLock(String key, SupplierThrow<T> supplier) throws Throwable {
        return executeWithLock(key, Constants.LOCK_WAIT_TIME, TimeUnit.SECONDS, supplier);
    }

    /**
     * 可抛出任意异常的 Supplier。
     *
     * <p>保留 {@code throws Throwable} 而非 {@code throws Exception} 的原因：
     * 调用处通常传入 {@code invocation::proceed}，该方法接口声明为 {@code throws Throwable}，
     * 若此处缩窄则会导致 lambda 表达式编译报错。
     */
    @FunctionalInterface
    public interface SupplierThrow<T> {
        T get() throws Throwable;
    }
}
