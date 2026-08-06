package com.yzm.fireworks.storage.core.orphan;

import java.time.Duration;
import java.util.List;

/**
 * 孤儿文件防护门面，供业务层以最简单的方式接入待确认/确认流程。
 * <p>
 * 典型用法：
 * <pre>{@code
 * // 1. 发放直传凭证后，登记待确认记录（此时前端可能尚未上传，或已上传但未提交表单）
 * UploadCredential credential = directUploadService.getUploadCredential(bucket, objectName, duration);
 * orphanFileGuard.pending(bucket, credential.getObjectName(), Duration.ofHours(2));
 *
 * // 2. 业务处理成功（表单提交落库等）后，确认该文件已被正常使用
 * orphanFileGuard.confirm(bucket, objectName);
 * }</pre>
 * 超过待确认有效期仍未 confirm 的对象，会被 {@link OrphanFileCleaner} 定时清理。
 * 由于 {@code objectName} 在签发凭证时即由后端精确生成（写死在 formData 的 key 中），
 * 因此业务侧用 {@code credential.getObjectName()} 即可直接登记与确认，无需额外处理。
 */
public class OrphanFileGuard {

    private final PendingFileRegistry registry;

    public OrphanFileGuard(PendingFileRegistry registry) {
        this.registry = registry;
    }

    /**
     * 登记一条待确认记录，TTL 使用清理器配置的默认值。
     */
    public void pending(String bucket, String objectName) {
        pending(bucket, objectName, null);
    }

    /**
     * 登记一条待确认记录，显式指定有效时长。
     */
    public void pending(String bucket, String objectName, Duration ttl) {
        registry.markPending(bucket, objectName, ttl);
    }

    /**
     * 确认文件已被业务正常使用，不再视为孤儿。记录不存在时静默忽略。
     */
    public void confirm(String bucket, String objectName) {
        registry.confirm(bucket, objectName);
    }

    /**
     * 批量确认同一桶下的多个文件。
     */
    public void confirm(String bucket, List<String> objectNames) {
        registry.confirm(bucket, objectNames);
    }

    /**
     * 批量确认不同桶的待确认文件记录。
     */
    public void confirm(List<PendingFile> pendingFiles) {
        registry.confirmFiles(pendingFiles);
    }
}

