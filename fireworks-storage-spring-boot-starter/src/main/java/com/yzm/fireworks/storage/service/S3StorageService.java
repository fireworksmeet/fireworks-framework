package com.yzm.fireworks.storage.service;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.storage.DirectUploadCredential;
import com.yzm.fireworks.storage.ObjectKeyUtil;
import com.yzm.fireworks.storage.StorageFile;
import com.yzm.fireworks.storage.StorageProperties;
import com.yzm.fireworks.storage.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.yzm.fireworks.storage.ObjectKeyUtil.normalizeObjectKey;

/**
 * 基于 AWS S3 SDK v2 的存储服务，统一对接所有 S3 协议兼容存储
 * （MinIO / SeaweedFS / RustFS / Ceph RGW / AWS S3 / 阿里云 OSS 兼容模式）。
 * <p>
 * 方法命名对齐 AWS S3 SDK v2 与阿里云 OSS SDK 事实标准，业务方可直接参照官方文档使用。
 * <p>
 * 设计原则：单一具体类，不做接口抽象。Mock 测试请使用 mockito-inline 即可。
 * <p>
 * <b>能力边界</b>：不提供分片上传（Multipart Upload），单个对象受 S3 单次 PUT 的 5GB 上限约束；
 * 不提供 PostPolicy 表单直传，原因见 {@link #generatePresignedPutUrl(String, String, String, Duration)}。
 */
