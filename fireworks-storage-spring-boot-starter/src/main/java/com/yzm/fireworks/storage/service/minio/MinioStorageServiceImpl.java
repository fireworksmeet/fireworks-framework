package com.yzm.fireworks.storage.service.minio;

import com.yzm.fireworks.storage.service.StorageService;
import com.yzm.fireworks.storage.model.dto.StorageFile;
import com.yzm.fireworks.storage.service.AbstractStorageService;
import com.yzm.fireworks.storage.config.properties.StorageProperties;
import com.yzm.fireworks.storage.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.common.constants.StringPool.*;

@Slf4j
public class MinioStorageServiceImpl extends AbstractStorageService implements StorageService {
    /**
     * MinIO/S3 协议要求分片大小（除最后一片外）不得小于 5MiB
     */
    private static final long MINIO_MIN_PART_SIZE = 5L * 1024 * 1024;

    /**
     * 内网通信客户端：用于服务端内部文件上传、删除、下载等高性能网络 IO
     */
    private final MinioClient minioClient;

    /**
     * 外网预签名客户端：用于生成外网用户/前端可直接访问的 S3 临时签名地址（解决 SigV4 Host 校验问题）
     */
    private final MinioClient presignClient;

    private final StorageProperties.Minio minioProperties;

    public MinioStorageServiceImpl(
            StorageProperties properties,
            MinioClient minioClient,
            MinioClient presignClient) {
        super(properties);
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient 不能为 null");
        this.presignClient = Objects.requireNonNull(presignClient, "presignClient 不能为 null");
        this.minioProperties = properties.getMinio();
    }

    @Override
    protected StorageFile doUpload(String bucket, String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception {
        ensureBucketExists(bucket);

        PutObjectArgs.Builder builder = PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey);

        // 根据 contentLength 是否已知动态配置 MinIO Stream 规则
        if (contentLength >= 0) {
            // 1. 已知文件大小：传入真实长度，partSize 传 -1 让 SDK 自动优化（小文件单次直传，大文件自动算分片）
            builder.stream(inputStream, contentLength, -1);
        } else {
            // 2. 未知文件大小：传 -1，必须指定 partSize (至少 5MB) 供 MinIO 分块缓冲
            builder.stream(inputStream, -1, MINIO_MIN_PART_SIZE);
        }

        if (StringUtils.hasText(contentType)) {
            builder.contentType(contentType);
        }

        ObjectWriteResponse response = minioClient.putObject(builder.build());
        log.info("MinIO 文件流式上传成功, bucket={}, object={}, etag={}", bucket, objectKey, response.etag());

        String fileUrl = getPublicUrl(bucket, objectKey);
        return StorageFile.builder()
                .bucket(bucket)
                .objectKey(objectKey)
                .url(fileUrl)
                .etag(response.etag())
                .contentType(contentType)
                .build();
    }

    @Override
    protected InputStream doGetInputStream(String bucket, String objectKey) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        );
    }

    @Override
    protected Optional<StorageFile> doGetMetadata(String bucket, String objectKey) throws Exception {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            String fileUrl = getPublicUrl(bucket, objectKey);
            StorageFile file = StorageFile.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .url(fileUrl)
                    .size(stat.size())
                    .contentType(stat.contentType())
                    .etag(stat.etag())
                    .lastModified(stat.lastModified() != null ? stat.lastModified().toLocalDateTime() : null)
                    .build();
            return Optional.of(file);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    protected boolean doExists(String bucket, String objectKey) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void doDeleteFile(String bucket, String objectKey) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        );
        log.info("MinIO 文件删除成功, bucket={}, object={}", bucket, objectKey);
    }

    @Override
    protected void doDeleteFiles(String bucket, List<String> objectKeys) throws Exception {
        List<DeleteObject> deleteObjects = new LinkedList<>();
        for (String key : objectKeys) {
            deleteObjects.add(new DeleteObject(key));
        }
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(bucket)
                        .objects(deleteObjects)
                        .build()
        );
        for (Result<DeleteError> result : results) {
            DeleteError error = result.get();
            log.warn("MinIO 批量删除部分失败, bucket={}, object={}, code={}", bucket, error.objectName(), error.code());
        }
    }

    @Override
    protected String doGetPublicUrl(String bucket, String objectKey) {
        String endpoint = minioProperties.getEndpoint();
        if (!endpoint.startsWith(HTTP_HOST_PREFIX) && !endpoint.startsWith(HTTPS_HOST_PREFIX)) {
            endpoint = (minioProperties.isSecure() ? HTTPS_HOST_PREFIX : HTTP_HOST_PREFIX) + endpoint;
        }
        if (endpoint.endsWith(SLASH)) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + SLASH + bucket + SLASH + objectKey;
    }

    @Override
    protected String doGetPresignedUrl(String bucket, String objectKey, Duration duration) {
        try {
            // 必须使用外网 presignClient 生成，避免内网 Host 导致的 SigV4 403 校验失败
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) duration.toSeconds(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("获取 MinIO 预签名 URL 失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    /**
     * 自动创建 Bucket 辅助方法
     */
    protected void ensureBucketExists(String bucket) {
        Assert.hasText(bucket, "bucket 不能为空");

        if (!minioProperties.isAutoCreateBucket()) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket 不存在，已根据 auto-create-bucket 配置自动创建, bucket={}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("自动创建 Bucket 失败: bucket=" + bucket, e);
        }
    }
}