package com.yzm.fireworks.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.yzm.fireworks.common.constants.StringPool.COLON;


/**
 * 单个 Redis 数据源的操作门面
 *
 * <p>每个实例绑定一个数据源，持有该数据源专属的 {@link JsonRedisTemplate} 和
 * {@link StringRedisTemplate}，不依赖任何全局状态（无 ThreadLocal、无静态字段）。
 *
 * <p>获取方式：
 * <pre>
 *     // 默认数据源，通过 RedisUtil 静态方法直接调用
 *     RedisUtil.set("key:1", value);
 *
 *     // 指定数据源，通过 on() 显式切换，天然支持异步
 *     RedisUtil.on("bbb").set("key:1", value);
 *
 *     String ds = "bbb";
 *     executor.submit(() -> RedisUtil.on(ds).set("key:1", value));
 * </pre>
 *
 * @author JYuan
 */
@Slf4j
@Getter
public class RedisContext {

    /**
     * Lua 脚本：原子性地执行 increment 和 expire，返回值为当前计数
     */
    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
                    "if count == 1 then " +
                    "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                    "end " +
                    "return count";

    private static final DefaultRedisScript<Long> RATE_LIMIT_REDIS_SCRIPT;

    static {
        RATE_LIMIT_REDIS_SCRIPT = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    }

