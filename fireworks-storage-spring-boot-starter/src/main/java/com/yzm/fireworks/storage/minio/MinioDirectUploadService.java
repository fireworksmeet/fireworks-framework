package com.yzm.fireworks.storage.minio;

import com.yzm.fireworks.storage.api.DirectUploadService;
import com.yzm.fireworks.storage.api.UploadCredential;
import com.yzm.fireworks.storage.api.UploadType;
import com.yzm.fireworks.storage.core.AbstractStorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import com.yzm.fireworks.storage.core.StorageUrlUtils;
import com.yzm.fireworks.storage.core.exception.StorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.common.constants.StringPool.SLASH;


/**
 * MinIO 直传凭证实现。
 * <p>
 * 目录归一化逻辑统一复用 {@link AbstractStorageService#normalizeDir(String)}，
 * 与 {@code MinioStorageService} 保持一致的 objectKey 规则。
 */
@Slf4j
public class MinioDirectUploadService extends AbstractStorageService implements DirectUploadService {

    // S3/MinIO PostPolicy 表单直传协议规定的字段名，不是业务自定义命名。
    private static final String FORM_FIELD_KEY = "key";

    private final MinioClient client;
    private final MinioClient presignClient;
    private final StorageProperties properties;
    private final StorageProperties.Minio minioProperties;

    public MinioDirectUploadService(MinioClient client, MinioClient presignClient, StorageProperties properties) {
        this.client = client;
        this.presignClient = presignClient;
        this.properties = properties;
        this.minioProperties = properties.getMinio();
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
            return UploadCredential.builder()
                    .type(UploadType.PRESIGNED_PUT)
                    .url(url)
                    .expiration(System.currentTimeMillis() + duration.toMillis())
                    .build();
        } catch (Exception e) {
            throw new StorageException("获取 MinIO 直传凭证失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public UploadCredential getUploadCredentialByPostPolicy(String bucket, String dir, Duration duration) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.notNull(duration, "duration 不能为空");
        String normalizedDir = normalizeDir(dir);
        ZonedDateTime expiration = ZonedDateTime.now(ZoneId.systemDefault()).plus(duration);
        try {
            PostPolicy postPolicy = new PostPolicy(bucket, expiration);
            postPolicy.addStartsWithCondition(FORM_FIELD_KEY, normalizedDir);
            postPolicy.addContentLengthRangeCondition(0, properties.getDirectUploadMaxSize());

            Map<String, String> formData = client.getPresignedPostFormData(postPolicy);
            // 与阿里云 OSS 不同：S3/MinIO 的表单直传不支持 ${filename} 占位符自动替换文件名，
            // 这里预填的 key 仅为目录前缀，前端提交表单前必须在该前缀基础上拼接真实文件名
            // （只要仍以该前缀开头即可满足 startsWith 策略条件）。
            formData.put(FORM_FIELD_KEY, normalizedDir);

            // PostPolicy 签名只覆盖 policy 文档本身，不覆盖上传目标 URL，
            // 因此直接用 publicEndpoint 替换 host 是安全的，签名照样有效。
            String baseEndpoint = StringUtils.hasText(properties.getPublicEndpoint())
                    ? properties.getPublicEndpoint()
                    : StorageUrlUtils.buildEndpointUrl(minioProperties.getEndpoint(), minioProperties.isSecure());
            String url = (baseEndpoint.endsWith(SLASH) ? baseEndpoint : baseEndpoint + SLASH) + bucket;
            log.info("获取 MinIO 表单直传凭证成功, bucket={}, dir={}", bucket, normalizedDir);
            return UploadCredential.builder()
                    .type(UploadType.POST_POLICY)
                    .url(url)
                    .formData(formData)
                    .expiration(expiration.toInstant().toEpochMilli())
                    .build();
        } catch (Exception e) {
            throw new StorageException("获取 MinIO 表单直传凭证失败: bucket=" + bucket, e);
        }
    }
}