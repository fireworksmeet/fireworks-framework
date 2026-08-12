package com.yzm.fireworks.storage.orphan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 孤儿文件清理相关配置，前缀 {@code fireworks.storage.orphan-cleanup}。
 */
@Data
@ConfigurationProperties(prefix = "fireworks.storage.orphan-cleanup")
public class OrphanCleanupProperties {

    /**
     * 是否启用孤儿文件清理能力，默认 true。
     * 关闭后待确认记录仍可登记/确认，但业务侧不会触发清理（框架本身不调度定时任务）。
     */
    private boolean enabled = true;

    /**
     * 是否在签发直传凭证时自动登记待确认记录，默认 true。
     * <p>
     * 为 true 时，通过 {@code DirectUploadService} 签发凭证（预签名 PUT / 表单直传）后会自动调用
     * {@code OrphanFileGuard.pending(...)} 登记待确认记录，业务方无需手动调用 pending，避免漏写导致孤儿文件保护失效。
     * 为 false 时，签发凭证仅返回凭证、不自动登记，由业务方自行决定登记时机。
     */
    private boolean autoMarkPending = true;

    /**
     * 孤儿文件待确认注册表使用的 Redis ZSet 键名，默认 {@code fireworks:storage:orphan:pending}。
     * 多套环境（如不同命名空间）可通过该键做隔离。
     */
    private String redisKey = "fireworks:storage:orphan:pending";

    /**
     * 默认桶名，用于 {@code @AutoConfirmFile} 解析到的文件对象未携带桶名时的兜底。
     * 使用该能力时，业务登记与确认需使用同一桶名；通常与直传签发时使用的桶一致。
     */
    private String defaultBucket = "";

    /**
     * 待确认记录的默认有效时长（TTL），超过该时长未确认即视为孤儿，默认 1 天。
     * 业务侧在 {@code markPending} 时未显式指定 TTL 时使用该值。
     */
    private Duration defaultTtl = Duration.ofDays(1);

    /**
     * 单次扫描最多清理的记录数量，默认 100，防止一次清理过多对象造成压力。
     */
    private int batchSize = 100;

    /**
     * 允许删除存储文件的桶白名单，用于防止孤儿清理误删跨桶的关键文件。
     * <p>
     * 为空表示不限制（对登记过的桶都删除对应存储对象）。
     * 非空时，仅白名单内的桶会被删除存储对象；白名单外的桶即使登记过期，
     * 也只移除 Redis 待确认 key、不删除存储对象——既避免误删，也防止过期登记在 Redis 中长期积压。
     */
    private List<String> buckets = new ArrayList<>();
}
