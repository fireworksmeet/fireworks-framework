package com.yzm.fireworks.storage.orphan;

import com.yzm.fireworks.storage.service.StorageService;
import com.yzm.fireworks.storage.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 孤儿文件清理器。
 * <p>
 * 扫描 {@link OrphanRegistry} 中已过期且未被业务确认的记录，调用 {@link StorageService#deleteFile}
 * 删除对应的云存储对象，从而避免"前端获取直传凭证并上传完成后未提交业务表单"导致的文件长期堆积。
 * <p>
 * <b>框架本身不调度定时任务</b>：本类仅提供 {@link #cleanExpired()} 清理方法，由业务侧自行决定触发时机与调度器
 * （如 XXL-Job、PowerJob、Spring {@code @Scheduled} 等）。多实例部署时由调度平台保证同一时刻仅一个实例执行，
 * 且底层 {@link OrphanRegistry} 基于 Redis ZSet，天然支持分布式。
 * <p>
 * 清理策略：
 * <ul>
 *     <li>只处理已过期的记录，处于有效期内（业务可能仍在处理）的记录一律跳过；</li>
 *     <li>按 Bucket 分组进行批量删除与批量记录移除，提升清理性能与降低网络 RTT；</li>
 *     <li>批量删除失败时自动降级为单条逐一清理，避免单个非法对象卡死整批清理任务；</li>
 *     <li>删除成功后才移除待确认记录，保证「记录存在 ⇔ 对象可能未删除」的一致性；</li>
 *     <li>桶不在白名单的过期记录：仅批量移除 Redis 待确认 key、不删除存储对象，防止误删跨桶文件，同时避免 Redis 积压。</li>
 * </ul>
 */
@Slf4j
public class OrphanFileCleaner {

    private final OrphanRegistry registry;
    private final StorageService storageService;
    private final OrphanCleanupProperties properties;

    public OrphanFileCleaner(OrphanRegistry registry, StorageService storageService,
                             OrphanCleanupProperties properties) {
        this.registry = registry;
        this.storageService = storageService;
        this.properties = properties;
    }

    /**
     * 立即执行一次孤儿文件清理，可由业务侧通过任意调度器（XXL-Job、@Scheduled 等）周期性触发。
     *
     * @return 本次实际删除的对象数量
     */
    public int cleanExpired() {
        if (!properties.isEnabled()) {
            return 0;
        }
        // 批大小在注册表层（Redis 端）即已控制，这里直接消费本批过期记录。
        List<OrphanFile> expired = registry.listExpired(properties.getBatchSize());
        if (expired.isEmpty()) {
            return 0;
        }

        // 按 bucket 分组
        Map<String, List<String>> bucketGroup = expired.stream()
                .filter(p -> p != null && StringUtils.hasText(p.getBucket()) && StringUtils.hasText(p.getObjectKey()))
                .collect(Collectors.groupingBy(OrphanFile::getBucket,
                        Collectors.mapping(OrphanFile::getObjectKey, Collectors.toList())));

        int deleted = 0;
        for (Map.Entry<String, List<String>> entry : bucketGroup.entrySet()) {
            String bucket = entry.getKey();
            List<String> objects = entry.getValue();

            if (!isBucketAllowed(bucket)) {
                // 桶不在白名单：为防误删跨桶文件，不删除存储对象；但记录已过期，
                // 批量移除 Redis 中的待确认 key，避免过期登记在 Redis 中长期积压。
                registry.removeAll(bucket, objects);
                log.info("孤儿文件记录已过期但桶不在白名单, 仅批量移除登记不删除文件, bucket={}, count={}",
                        bucket, objects.size());
                continue;
            }

            try {
                // 优先走批量删除，单次 RPC 完成云服务与 Redis 批量清理
                storageService.deleteFiles(bucket, objects);
                registry.removeAll(bucket, objects);
                deleted += objects.size();
                log.info("已批量清理孤儿文件, bucket={}, count={}", bucket, objects.size());
            } catch (StorageException e) {
                log.warn("批量清理孤儿文件失败, 自动降级为单条依次清理, bucket={}, count={}, reason={}",
                        bucket, objects.size(), e.getMessage());
                // 降级为单条处理，避免单条对象异常导致整批失败
                for (String objectKey : objects) {
                    try {
                        storageService.deleteFile(bucket, objectKey);
                        registry.remove(bucket, objectKey);
                        deleted++;
                        log.info("已清理孤儿文件(降级单条), bucket={}, object={}", bucket, objectKey);
                    } catch (StorageException ex) {
                        log.warn("清理孤儿文件失败, 将在下轮重试, bucket={}, object={}, reason={}",
                                bucket, objectKey, ex.getMessage());
                    }
                }
            }
        }
        log.info("孤儿文件清理完成, 本批扫描 {} 条, 成功删除 {} 条", expired.size(), deleted);
        return deleted;
    }

    private boolean isBucketAllowed(String bucket) {
        List<String> buckets = properties.getBuckets();
        return ObjectUtils.isEmpty(buckets) || buckets.contains(bucket);
    }
}

