package com.yzm.fireworks.storage.service.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.yzm.fireworks.storage.model.dto.StorageFile;
import com.yzm.fireworks.storage.service.AbstractStorageService;
import com.yzm.fireworks.storage.config.properties.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.yzm.fireworks.common.constants.StringPool.*;

/**
 * 阿里云 OSS 存储实现。
 * <p>
 * 分片上传完全交给 OSS SDK 自身的能力
 * <ul>
 *     <li>File 上传：使用 {@code ossClient.uploadFile()}（断点续传上传），由 SDK 自行判断是否需要分片、
 *     自行管理并发线程，并提供基于本地 checkpoint 文件的断点续传能力。</li>
 *     <li>Stream 上传：使用最基础的 {@code putObject}（简单上传），OSS SDK 没有提供针对任意
 *     {@link InputStream} 的自动分片能力（断点续传依赖可重复读取的本地文件，流式数据天然不具备这个条件），
 *     因此流式上传被限制在 OSS 简单上传的单次大小上限（5GB）以内，超出该大小请先落盘后改用 File 上传。</li>
 * </ul>
 */
@Slf4j
public class AliyunStorageServiceImpl extends AbstractStorageService {

    private final OSS ossClient;
    private final StorageProperties.Aliyun aliyunProperties;

    public AliyunStorageServiceImpl(StorageProperties properties, OSS ossClient) {
        super(properties);
        this.ossClient = Objects.requireNonNull(ossClient, "ossClient 不能为 null");
        this.aliyunProperties = properties.getAliyun();
    }

    @Override
    protected StorageFile doUpload(String bucket, String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception {
        ObjectMetadata metadata = new ObjectMetadata();
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType);
        }

        // 当 contentLength >= 0 时，显式告知 OSS 文件大小
        if (contentLength >= 0) {
            metadata.setContentLength(contentLength);
        }

        PutObjectResult result = ossClient.putObject(bucket, objectKey, inputStream, metadata);
        log.info("阿里云 OSS 文件上传成功, bucket={}, object={}, etag={}", bucket, objectKey, result.getETag());

        String fileUrl = getPublicUrl(bucket, objectKey);
        return StorageFile.builder()
                .bucket(bucket)
                .objectKey(objectKey)
                .url(fileUrl)
                .etag(result.getETag())
                .contentType(contentType)
                .build();
    }

    @Override
    protected InputStream doGetInputStream(String bucket, String objectKey) throws Exception {
        OSSObject ossObject = ossClient.getObject(bucket, objectKey);
        return ossObject.getObjectContent();
    }

    @Override
    protected Optional<StorageFile> doGetMetadata(String bucket, String objectKey) throws Exception {
        try {
            ObjectMetadata meta = ossClient.getObjectMetadata(bucket, objectKey);
            String fileUrl = getPublicUrl(bucket, objectKey);

            LocalDateTime lastModifiedTime = meta.getLastModified() != null
                    ? meta.getLastModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : null;

            StorageFile file = StorageFile.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .url(fileUrl)
                    .size(meta.getContentLength())
                    .contentType(meta.getContentType())
                    .etag(meta.getETag())
                    .lastModified(lastModifiedTime)
                    .build();
            return Optional.of(file);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    protected boolean doExists(String bucket, String objectKey) throws Exception {
        return ossClient.doesObjectExist(bucket, objectKey);
    }

    @Override
    protected void doDeleteFile(String bucket, String objectKey) throws Exception {
        ossClient.deleteObject(bucket, objectKey);
        log.info("阿里云 OSS 文件删除成功, bucket={}, object={}", bucket, objectKey);
    }

    @Override
    protected void doDeleteFiles(String bucket, List<String> objectKeys) throws Exception {
        DeleteObjectsRequest request = new DeleteObjectsRequest(bucket).withKeys(objectKeys);
        DeleteObjectsResult result = ossClient.deleteObjects(request);
        log.info("阿里云 OSS 批量删除文件成功, bucket={}, count={}", bucket, result.getDeletedObjects().size());
    }

    @Override
    protected String doGetPublicUrl(String bucket, String objectKey) {
        // 默认按阿里云标准虚拟托管风格（Virtual-Hosted Style）拼接：https://bucket.endpoint/objectKey
        String endpoint = aliyunProperties.getEndpoint();
        String cleanEndpoint = endpoint.replace(HTTPS_HOST_PREFIX, EMPTY).replace(HTTP_HOST_PREFIX, EMPTY);
        if (cleanEndpoint.endsWith(SLASH)) {
            cleanEndpoint = cleanEndpoint.substring(0, cleanEndpoint.length() - 1);
        }

        return String.format("https://%s.%s/%s", bucket, cleanEndpoint, objectKey);
    }

    @Override
    protected String doGetPresignedUrl(String bucket, String objectKey, Duration duration) {
        Date expiration = Date.from(Instant.now().plus(duration));
        URL url = ossClient.generatePresignedUrl(bucket, objectKey, expiration, HttpMethod.GET);
        return url.toString();
    }
}