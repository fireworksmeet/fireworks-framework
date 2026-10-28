package com.yzm.fireworks.redis.lock;

import com.yzm.fireworks.redis.RedisConstants;
import com.yzm.fireworks.redis.RedisLookup;
import com.yzm.fireworks.redis.RedisUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
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
public class LockService {

    private final RedissonClient defaultClient;
    private final Map<String, RedissonClient> clientMap;

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
        return new LockService(RedisLookup.require(clientMap, datasource, "Redis datasource for lock"), clientMap);
    }

    /**
     * 尝试获取分布式锁，不抛异常。
     *
     * <p>返回的 {@link LockHandle} <b>必须释放</b>，推荐 try-with-resources：
     * <pre>{@code
     * try (LockService.LockHandle lock = lockService.tryLock("order:1", 0, TimeUnit.SECONDS).orElse(null)) {
     *     if (lock == null) { return; }   // 没抢到，直接跳过
     *     // 业务
     * }
     * }</pre>
     *
     * <p>只返回 {@code boolean} 会让调用方拿不到锁对象、无法释放（Redisson 的看门狗会持续续期），
     * 因此这里把锁句柄一并返回。
     *
     * @param key      锁的 key
     * @param waitTime 获取锁的最大等待时间
     * @param unit     时间单位
     * @return 抢到锁时返回句柄，抢不到（或被中断）返回 {@link Optional#empty()}
     */
    public Optional<LockHandle> tryLock(String key, long waitTime, TimeUnit unit) {
        RLock lock = defaultClient.getLock(key);
        try {
            return lock.tryLock(waitTime, unit) ? Optional.of(new LockHandle(lock)) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * 已获取的锁句柄：关闭即释放，必须在拿到它的线程上关闭。
     */
    public static final class LockHandle implements AutoCloseable {

        private final RLock lock;

        private LockHandle(RLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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
    public <T> T executeWithLock(String key, long waitTime, TimeUnit unit, SupplierThrow<T> supplier) throws Throwable {
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
            // isHeldByCurrentThread() 已包含“是否仍被锁住”的判断，再调 isLocked() 只是多一次往返
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 使用默认等待时间执行分布式锁保护的业务逻辑
     */
    public <T> T executeWithLock(String key, SupplierThrow<T> supplier) throws Throwable {
        return executeWithLock(key, RedisConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS, supplier);
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