    private final JsonRedisTemplate jsonRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisContext(JsonRedisTemplate jsonRedisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.jsonRedisTemplate = jsonRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== String 操作 ====================

    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        checkKey(key);
        jsonRedisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    public void set(String key, Object value, long timeoutSeconds) {
        set(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }

    public void set(String key, Object value) {
        checkKey(key);
        jsonRedisTemplate.opsForValue().set(key, value);
    }

    public Boolean setNx(String key, Object value, long timeout, TimeUnit timeUnit) {
        checkKey(key);
        return jsonRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
    }

    public Boolean expire(String key, long timeout, TimeUnit timeUnit) {
        checkKey(key);
        return jsonRedisTemplate.expire(key, timeout, timeUnit);
    }

    public Boolean expire(String key, long timeoutSeconds) {
        return expire(key, timeoutSeconds, TimeUnit.SECONDS);
    }

    public Boolean expireAt(String key, Date date) {
        checkKey(key);
        return jsonRedisTemplate.expireAt(key, date);
    }

    public <V> V get(String key, Class<V> returnType) {
        return convert(jsonRedisTemplate.opsForValue().get(key), returnType);
    }

    public <V> V get(String key, TypeReference<V> typeReference) {
        return convert(jsonRedisTemplate.opsForValue().get(key), typeReference);
    }

    public <V> List<V> multiGet(List<String> keys, Class<V> returnType) {
        return convertList(jsonRedisTemplate.opsForValue().multiGet(keys), returnType);
    }

    public <V> List<V> multiGet(List<String> keys, TypeReference<List<V>> typeReference) {
        return convert(jsonRedisTemplate.opsForValue().multiGet(keys), typeReference);
    }

    public <V> List<V> multiGetNotHasNull(List<String> keys, Class<V> returnType) {
        List<Object> list = jsonRedisTemplate.opsForValue().multiGet(keys);
        if (ObjectUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return convertList(list.stream().filter(Objects::nonNull).collect(Collectors.toList()), returnType);
    }

    public void multiSet(Map<String, Object> map) {
        if (ObjectUtils.isEmpty(map)) {
            return;
        }
        map.keySet().forEach(this::checkKey);
        jsonRedisTemplate.opsForValue().multiSet(map);
    }

    public void multiSet(Map<String, Object> map, long timeout, TimeUnit unit) {
        if (ObjectUtils.isEmpty(map)) {
            return;
        }
        map.keySet().forEach(this::checkKey);
        Jackson2JsonRedisSerializer<Object> valueSerializer = jsonRedisTemplate.getValueSerializer();
        long expireTime = unit.toSeconds(timeout);
        Map<byte[], byte[]> tuple = map.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getBytes(),
                        entry -> valueSerializer.serialize(entry.getValue())
                ));
        jsonRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.stringCommands().mSet(tuple);
            tuple.keySet().forEach(key -> connection.keyCommands().expire(key, expireTime));
            return null;
        });
    }

    public void multiSet(Map<String, Object> map, long timeoutSeconds) {
        multiSet(map, timeoutSeconds, TimeUnit.SECONDS);
    }

    public Boolean delete(String key) {
        checkKey(key);
        return jsonRedisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return 0L;
        }
        keys.forEach(this::checkKey);
        return jsonRedisTemplate.delete(keys);
    }

    public Boolean exist(String key) {
        checkKey(key);
        return jsonRedisTemplate.hasKey(key);
    }

    // ==================== 计数器操作 ====================

    public Long increment(String key) {
        checkKey(key);
        return jsonRedisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long time, TimeUnit unit) {
        return stringRedisTemplate.execute(RATE_LIMIT_REDIS_SCRIPT,
                Collections.singletonList(key), String.valueOf(unit.toSeconds(time)));
    }

    public Long increment(String key, Long value) {
        checkKey(key);
        return jsonRedisTemplate.opsForValue().increment(key, value);
    }

    public Long decrement(String key) {
        checkKey(key);
        return jsonRedisTemplate.opsForValue().decrement(key);
    }

    public Long decrement(String key, Long value) {
        checkKey(key);
        return jsonRedisTemplate.opsForValue().decrement(key, value);
    }

    // ==================== Hash 操作 ====================

    public void hashSet(String mapName, Object key, Object value) {
        checkKey(mapName);
        jsonRedisTemplate.opsForHash().put(mapName, key, value);
    }

    public void hashMultiSet(String mapName, Map<?, ?> value) {
        checkKey(mapName);
        jsonRedisTemplate.opsForHash().putAll(mapName, value);
    }

    public <V> V hashGet(String mapName, Object key, Class<V> returnType) {
        return convert(jsonRedisTemplate.opsForHash().get(mapName, key), returnType);
    }

    public <V> V hashGet(String mapName, Object key, TypeReference<V> typeReference) {
        return convert(jsonRedisTemplate.opsForHash().get(mapName, key), typeReference);
    }

    public Long hashDelete(String mapName, Object... key) {
        return jsonRedisTemplate.opsForHash().delete(mapName, key);
    }

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> hashEntries(String mapName) {
        return (Map<K, V>) jsonRedisTemplate.opsForHash().entries(mapName);
    }

    public <V> List<V> hashValues(String mapName, Class<V> returnType) {
        return convertList(jsonRedisTemplate.opsForHash().values(mapName), returnType);
    }

    public <V> List<V> hashValues(String mapName, TypeReference<List<V>> typeReference) {
        return convert(jsonRedisTemplate.opsForHash().values(mapName), typeReference);
    }

    public Boolean hashExist(String mapName, Object key) {
        return jsonRedisTemplate.opsForHash().hasKey(mapName, key);
    }

    public Long hashSize(String mapName) {
        return jsonRedisTemplate.opsForHash().size(mapName);
    }

    // ==================== ZSet 操作 ====================

    public Boolean zSetAdd(String key, Object value, double score) {
        checkKey(key);
        return jsonRedisTemplate.opsForZSet().add(key, value, score);
    }

    public Long zSetAddBatch(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        checkKey(key);
        return jsonRedisTemplate.opsForZSet().add(key, tuples);
    }

    public Long zSetRemove(String key, Object... values) {
        checkKey(key);
        return jsonRedisTemplate.opsForZSet().remove(key, values);
    }

    public Double zSetIncrScore(String key, Object value, double delta) {
        checkKey(key);
        return jsonRedisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    public Double zSetScore(String key, Object value) {
        return jsonRedisTemplate.opsForZSet().score(key, value);
    }

    public Long zSetRank(String key, Object value) {
        return jsonRedisTemplate.opsForZSet().rank(key, value);
    }

    public Long zSetReverseRank(String key, Object value) {
        return jsonRedisTemplate.opsForZSet().reverseRank(key, value);
    }

    public Long zSetSize(String key) {
        return jsonRedisTemplate.opsForZSet().size(key);
    }

    public Long zSetCountByScore(String key, double min, double max) {
        return jsonRedisTemplate.opsForZSet().count(key, min, max);
    }

    public <V> List<V> zSetRange(String key, long start, long end, Class<V> returnType) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().range(key, start, end);
        return ObjectUtils.isEmpty(set) ? Collections.emptyList() : convertList(set, returnType);
    }

    public <V> List<V> zSetRange(String key, long start, long end, TypeReference<List<V>> typeReference) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().range(key, start, end);
        return convert(set, typeReference);
    }

    public <V> List<V> zSetReverseRange(String key, long start, long end, Class<V> returnType) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return ObjectUtils.isEmpty(set) ? Collections.emptyList() : convertList(set, returnType);
    }

    public <V> List<V> zSetReverseRange(String key, long start, long end, TypeReference<List<V>> typeReference) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return convert(set, typeReference);
    }

    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScore(String key, long start, long end) {
        return jsonRedisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    public Set<ZSetOperations.TypedTuple<Object>> zSetReverseRangeWithScore(String key, long start, long end) {
        return jsonRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
    }

    public <V> List<V> zSetRangeByScore(String key, double min, double max, Class<V> returnType) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().rangeByScore(key, min, max);
        return ObjectUtils.isEmpty(set) ? Collections.emptyList() : convertList(set, returnType);
    }

    public <V> List<V> zSetRangeByScore(String key, double min, double max, TypeReference<List<V>> typeReference) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().rangeByScore(key, min, max);
        return convert(set, typeReference);
    }

    public <V> List<V> zSetReverseRangeByScore(String key, double min, double max, Class<V> returnType) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().reverseRangeByScore(key, min, max);
        return ObjectUtils.isEmpty(set) ? Collections.emptyList() : convertList(set, returnType);
    }

    public <V> List<V> zSetReverseRangeByScore(String key, double min, double max, TypeReference<List<V>> typeReference) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().reverseRangeByScore(key, min, max);
        return convert(set, typeReference);
    }

    public <V> List<V> zSetRangeByScorePage(String key, double min, double max,
                                             long offset, long count, Class<V> returnType) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().rangeByScore(key, min, max, offset, count);
        return ObjectUtils.isEmpty(set) ? Collections.emptyList() : convertList(set, returnType);
    }

    public <V> List<V> zSetRangeByScorePage(String key, double min, double max,
                                             long offset, long count, TypeReference<List<V>> typeReference) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().rangeByScore(key, min, max, offset, count);
        return convert(set, typeReference);
    }

    public <V> List<V> zSetReverseRangeByScorePage(String key, double min, double max,
                                                    long offset, long count, Class<V> returnType) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().reverseRangeByScore(key, min, max, offset, count);
        return ObjectUtils.isEmpty(set) ? Collections.emptyList() : convertList(set, returnType);
    }

    public <V> List<V> zSetReverseRangeByScorePage(String key, double min, double max,
                                                    long offset, long count, TypeReference<List<V>> typeReference) {
        Set<Object> set = jsonRedisTemplate.opsForZSet().reverseRangeByScore(key, min, max, offset, count);
        return convert(set, typeReference);
    }

    public Long zSetRemoveByScore(String key, double min, double max) {
        checkKey(key);
        return jsonRedisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    public Long zSetRemoveByRank(String key, long start, long end) {
        checkKey(key);
        return jsonRedisTemplate.opsForZSet().removeRange(key, start, end);
    }

    // ==================== Scan 操作 ====================

    public void scan(String pattern, Consumer<List<String>> consumer) {
        scan(pattern, consumer, Constants.DEFAULT_BATCH_COUNT);
    }

    public void scan(String pattern, Consumer<List<String>> consumer, int batchCount) {
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().count(batchCount).match(pattern).build())) {
            List<String> keys = new ArrayList<>();
            while (cursor.hasNext()) {
                keys.add(cursor.next());
                if (keys.size() >= batchCount) {
                    consumer.accept(keys);
                    keys.clear();
                }
            }
            if (!keys.isEmpty()) {
                consumer.accept(keys);
            }
        }
    }

    // ==================== 类型转换 ====================

    public <V> V convert(Object value, Class<V> returnType) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        try {
            return jsonRedisTemplate.getObjectMapper().convertValue(value, returnType);
        } catch (Exception e) {
            log.error("Redis convert error, returnType: {}, value: {}", returnType.getName(), value, e);
            throw e;
        }
    }

    public <V> V convert(Object value, TypeReference<V> typeReference) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        try {
            return jsonRedisTemplate.getObjectMapper().convertValue(value, typeReference);
        } catch (Exception e) {
            log.error("Redis convert error, typeReference: {}, value: {}", typeReference.getType(), value, e);
            throw e;
        }
    }

    public <V> List<V> convertList(Collection<?> values, Class<V> returnType) {
        if (ObjectUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = jsonRedisTemplate.getObjectMapper();
            JavaType javaType = mapper.getTypeFactory().constructParametricType(List.class, returnType);
            return mapper.convertValue(values, javaType);
        } catch (Exception e) {
            log.error("Redis convert list error, returnType: {}, values: {}", returnType.getName(), values, e);
            throw e;
        }
    }

    // ==================== 私有工具 ====================

    private void checkKey(String key) {
        Assert.isTrue(StringUtils.hasText(key) && key.contains(COLON),
                key + " Must contain '" + COLON + "' ");
    }
}
