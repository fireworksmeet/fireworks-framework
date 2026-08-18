package com.yzm.fireworks.storage.service.minio;

import com.yzm.fireworks.storage.exception.StorageException;
import com.yzm.fireworks.storage.orphan.OrphanCleanupProperties;
import com.yzm.fireworks.storage.orphan.OrphanFileGuard;
import com.yzm.fireworks.storage.service.AbstractDirectUploadService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


/**
 * MinIO 直传凭证实现。
 * <p>
 * 签发凭证时，若孤儿清理能力已启用（存在 {@link OrphanFileGuard}）且配置
 * {@code fireworks.storage.orphan-cleanup.auto-mark-pending=true}（默认），会自动登记待确认记录，
 * 业务方无需手动调用 {@link OrphanFileGuard#pending}，避免漏写导致孤儿文件保护失效。
 */
@Slf4j
public class MinioDirectUploadServiceImpl extends AbstractDirectUploadService {

    private final MinioClient presignClient;
    private final MinioStorageServiceImpl storageService;


    public MinioDirectUploadServiceImpl(
            MinioClient presignClient,
            MinioStorageServiceImpl storageService,
            @Nullable OrphanFileGuard orphanFileGuard,
            OrphanCleanupProperties orphanCleanupProperties) {
        super(storageService, orphanFileGuard, orphanCleanupProperties);
        Assert.notNull(presignClient, "presignClient 不能为 null");
        this.presignClient = presignClient;
        this.storageService = storageService;
    }

    @Override
    protected String doIssueCredential(String bucket, String objectKey, String contentType, Duration duration) {
        storageService.ensureBucketExists(bucket);

        try {
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) duration.getSeconds(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("签发 MinIO 直传凭证失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }
}