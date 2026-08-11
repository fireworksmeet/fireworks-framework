package com.yzm.fireworks.storage.core.orphan;

import com.yzm.fireworks.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Redis ZSet 的待确认文件注册表，作为孤儿文件清理的默认分布式实现。
 * <p>
 * 数据结构：
 * <ul>
 *     <li>key  ：{@code fireworks.storage.orphan-cleanup.redis-key}，默认 {@code fireworks:storage:orphan:pending}；</li>
 *     <li>member：{@link PendingFile} 对象（利用底层 {@code JsonRedisTemplate} 的 Jackson 自动完成 JSON 序列化与反序列化）；</li>
 *     <li>score：期望确认的截止时间戳（毫秒，即 expireAt）。</li>
 * </ul>
 * 利用 ZSet 天然按 score 有序、且 Redis 单线程保证 ZADD/ZREM 原子性，在高并发与多实例场景下
 * 登记、确认、过期扫描均安全一致：
 * <ul>
 *     <li>{@link #markPending} → ZADD；</li>
 *     <li>{@link #confirm} / {@link #remove} → ZREM；</li>
 *     <li>{@link #listExpired} → ZRANGEBYSCORE(0, now) 秒级取出已过期记录。</li>
 * </ul>
 * 框架本身不调度定时任务，由业务侧自行决定触发 {@link OrphanFileCleaner#cleanExpired()} 的时机与调度器。
 */
@Slf4j
public class RedisPendingFileRegistry implements PendingFileRegistry {

    private final OrphanCleanupProperties properties;

    public RedisPendingFileRegistry(OrphanCleanupProperties properties) {
        this.properties = properties;
    }

    private String key() {
        return properties.getRedisKey();
    }

    @Override
    public void markPending(String bucket, String objectKey, Duration ttl) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = ttl != null ? ttl.toMillis() : properties.getDefaultTtl().toMillis();
        if (ttlMillis <= 0) {
            ttlMillis = properties.getDefaultTtl().toMillis();
        }
        long expireAt = now + ttlMillis;
        // 直接保存 PendingFile 对象，RedisUtil 底层的 JsonRedisTemplate 会自动序列化为 JSON 存储
        RedisUtil.zSetAdd(key(), new PendingFile(bucket, objectKey), expireAt);
        log.info("已登记待确认文件, bucket={}, object={}, expireAt={}", bucket, objectKey, expireAt);
    }

    @Override
    public void confirm(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey)) {
            return;
        }
        Long removed = RedisUtil.zSetRemove(key(), new PendingFile(bucket, objectKey));
        if (removed != null && removed > 0) {
            log.info("文件已确认, 移除待确认记录, bucket={}, object={}", bucket, objectKey);
        }
    }

    @Override
    public void confirm(String bucket, List<String> objectKeys) {
        if (!StringUtils.hasText(bucket) || CollectionUtils.isEmpty(objectKeys)) {
            return;
        }
        List<PendingFile> files = objectKeys.stream()
                .filter(StringUtils::hasText)
                .map(obj -> new PendingFile(bucket, obj))
                .toList();
        if (!files.isEmpty()) {
            Long removed = RedisUtil.zSetRemove(key(), files.toArray());
            if (removed != null && removed > 0) {
                log.info("文件已批量确认, 批量移除待确认记录, bucket={}, count={}", bucket, removed);
            }
        }
    }

    @Override
    public void confirmFiles(List<PendingFile> pendingFiles) {
        if (CollectionUtils.isEmpty(pendingFiles)) {
            return;
        }
        List<PendingFile> validFiles = pendingFiles.stream()
                .filter(pf -> pf != null && StringUtils.hasText(pf.getBucket()) && StringUtils.hasText(pf.getObjectKey()))
                .collect(Collectors.toList());
        if (!validFiles.isEmpty()) {
            Long removed = RedisUtil.zSetRemove(key(), validFiles.toArray());
            if (removed != null && removed > 0) {
                log.info("文件已批量确认(跨桶), 批量移除待确认记录, count={}", removed);
            }
        }
    }

    @Override
    public List<PendingFile> listExpired(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        // 在 Redis 端按 count 限制一次取出的数量，避免全量取出造成内存与网络压力。
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                RedisUtil.zSetRangeByScoreWithScoresPage(key(), 0.0D, (double) now, 0L, (long) limit);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        List<PendingFile> result = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            Object value = tuple.getValue();
            if (value == null) {
                continue;
            }
            try {
                // 利用 RedisUtil.convert 自动反序列化成 PendingFile 对象
                PendingFile pending = RedisUtil.convert(value, PendingFile.class);
                if (pending != null && StringUtils.hasText(pending.getBucket()) && StringUtils.hasText(pending.getObjectKey())) {
                    result.add(pending);
                }
            } catch (Exception e) {
                log.warn("无法解析待确认文件记录, 跳过: {}", value);
            }
        }
        return result;
    }

    @Override
    public void remove(String bucket, String objectKey) {
        confirm(bucket, objectKey);
    }

    @Override
    public void removeAll(String bucket, List<String> objectKeys) {
        confirm(bucket, objectKeys);
    }

    /**
     * 当前待确认记录总数（含未过期与已过期未清理），仅用于观测。
     */
    public long size() {
        Long size = RedisUtil.zSetSize(key());
        return size == null ? 0L : size;
    }
}



