package com.yzm.fireworks.storage.minio;

import com.yzm.fireworks.storage.api.DirectUploadService;
import com.yzm.fireworks.storage.api.StorageService;
import com.yzm.fireworks.storage.api.UploadCredential;
import com.yzm.fireworks.storage.api.UploadType;
import com.yzm.fireworks.storage.core.AbstractStorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import com.yzm.fireworks.storage.core.StorageUrlUtils;
import com.yzm.fireworks.storage.core.exception.StorageException;
import com.yzm.fireworks.storage.core.orphan.OrphanCleanupProperties;
import com.yzm.fireworks.storage.core.orphan.OrphanFileGuard;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;
import static com.yzm.fireworks.common.constants.StringPool.SLASH;


/**
 * MinIO 直传凭证实现。
 * <p>
 * 目录归一化逻辑统一复用 {@link AbstractStorageService#normalizeDir(String)}，
 * 与 {@code MinioStorageService} 保持一致的 objectKey 规则。
 * <p>
 * 签发凭证时，若孤儿清理能力已启用（存在 {@link OrphanFileGuard}）且配置
 * {@code fireworks.storage.orphan-cleanup.auto-mark-pending=true}（默认），会自动登记待确认记录，
 * 业务方无需手动调用 {@link OrphanFileGuard#pending}，避免漏写导致孤儿文件保护失效。
 */
@Slf4j
public class MinioDirectUploadService extends AbstractStorageService implements DirectUploadService {

    // S3/MinIO PostPolicy 表单直传协议规定的字段名，不是业务自定义命名。
    private static final String FORM_FIELD_KEY = "key";

    private final MinioClient client;
    private final MinioClient presignClient;
    private final StorageProperties properties;
    private final StorageProperties.Minio minioProperties;
    private final StorageService storageService;
    /** 可为 null：孤儿清理依赖 Redis，未引入 Redis 时该 Bean 不存在，跳过自动登记。 */
    @Nullable
    private final OrphanFileGuard orphanFileGuard;
    private final OrphanCleanupProperties orphanCleanupProperties;

    public MinioDirectUploadService(MinioClient client, MinioClient presignClient, StorageProperties properties,
                                    StorageService storageService,
                                    @Nullable OrphanFileGuard orphanFileGuard,
                                    OrphanCleanupProperties orphanCleanupProperties) {
        this.client = client;
        this.presignClient = presignClient;
        this.properties = properties;
        this.minioProperties = properties.getMinio();
        this.storageService = storageService;
        this.orphanFileGuard = orphanFileGuard;
        this.orphanCleanupProperties = orphanCleanupProperties;
    }

    /**
     * 凭证签发后，根据配置决定是否自动登记孤儿文件待确认记录。
     */
    private void markPendingIfAuto(String bucket, UploadCredential credential) {
        if (orphanFileGuard == null || !orphanCleanupProperties.isEnabled() || !orphanCleanupProperties.isAutoMarkPending()
                || credential == null || !StringUtils.hasText(credential.getObjectName())) {
            return;
        }
        orphanFileGuard.pending(bucket, credential.getObjectName());
    }


    @Override
    public UploadCredential getUploadCredential(String bucket, String objectName, Duration duration) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
        Assert.notNull(duration, "duration 不能为空");
        try {
            // SigV4 签名将 Host 纳入签名范围，必须用 presignClient（公网 endpoint 构建）生成预签名 URL。
            String url = presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry((int) duration.toSeconds(), TimeUnit.SECONDS)
                            .build());
            log.info("获取 MinIO 直传凭证成功, bucket={}, object={}", bucket, objectName);
            UploadCredential credential = UploadCredential.builder()
                    .type(UploadType.PRESIGNED_PUT)
                    .url(url)
                    .objectName(objectName)
                    // displayUrl 仅用于上传成功后的立即回显，业务落库用 objectName，故用临时签名地址更通用
                    // （对私有 Bucket 也能访问）。时效独立于上传凭证，使用 displayUrlTtl（默认 24h），
                    // 以覆盖"用户上传后继续填写其他表单信息"的整段过程。
                    .displayUrl(storageService.getPresignedUrl(bucket, objectName, properties.getDisplayUrlTtl()))
                    .expiration(System.currentTimeMillis() + duration.toMillis())
                    .build();
            markPendingIfAuto(bucket, credential);
            return credential;
        } catch (Exception e) {
            throw new StorageException("获取 MinIO 直传凭证失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public UploadCredential getUploadCredentialByPostPolicy(String bucket, String objectName, Duration duration) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
        Assert.notNull(duration, "duration 不能为空");
        ZonedDateTime expiration = ZonedDateTime.now(ZoneId.systemDefault()).plus(duration);
        try {
            PostPolicy postPolicy = new PostPolicy(bucket, expiration);
            // 策略仅要求对象路径以 objectName 所在目录开头（objectName 本身一定满足该前缀）。
            postPolicy.addStartsWithCondition(FORM_FIELD_KEY, extractDirPrefix(objectName));
            postPolicy.addContentLengthRangeCondition(0, properties.getDirectUploadMaxSize());

            Map<String, String> formData = client.getPresignedPostFormData(postPolicy);
            // 后端生成的完整 objectName 精确写入 key：OSS/S3 收到请求后直接以该值作为存储路径，
            // 忽略前端文件原名，彻底规避特殊字符/中文乱码/超长文件名与同名覆盖问题。
            formData.put(FORM_FIELD_KEY, objectName);

            // PostPolicy 签名只覆盖 policy 文档本身，不覆盖上传目标 URL，
            // 因此直接用 publicEndpoint 替换 host 是安全的，签名照样有效。
            String baseEndpoint = StringUtils.hasText(properties.getPublicEndpoint())
                    ? properties.getPublicEndpoint()
                    : StorageUrlUtils.buildEndpointUrl(minioProperties.getEndpoint(), minioProperties.isSecure());
            String url = (baseEndpoint.endsWith(SLASH) ? baseEndpoint : baseEndpoint + SLASH) + bucket;
            // objectName 与 displayUrl 在签发时即可 100% 确定，精确返回，前端无需任何替换。
            log.info("获取 MinIO 表单直传凭证成功, bucket={}, object={}", bucket, objectName);
            UploadCredential credential = UploadCredential.builder()
                    .type(UploadType.POST_POLICY)
                    .url(url)
                    .formData(formData)
                    .objectName(objectName)
                    .displayUrl(storageService.getPresignedUrl(bucket, objectName, properties.getDisplayUrlTtl()))
                    .expiration(expiration.toInstant().toEpochMilli())
                    .build();
            markPendingIfAuto(bucket, credential);
            return credential;
        } catch (Exception e) {
            throw new StorageException("获取 MinIO 表单直传凭证失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

}