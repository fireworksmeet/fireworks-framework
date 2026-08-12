package com.yzm.fireworks.storage.service;

import com.yzm.fireworks.common.util.TimeUtil;
import com.yzm.fireworks.storage.model.dto.DirectUploadCredential;
import com.yzm.fireworks.storage.model.util.ObjectKeyUtil;
import com.yzm.fireworks.storage.orphan.OrphanCleanupProperties;
import com.yzm.fireworks.storage.orphan.OrphanFileGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 客户端直传凭证服务接口
 */
@Slf4j
public abstract class AbstractDirectUploadService implements DirectUploadService {

    protected final AbstractStorageService storageService;
    private final OrphanFileGuard orphanFileGuard;
    private final OrphanCleanupProperties orphanCleanupProperties;

    protected AbstractDirectUploadService(AbstractStorageService storageService, @Nullable OrphanFileGuard orphanFileGuard, OrphanCleanupProperties orphanCleanupProperties) {
        this.storageService = storageService;
        this.orphanFileGuard = orphanFileGuard;
        this.orphanCleanupProperties = orphanCleanupProperties;
    }

    @Override
    public DirectUploadCredential issueCredential(String objectKey, String contentType, Duration duration) {
        return issueCredential(null, objectKey, contentType, duration);
    }

    @Override
    public DirectUploadCredential issueCredential(String bucket, String objectKey, String contentType, Duration duration) {
        String targetBucket = storageService.getEffectiveBucket(bucket);
        String targetKey = ObjectKeyUtil.normalizeObjectKey(objectKey);
        Duration effectiveDuration = duration != null ? duration : Duration.ofMinutes(15);

        String uploadUrl = doIssueCredential(targetBucket, targetKey, contentType, effectiveDuration);
        long expireAt = TimeUtil.toEpochSecond(LocalDateTime.now().plus(effectiveDuration));

        log.info("成功签发 MinIO 直传凭证, bucket={}, object={}, ttlSeconds={}", targetBucket, targetKey, effectiveDuration.getSeconds());

        DirectUploadCredential credential = DirectUploadCredential.builder()
                .bucket(targetBucket)
                .objectKey(targetKey)
                .uploadUrl(uploadUrl)
                .expireAt(expireAt)
                .build();

        markPendingIfAuto(bucket, credential);
        return credential;
    }

    private void markPendingIfAuto(String bucket, DirectUploadCredential credential) {
        if (orphanFileGuard == null || !orphanCleanupProperties.isEnabled() || !orphanCleanupProperties.isAutoMarkPending()
                || credential == null || !StringUtils.hasText(credential.getObjectKey())) {
            return;
        }
        orphanFileGuard.pending(bucket, credential.getObjectKey());
    }

    protected abstract String doIssueCredential(String bucket, String objectKey, String contentType, Duration duration);
}