@Slf4j
@RequiredArgsConstructor
public final class S3StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    /**
     * 本地缓存已确认存在的 Bucket，避免高并发上传场景下每个 putObject 都先发一次 headBucket 网络请求
     * （既会拖慢 RTT 又可能触发 S3/MinIO 的频控）
     */
    private final Set<String> KNOWN_EXISTING_BUCKETS = ConcurrentHashMap.newKeySet();

    // 阈值定义：10MB 以内走内存，超过 10MB 走临时文件，防止 OOM
    private static final int MEMORY_THRESHOLD = 10 * 1024 * 1024;

    // ═══════ 上传 (putObject) ═════════════════════════════════════════════════════════

    /**
     * 上传本地文件（使用配置的默认 Bucket）
     */
    public StorageFile putObject(String objectKey, File file, String contentType) {
        return putObject(null, objectKey, file, contentType);
    }

    /**
     * 上传本地文件（推荐：直接基于 File 上传，由 AWS SDK 自动管理文件句柄、零拷贝、自动计算 Checksum/Content-Length）
     * <p>
     * 注意：不要在外面 try-with-resources 包装 FileInputStream 再传入，会与 AWS SDK 内部自动关闭产生双重关闭风险。
     */
    public StorageFile putObject(String bucket, String objectKey, File file, String contentType) {
        Assert.notNull(file, "待上传文件不能为空");
        Assert.isTrue(file.exists() && file.isFile(), "待上传文件不存在: " + file.getAbsolutePath());

        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        try {
            ensureBucketExists(targetBucket);
            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(targetKey);
            if (StringUtils.hasText(contentType)) {
                reqBuilder.contentType(contentType);
            }

            PutObjectResponse resp = s3.putObject(reqBuilder.build(), RequestBody.fromFile(file));

            log.debug("S3 putObject 成功, bucket={}, key={}, etag={}", targetBucket, targetKey, resp.eTag());
            return StorageFile.builder()
                    .bucket(targetBucket)
                    .objectKey(targetKey)
                    .url(getUrl(targetBucket, targetKey))
                    .etag(resp.eTag())
                    .contentType(contentType)
                    .size(file.length())
                    .build();
        } catch (Exception e) {
            throw new StorageException(String.format("putObject 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    /**
     * 上传字节数组（适合小文件 < 10MB）
     */
    public StorageFile putObject(String bucket, String objectKey, byte[] bytes, String contentType) {
        Assert.notNull(bytes, "上传字节数组不能为空");
        return putObject(bucket, objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    /**
     * 上传输入流（contentLength 未知）。
     * <p>
     * 注意：S3 协议要求确定的 Content-Length，此重载会将流完整缓冲到内存，
     * 大文件请改用 {@link #putObject(String, String, InputStream, long, String)} 显式传入长度。
     */
    public StorageFile putObject(String bucket, String objectKey, InputStream inputStream, String contentType) {
        return putObject(bucket, objectKey, inputStream, -1, contentType);
    }

    /**
     * 上传输入流（推荐：显式指定 contentLength）
     */
    public StorageFile putObject(String bucket, String objectKey, InputStream inputStream,
                                 long contentLength, String contentType) {
        Assert.notNull(inputStream, "待上传输入流不能为空");

        // 1. 如果没有指定长度 (contentLength < 0)，在 Starter 内部自动转换为“已知长度”的载体
        if (contentLength < 0) {
            return putObjectWithUnknownLength(bucket, objectKey, inputStream, contentType);
        }

        // 2. 如果已经已知长度，走原有的高效逻辑
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        try {
            ensureBucketExists(targetBucket);
            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(targetKey);
            if (StringUtils.hasText(contentType)) {
                reqBuilder.contentType(contentType);
            }
            reqBuilder.contentLength(contentLength);

            PutObjectResponse resp = s3.putObject(reqBuilder.build(), RequestBody.fromInputStream(inputStream, contentLength));

            log.debug("S3 putObject 成功, bucket={}, key={}, etag={}", targetBucket, targetKey, resp.eTag());
            return StorageFile.builder()
                    .bucket(targetBucket)
                    .objectKey(targetKey)
                    .url(getUrl(targetBucket, targetKey))
                    .etag(resp.eTag())
                    .contentType(contentType)
                    .size(contentLength)
                    .build();
        } catch (Exception e) {
            throw new StorageException(String.format("putObject 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    // ═══════ 下载与读取 (getObject / headObject) ═════════════════════════════════════

    /**
     * 函数式读取文件流（自动管理流生命周期，防止连接泄漏）
     */
    public void getObject(String bucket, String objectKey, Consumer<InputStream> streamConsumer) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        Assert.notNull(streamConsumer, "streamConsumer 不能为空");
        try (ResponseInputStream<GetObjectResponse> is = s3.getObject(req -> req
                .bucket(targetBucket).key(targetKey))) {
            Assert.notNull(is, "获取到的文件流为空: " + targetKey);
            streamConsumer.accept(is);
        } catch (Exception e) {
            throw new StorageException(String.format("getObject 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    /**
     * 获取文件字节内容（适合小文件）
     */
    public byte[] getObjectAsBytes(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        try {
            return s3.getObjectAsBytes(req -> req.bucket(targetBucket).key(targetKey)).asByteArray();
        } catch (Exception e) {
            throw new StorageException(String.format("getObjectAsBytes 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    /**
     * 获取文件元数据（对齐 AWS SDK headObject / 阿里云 SDK getObjectMetadata）
     */
    public Optional<StorageFile> headObject(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        try {
            HeadObjectResponse resp = s3.headObject(req -> req.bucket(targetBucket).key(targetKey));
            return Optional.of(StorageFile.builder()
                    .bucket(targetBucket)
                    .objectKey(targetKey)
                    .url(getUrl(targetBucket, targetKey))
                    .size(resp.contentLength())
                    .contentType(resp.contentType())
                    .etag(resp.eTag())
                    .lastModified(resp.lastModified())
                    .userMetadata(toUserMetadata(resp.metadata()))
                    .build());
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // 兼容阿里云 OSS S3 兼容模式：缺失对象时可能抛出 404 S3Exception（而非标准 NoSuchKeyException）
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageException(String.format("headObject 失败: %s/%s", targetBucket, targetKey), e);
        } catch (Exception e) {
            throw new StorageException(String.format("headObject 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    /**
     * 检查文件是否存在（对齐阿里云 OSS SDK doesObjectExist）
     */
    public boolean doesObjectExist(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        try {
            s3.headObject(req -> req.bucket(targetBucket).key(targetKey));
            return true;
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            // 兼容阿里云 OSS S3 兼容模式：缺失对象或未授权读取元数据时返回 404/403
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                return false;
            }
            throw new StorageException(String.format("doesObjectExist 失败: %s/%s", targetBucket, targetKey), e);
        } catch (Exception e) {
            throw new StorageException(String.format("doesObjectExist 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    // ═══════ 删除 (deleteObject / deleteObjects) ══════════════════════════════════════

    /**
     * 删除单个文件（对齐 AWS SDK deleteObject）
     */
    public void deleteObject(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        try {
            s3.deleteObject(req -> req.bucket(targetBucket).key(targetKey));
            log.debug("S3 deleteObject 成功, bucket={}, key={}", targetBucket, targetKey);
        } catch (Exception e) {
            throw new StorageException(String.format("deleteObject 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    /**
     * 批量删除文件（对齐 AWS SDK deleteObjects）
     */
    public void deleteObjects(String bucket, List<String> objectKeys) {
        String targetBucket = resolveBucket(bucket);
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        List<String> normalizedKeys = objectKeys.stream()
                .map(ObjectKeyUtil::normalizeObjectKey)
                .toList();
        try {
            List<ObjectIdentifier> objects = normalizedKeys.stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
            s3.deleteObjects(req -> req.bucket(targetBucket).delete(d -> d.objects(objects)));
            log.debug("S3 deleteObjects 成功, bucket={}, count={}", targetBucket, normalizedKeys.size());
        } catch (Exception e) {
            throw new StorageException(String.format("deleteObjects 失败: %s, count=%d", targetBucket, normalizedKeys.size()), e);
        }
    }

    // ═══════ URL 生成 (getUrl / generatePresignedUrl) ════════════════════════════════

    /**
     * 公开访问 URL（CDN/自定义域名优先，否则由 S3 SDK 构造，对齐 AWS SDK getUrl）
     * <p>
     * URL 风格由 enableBucketInUrl 控制：
     * <ul>
     *   <li>true：{@code publicEndpoint/bucket/key}（适用于多 Bucket 共用 CDN）</li>
     *   <li>false：{@code publicEndpoint/key}（适用于 CDN 已绑定特定 Bucket）</li>
     * </ul>
     */
    public String getUrl(String bucket, String objectKey) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        String publicEndpoint = properties.getPublicEndpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            String prefix = trimSlash(publicEndpoint);
            if (properties.isEnableBucketInUrl()) {
                return prefix + StringPool.SLASH + targetBucket + StringPool.SLASH + targetKey;
            }
            return prefix + StringPool.SLASH + targetKey;
        }
        return s3.utilities().getUrl(req -> req.bucket(targetBucket).key(targetKey)).toString();
    }

    /**
     * 临时签名下载 URL（对齐阿里云 SDK generatePresignedUrl）
     */
    public String generatePresignedUrl(String bucket, String objectKey, Duration duration) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        Duration effectiveDuration = duration != null ? duration : Duration.ofMinutes(15);
        try {
            PresignedGetObjectRequest presigned = presigner.presignGetObject((GetObjectPresignRequest.Builder b) ->
                    b.getObjectRequest(g -> g.bucket(targetBucket).key(targetKey))
                            .signatureDuration(effectiveDuration));
            return presigned.url().toString();
        } catch (Exception e) {
            throw new StorageException(String.format("generatePresignedUrl 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    // ═══════ 客户端直传凭证签发 (generatePresignedPutUrl) ════════════════════════════

    /**
     * 签发 PUT 预签名直传凭证（使用配置的默认 Bucket，对齐阿里云 SDK generatePresignedUrl + AWS SDK presignPutObject）
     */
    public DirectUploadCredential generatePresignedPutUrl(String objectKey, String contentType, Duration duration) {
        return generatePresignedPutUrl(null, objectKey, contentType, duration);
    }

    /**
     * 签发 PUT 预签名直传凭证（指定 Bucket）
     * <p>
     * 前端拿到 uploadUrl 后，以 PUT 方式上传整个文件。
     * 后端生成 objectKey 时应使用 UUID / Snowflake 等保证全局唯一，避免特殊字符、中文乱码、超长文件名。
     * <p>
     * <b>关于 PostPolicy 表单直传</b>：AWS SDK Java v2 当前版本尚未提供 <code>presignPostRequest</code> API，
     * PostPolicy V4 签名需要 100+ 行手工实现，超出 starter 职责范围，故本 starter 不提供该能力。
     */
    public DirectUploadCredential generatePresignedPutUrl(String bucket, String objectKey, String contentType, Duration duration) {
        String targetBucket = resolveBucket(bucket);
        String targetKey = ObjectKeyUtil.normalizeObjectKey(objectKey);
        Duration effectiveDuration = duration != null ? duration : Duration.ofMinutes(15);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(targetBucket)
                .key(targetKey)
                .contentType(contentType)
                .build();

        try {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(req -> req
                    .signatureDuration(effectiveDuration)
                    .putObjectRequest(objectRequest));

            Map<String, String> headers = new HashMap<>();
            presigned.signedHeaders().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    headers.put(k, v.getFirst());
                }
            });
            // Content-Type 已参与 HMAC-SHA256 签名计算，前端 PUT 时必须严格带上同样的 Content-Type，否则会 403 SignatureDoesNotMatch
            if (StringUtils.hasText(contentType)) {
                headers.put("Content-Type", contentType);
            }

            long expiration = Instant.now().plus(effectiveDuration).getEpochSecond();
            log.info("签发 PUT 直传凭证, bucket={}, key={}, ttlSeconds={}",
                    targetBucket, targetKey, effectiveDuration.getSeconds());

            return DirectUploadCredential.builder()
                    .bucket(targetBucket)
                    .objectKey(targetKey)
                    .uploadUrl(presigned.url().toString())
                    .httpMethod(DirectUploadCredential.HttpMethod.PUT)
                    .headers(headers)
                    .expiration(expiration)
                    .build();
        } catch (Exception e) {
            throw new StorageException(String.format("generatePresignedPutUrl 失败: %s/%s", targetBucket, targetKey), e);
        }
    }

    // ═══════ 私有工具方法（替代原 delegate/abstract）═════════════════════════════════

    private String resolveBucket(String bucket) {
        if (StringUtils.hasText(bucket)) {
            return bucket.trim();
        }
        String defaultBucket = properties.getDefaultBucket();
        Assert.hasText(defaultBucket, "默认 Bucket 未配置，且当前请求未传入 bucket");
        return defaultBucket;
    }

    private void ensureBucketExists(String bucket) {
        if (!properties.isAutoCreateBucket() || KNOWN_EXISTING_BUCKETS.contains(bucket)) {
            return;
        }
        try {
            s3.headBucket(req -> req.bucket(bucket));
            KNOWN_EXISTING_BUCKETS.add(bucket);
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(req -> req.bucket(bucket));
                KNOWN_EXISTING_BUCKETS.add(bucket);
                log.info("Bucket 不存在，已根据 auto-create-bucket 配置自动创建, bucket={}", bucket);
            } catch (Exception ex) {
                throw new StorageException("自动创建 Bucket 失败: " + bucket, ex);
            }
        } catch (Exception e) {
            throw new StorageException("检查 Bucket 失败: " + bucket, e);
        }
    }

    /**
     * 内部私有方法：专门兜底处理 contentLength < 0 (未知长度) 的流
     */
    private StorageFile putObjectWithUnknownLength(String bucket, String objectKey, InputStream inputStream, String contentType) {
        File tempFile = null;
        try {
            // 先尝试读取最多 10MB 到内存中
            byte[] bytes = inputStream.readNBytes(MEMORY_THRESHOLD + 1);

            if (bytes.length <= MEMORY_THRESHOLD) {
                // 说明文件 <= 10MB，直接转为 ByteArrayInputStream 上传，零临时文件开销
                log.debug("未知长度流 <= 10MB，自动转为字节数组上传: {}", objectKey);
                return putObject(bucket, objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
            } else {
                // 说明文件 > 10MB，为了防止内存溢出，将已读取的和剩余的流写入临时磁盘文件
                log.warn("未知长度流 > 10MB，为防止 OOM，自动落磁盘临时文件后再上传: {}", objectKey);
                tempFile = File.createTempFile("s3_upload_", ".tmp");

                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    fos.write(bytes); // 先把前面读出来的 10MB 写入
                    inputStream.transferTo(fos); // 再把剩下的流续写进去
                }

                // 调用 File 重载的 putObject (前面建议过的 RequestBody.fromFile 高效方式)
                return putObject(bucket, objectKey, tempFile, contentType);
            }
        } catch (IOException e) {
            throw new StorageException(String.format("处理未知长度流失败: %s", objectKey), e);
        } finally {
            // 确保临时文件一定会被清理，防止磁盘被撑爆
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    private String trimSlash(String s) {
        return s.endsWith(StringPool.SLASH) ? s.substring(0, s.length() - 1) : s;
    }

    private Map<String, String> toUserMetadata(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(raw);
    }
}