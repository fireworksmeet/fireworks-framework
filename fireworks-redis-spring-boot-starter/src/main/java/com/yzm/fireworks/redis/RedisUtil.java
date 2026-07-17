package com.yzm.fireworks.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis 静态工具类
 *
 * <p>所有静态方法委托给默认数据源的 {@link RedisContext}。
 * 需要切换数据源时，通过 {@link #on(String)} 获取指定数据源的 {@link RedisContext} 后调用同名方法：
 *
 * <pre>
 *     // 默认数据源
 *     RedisUtil.set("user:1", user);
 *
 *     // 指定数据源（多数据源场景），天然支持异步，无 ThreadLocal
 *     RedisUtil.on("bbb").set("user:1", user);
 *
 *     String ds = "bbb";
 *     executor.submit(() -> RedisUtil.on(ds).set("user:1", user));
 * </pre>
 *
 * <p>Spring 完成依赖注入后，{@code afterPropertiesSet()} 将实例赋给静态字段 {@code INSTANCE}，
 * 所有静态方法通过 {@code INSTANCE} 委托调用，保持静态调用体验，同时遵循 Spring 生命周期。
 *
 * @author JYuan
 */
@Slf4j
public class RedisUtil implements InitializingBean {

    // ── 静态持有者 ─────────────────────────────────────────────────────────────
    private static RedisUtil INSTANCE;

    // ── 实例字段（构造器注入）────────────────────────────────────────────────────
    private final RedisContext defaultContext;
    private final Map<String, RedisContext> contextMap;

    /**
     * 单数据源构造器（多数据源未开启时使用）
     */
    public RedisUtil(JsonRedisTemplate jsonRedisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.defaultContext = new RedisContext(jsonRedisTemplate, stringRedisTemplate);
        this.contextMap = Collections.emptyMap();
    }

    /**
     * 多数据源构造器
     *
     * @param contextMap key 为数据源名称，value 为对应的操作上下文
     */
    public RedisUtil(JsonRedisTemplate jsonRedisTemplate, StringRedisTemplate stringRedisTemplate,
                     Map<String, RedisContext> contextMap) {
        this.defaultContext = new RedisContext(jsonRedisTemplate, stringRedisTemplate);
        this.contextMap = contextMap;
    }

    @Override
    public void afterPropertiesSet() {
        INSTANCE = this;
    }

    // ── 多数据源切换 ────────────────────────────────────────────────────────────

    /**
     * 切换到指定数据源，返回该数据源的操作上下文。
     *
     * <p>返回值是一个轻量对象，不持有任何全局状态，可安全地在异步线程中使用：
     * <pre>
     *     RedisContext ctx = RedisUtil.on("bbb");
     *     executor.submit(() -> ctx.set("key:1", value));
     * </pre>
     *
     * @param datasource 数据源名称，对应配置文件中 {@code spring.data.redis.multi.datasource} 下的 key
     * @throws IllegalArgumentException 数据源名称不存在时抛出
     * @throws IllegalStateException    多数据源未开启时调用此方法时抛出
     */
    public static RedisContext on(String datasource) {
        Map<String, RedisContext> map = instance().contextMap;
        Assert.state(!map.isEmpty(), "Multi-datasource is not enabled. " +
                "Please set spring.data.redis.multi.enabled=true");
        RedisContext ctx = map.get(datasource);
        Assert.notNull(ctx, "Redis datasource '" + datasource + "' not found. " +
                "Available: " + map.keySet());
        return ctx;
    }

    // ── 模板访问 ────────────────────────────────────────────────────────────────

    public static JsonRedisTemplate getJsonRedisTemplate() {
        return instance().defaultContext.getJsonRedisTemplate();
    }

    public static StringRedisTemplate getStringRedisTemplate() {
        return instance().defaultContext.getStringRedisTemplate();
    }

    // ==================== String 操作 ====================

    public static void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        instance().defaultContext.set(key, value, timeout, timeUnit);
    }

    public static void set(String key, Object value, long timeoutSeconds) {
        instance().defaultContext.set(key, value, timeoutSeconds);
    }

    public static void set(String key, Object value) {
        instance().defaultContext.set(key, value);
    }

    public static Boolean setNx(String key, Object value, long timeout, TimeUnit timeUnit) {
        return instance().defaultContext.setNx(key, value, timeout, timeUnit);
    }

    public static Boolean expire(String key, long timeout, TimeUnit timeUnit) {
        return instance().defaultContext.expire(key, timeout, timeUnit);
    }

    public static Boolean expire(String key, long timeoutSeconds) {
        return instance().defaultContext.expire(key, timeoutSeconds);
    }

    /**
     * expireAt 是在指定的具体时间过期，expire 是相对时间
     */
    public static Boolean expireAt(String key, Date date) {
        return instance().defaultContext.expireAt(key, date);
    }

    public static <V> V get(String key, Class<V> returnType) {
        return instance().defaultContext.get(key, returnType);
    }

    /**
     * @param key           缓存的key
     * @param typeReference 使用时直接new TypeReference<返回值的类型>() {} 就可以了。
     *                      之所以使用这个是因为在Java中，运行时无法获取List<User>中的User类型信息(它会被擦除为List<Object>)。
     *                      TypeReference通过创建一个匿名内部类，利用JVM保留匿名类父类泛型信息的特性，让Jackson在运行时能够看到完整的泛型结构
     * @param <V>           泛型
     * @return 缓存的value
     */
    public static <V> V get(String key, TypeReference<V> typeReference) {
        return instance().defaultContext.get(key, typeReference);
    }

    public static <V> List<V> multiGet(List<String> keys, Class<V> returnType) {
        return instance().defaultContext.multiGet(keys, returnType);
    }

    public static <V> List<V> multiGet(List<String> keys, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.multiGet(keys, typeReference);
    }

    public static <V> List<V> multiGetNotHasNull(List<String> keys, Class<V> returnType) {
        return instance().defaultContext.multiGetNotHasNull(keys, returnType);
    }

    /**
     * 批量设置 string 类型键值对
     *
     * @param map 键值对集合
     */
    public static void multiSet(Map<String, Object> map) {
        instance().defaultContext.multiSet(map);
    }

    /**
     * 批量设置 string 类型键值对，并设置过期时间
     *
     * @param map     键值对集合
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public static void multiSet(Map<String, Object> map, long timeout, TimeUnit unit) {
        instance().defaultContext.multiSet(map, timeout, unit);
    }

    /**
     * 批量设置 string 类型键值对，并设置过期时间（秒）
     *
     * @param map            键值对集合
     * @param timeoutSeconds 过期时间（秒）
     */
    public static void multiSet(Map<String, Object> map, long timeoutSeconds) {
        instance().defaultContext.multiSet(map, timeoutSeconds);
    }

    public static Boolean delete(String key) {
        return instance().defaultContext.delete(key);
    }

    public static Long delete(Collection<String> keys) {
        return instance().defaultContext.delete(keys);
    }

    public static Boolean exist(String key) {
        return instance().defaultContext.exist(key);
    }

    // ==================== 计数器操作 ====================

    public static Long increment(String key) {
        return instance().defaultContext.increment(key);
    }

    public static Long increment(String key, long time, TimeUnit unit) {
        return instance().defaultContext.increment(key, time, unit);
    }

    public static Long increment(String key, Long value) {
        return instance().defaultContext.increment(key, value);
    }

    public static Long decrement(String key) {
        return instance().defaultContext.decrement(key);
    }

    public static Long decrement(String key, Long value) {
        return instance().defaultContext.decrement(key, value);
    }

    // ==================== Hash 操作 ====================

    public static void hashSet(String mapName, Object key, Object value) {
        instance().defaultContext.hashSet(mapName, key, value);
    }

    public static void hashMultiSet(String mapName, Map<?, ?> value) {
        instance().defaultContext.hashMultiSet(mapName, value);
    }

    public static <V> V hashGet(String mapName, Object key, Class<V> returnType) {
        return instance().defaultContext.hashGet(mapName, key, returnType);
    }

    public static <V> V hashGet(String mapName, Object key, TypeReference<V> typeReference) {
        return instance().defaultContext.hashGet(mapName, key, typeReference);
    }

    public static Long hashDelete(String mapName, Object... key) {
        return instance().defaultContext.hashDelete(mapName, key);
    }

    public static <K, V> Map<K, V> hashEntries(String mapName) {
        return instance().defaultContext.hashEntries(mapName);
    }

    public static <V> List<V> hashValues(String mapName, Class<V> returnType) {
        return instance().defaultContext.hashValues(mapName, returnType);
    }

    public static <V> List<V> hashValues(String mapName, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.hashValues(mapName, typeReference);
    }

    public static Boolean hashExist(String mapName, Object key) {
        return instance().defaultContext.hashExist(mapName, key);
    }

    public static Long hashSize(String mapName) {
        return instance().defaultContext.hashSize(mapName);
    }

    // ==================== ZSet 操作 ====================

    /**
     * 添加单个元素
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     * @return 是否添加成功（已存在则更新分数并返回 false）
     */
    public static Boolean zSetAdd(String key, Object value, double score) {
        return instance().defaultContext.zSetAdd(key, value, score);
    }

    /**
     * 批量添加元素
     *
     * @param key    键
     * @param tuples 带分数的元素集合
     * @return 新增的元素数量
     */
    public static Long zSetAddBatch(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        return instance().defaultContext.zSetAddBatch(key, tuples);
    }

    /**
     * 删除一个或多个元素
     *
     * @param key    键
     * @param values 要删除的值
     * @return 删除的元素数量
     */
    public static Long zSetRemove(String key, Object... values) {
        return instance().defaultContext.zSetRemove(key, values);
    }

    /**
     * 对指定元素的 score 增加 delta
     *
     * @param key   键
     * @param value 值
     * @param delta 增量（可为负数）
     * @return 新的 score
     */
    public static Double zSetIncrScore(String key, Object value, double delta) {
        return instance().defaultContext.zSetIncrScore(key, value, delta);
    }

    /**
     * 获取元素的 score，不存在返回 null
     */
    public static Double zSetScore(String key, Object value) {
        return instance().defaultContext.zSetScore(key, value);
    }

    /**
     * 获取元素的升序排名（从 0 开始），不存在返回 null
     */
    public static Long zSetRank(String key, Object value) {
        return instance().defaultContext.zSetRank(key, value);
    }

    /**
     * 获取元素的降序排名（从 0 开始），不存在返回 null
     */
    public static Long zSetReverseRank(String key, Object value) {
        return instance().defaultContext.zSetReverseRank(key, value);
    }

    /**
     * 获取 ZSet 元素总数量
     *
     * @param key 键
     * @return 元素数量
     */
    public static Long zSetSize(String key) {
        return instance().defaultContext.zSetSize(key);
    }

    /**
     * 统计 score 范围内的元素数量
     *
     * @param key 键
     * @param min 最小 score（含）
     * @param max 最大 score（含）
     * @return 元素数量
     */
    public static Long zSetCountByScore(String key, double min, double max) {
        return instance().defaultContext.zSetCountByScore(key, min, max);
    }

    /**
     * 按升序获取指定排名范围内的元素（不含 score）
     *
     * @param key   键
     * @param start 起始排名（0-based，含）
     * @param end   结束排名（含，-1 表示最后一个）
     * @return 元素集合
     */
    public static <V> List<V> zSetRange(String key, long start, long end, Class<V> returnType) {
        return instance().defaultContext.zSetRange(key, start, end, returnType);
    }

    public static <V> List<V> zSetRange(String key, long start, long end, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.zSetRange(key, start, end, typeReference);
    }

    /**
     * 按降序获取指定排名范围内的元素（不含 score）
     *
     * @param key   键
     * @param start 起始排名（0-based，含）
     * @param end   结束排名（含，-1 表示最后一个）
     * @return 元素集合
     */
    public static <V> List<V> zSetReverseRange(String key, long start, long end, Class<V> returnType) {
        return instance().defaultContext.zSetReverseRange(key, start, end, returnType);
    }

    public static <V> List<V> zSetReverseRange(String key, long start, long end, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.zSetReverseRange(key, start, end, typeReference);
    }

    /**
     * 按升序获取指定排名范围内的元素（含 score）
     *
     * @param key   键
     * @param start 起始排名（0-based，含）
     * @param end   结束排名（含，-1 表示最后一个）
     * @return 带 score 的元素集合
     */
    public static Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScore(String key, long start, long end) {
        return instance().defaultContext.zSetRangeWithScore(key, start, end);
    }

    /**
     * 按降序获取指定排名范围内的元素（含 score）
     *
     * @param key   键
     * @param start 起始排名（0-based，含）
     * @param end   结束排名（含，-1 表示最后一个）
     * @return 带 score 的元素集合
     */
    public static Set<ZSetOperations.TypedTuple<Object>> zSetReverseRangeWithScore(String key, long start, long end) {
        return instance().defaultContext.zSetReverseRangeWithScore(key, start, end);
    }

    /**
     * 按 score 范围升序获取元素（不含 score）
     *
     * @param key 键
     * @param min 最小 score（含）
     * @param max 最大 score（含）
     * @return 元素集合
     */
    public static <V> List<V> zSetRangeByScore(String key, double min, double max, Class<V> returnType) {
        return instance().defaultContext.zSetRangeByScore(key, min, max, returnType);
    }

    public static <V> List<V> zSetRangeByScore(String key, double min, double max, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.zSetRangeByScore(key, min, max, typeReference);
    }

    /**
     * 按 score 范围降序获取元素（不含 score）
     *
     * @param key 键
     * @param min 最小 score（含）
     * @param max 最大 score（含）
     * @return 元素集合
     */
    public static <V> List<V> zSetReverseRangeByScore(String key, double min, double max, Class<V> returnType) {
        return instance().defaultContext.zSetReverseRangeByScore(key, min, max, returnType);
    }

    public static <V> List<V> zSetReverseRangeByScore(String key, double min, double max, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.zSetReverseRangeByScore(key, min, max, typeReference);
    }

    /**
     * 按 score 范围升序分页获取元素（不含 score）
     *
     * @param key    键
     * @param min    最小 score（含）
     * @param max    最大 score（含）
     * @param offset 偏移量
     * @param count  获取数量
     * @return 元素集合
     */
    public static <V> List<V> zSetRangeByScorePage(String key, double min, double max,
                                                   long offset, long count, Class<V> returnType) {
        return instance().defaultContext.zSetRangeByScorePage(key, min, max, offset, count, returnType);
    }

    public static <V> List<V> zSetRangeByScorePage(String key, double min, double max,
                                                   long offset, long count, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.zSetRangeByScorePage(key, min, max, offset, count, typeReference);
    }

    /**
     * 按 score 范围降序分页获取元素（不含 score）
     *
     * @param key    键
     * @param min    最小 score（含）
     * @param max    最大 score（含）
     * @param offset 偏移量
     * @param count  获取数量
     * @return 元素集合
     */
    public static <V> List<V> zSetReverseRangeByScorePage(String key, double min, double max,
                                                          long offset, long count, Class<V> returnType) {
        return instance().defaultContext.zSetReverseRangeByScorePage(key, min, max, offset, count, returnType);
    }

    public static <V> List<V> zSetReverseRangeByScorePage(String key, double min, double max,
                                                          long offset, long count, TypeReference<List<V>> typeReference) {
        return instance().defaultContext.zSetReverseRangeByScorePage(key, min, max, offset, count, typeReference);
    }

    /**
     * 删除指定 score 范围内的元素
     *
     * @param key 键
     * @param min 最小 score（含）
     * @param max 最大 score（含）
     * @return 删除的元素数量
     */
    public static Long zSetRemoveByScore(String key, double min, double max) {
        return instance().defaultContext.zSetRemoveByScore(key, min, max);
    }

    /**
     * 删除指定排名范围内的元素
     *
     * @param key   键
     * @param start 起始排名（0-based，含）
     * @param end   结束排名（含，-1 表示最后一个）
     * @return 删除的元素数量
     */
    public static Long zSetRemoveByRank(String key, long start, long end) {
        return instance().defaultContext.zSetRemoveByRank(key, start, end);
    }

    // ==================== Scan 操作 ====================

    public static void scan(String pattern, Consumer<List<String>> consumer) {
        instance().defaultContext.scan(pattern, consumer);
    }

    /**
     * 调用 Redis 的 scan 命令，每次获取 batchCount 个 key，
     * 消费完后继续拉取直到全部拉取完毕
     *
     * @param pattern    key 的表达式
     * @param consumer   处理逻辑
     * @param batchCount 一次批处理的条数
     */
    public static void scan(String pattern, Consumer<List<String>> consumer, int batchCount) {
        instance().defaultContext.scan(pattern, consumer, batchCount);
    }

    // ==================== 类型转换（代理到 defaultContext）====================

    public static <V> V convert(Object value, Class<V> returnType) {
        return instance().defaultContext.convert(value, returnType);
    }

    public static <V> V convert(Object value, TypeReference<V> typeReference) {
        return instance().defaultContext.convert(value, typeReference);
    }

    public static <V> List<V> convertList(Collection<?> values, Class<V> returnType) {
        return instance().defaultContext.convertList(values, returnType);
    }

    // ==================== 私有工具 ====================

    private static RedisUtil instance() {
        Assert.notNull(INSTANCE, "RedisUtil is not initialized yet. " +
                "Ensure FireworksRedisAutoConfiguration is loaded before calling static methods.");
        return INSTANCE;
    }
}
