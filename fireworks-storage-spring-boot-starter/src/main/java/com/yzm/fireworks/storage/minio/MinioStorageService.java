package com.yzm.fireworks.storage.minio;

import com.yzm.fireworks.storage.api.ImageOptions;
import com.yzm.fireworks.storage.api.StorageFile;
import com.yzm.fireworks.storage.api.StorageService;
import com.yzm.fireworks.storage.core.AbstractStorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import com.yzm.fireworks.storage.core.StorageUrlUtils;
import com.yzm.fireworks.storage.core.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.common.constants.StringPool.SLASH;

@Slf4j
public class MinioStorageService extends AbstractStorageService implements StorageService {

    private final MinioClient client;
    private final MinioClient presignClient;
    private final StorageProperties properties;
    private final StorageProperties.Minio minioProperties;

    public MinioStorageService(StorageProperties properties, MinioClient client, MinioClient presignClient) {
        Assert.notNull(properties, "StorageProperties 不能为空");
        Assert.notNull(client, "MinioClient 不能为空");
        Assert.notNull(presignClient, "presignMinioClient 不能为空");
        this.properties = properties;
        this.minioProperties = properties.getMinio();
        this.client = client;
        this.presignClient = presignClient;
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, String file) {
        Assert.hasText(file, "文件路径不能为空");
        return uploadFile(bucket, dir, new File(file));
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, File file) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.notNull(file, "file 不能为空");
        Assert.isTrue(file.isFile(), () -> "文件不存在或不是合法文件: " + file.getAbsolutePath());

        String objectName = buildObjectName(dir, file.getName());
        ensureBucketExists(bucket);
        try {
            ObjectWriteResponse response = client.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .filename(file.getAbsolutePath())
                            .headers(buildContentDispositionHeaders(file.getName()))
                            .build());
            log.info("文件上传成功, bucket={}, object={}, size={}, etag={}", bucket, objectName, file.length(), response.etag());
            return buildStorageFile(bucket, objectName, file.getName(), getFileUrl(bucket, objectName),
                    file.length(), null, response.etag());
        } catch (Exception e) {
            throw new StorageException("文件上传失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, String fileName, InputStream inputStream, long contentLength) {
        return uploadFile(bucket, dir, fileName, inputStream, contentLength, null);
    }

    @Override
    public StorageFile uploadFile(String bucket, String dir, String fileName, InputStream inputStream, long contentLength, String contentType) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(fileName, "fileName 不能为空");
        Assert.notNull(inputStream, "inputStream 不能为空");

        String objectName = buildObjectName(dir, fileName);
        ensureBucketExists(bucket);
        // 显式关闭传入的 InputStream：之前的实现依赖调用方自行关闭，容易造成连接/文件句柄泄漏。
        try (InputStream in = inputStream) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    // 显式传入统一配置的 partSize，行为与 Aliyun 实现的分片大小语义保持一致，
                    // 而不是依赖 MinIO SDK 内部的默认分片策略（-1 表示由 SDK 自行决定）。
                    .stream(in, contentLength, resolvePartSize())
                    .headers(buildContentDispositionHeaders(fileName));
            if (StringUtils.hasText(contentType)) {
                builder.contentType(contentType);
            }
            ObjectWriteResponse response = client.putObject(builder.build());
            log.info("文件流式上传成功, bucket={}, object={}, size={}, etag={}", bucket, objectName, contentLength, response.etag());
            return buildStorageFile(bucket, objectName, fileName, getFileUrl(bucket, objectName),
                    contentLength, contentType, response.etag());
        } catch (IOException e) {
            throw new StorageException("关闭文件输入流失败, object=" + objectName, e);
        } catch (Exception e) {
            throw new StorageException("文件流式上传失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public void deleteFile(String bucket, String objectName) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            log.info("文件删除成功, bucket={}, object={}", bucket, objectName);
        } catch (Exception e) {
            throw new StorageException("删除文件失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public void deleteFile(String bucket, List<String> objectNames) {
        Assert.hasText(bucket, "bucket 不能为空");
        if (CollectionUtils.isEmpty(objectNames)) {
            log.warn("deleteFile 批量删除时 objectNames 为空，跳过操作, bucket={}", bucket);
            return;
        }
        try {
            List<DeleteObject> deleteObjects = new LinkedList<>();
            for (String objectName : objectNames) {
                deleteObjects.add(new DeleteObject(objectName));
            }
            Iterable<Result<DeleteError>> results =
                    client.removeObjects(
                            RemoveObjectsArgs.builder()
                                    .bucket(bucket)
                                    .objects(deleteObjects)
                                    .build());
            boolean hasError = false;
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                log.warn("批量删除部分失败, bucket={}, object={}, code={}",
                        bucket, error.objectName(), error.code());
                hasError = true;
            }
            if (hasError) {
                log.warn("文件批量删除部分失败, bucket={}, count={}", bucket, objectNames.size());
            } else {
                log.info("文件批量删除成功, bucket={}, count={}", bucket, objectNames.size());
            }
        } catch (Exception e) {
            throw new StorageException("批量删除文件失败: bucket=" + bucket, e);
        }
    }

    @Override
    public String getFileUrl(String bucket, String objectName) {
        String publicEndpoint = properties.getPublicEndpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            return publicEndpoint + SLASH + bucket + SLASH + objectName;
        }
        String baseUrl = StorageUrlUtils.buildEndpointUrl(minioProperties.getEndpoint(), minioProperties.isSecure());
        return baseUrl + SLASH + bucket + SLASH + objectName;
    }

    @Override
    public String getPresignedUrl(String bucket, String objectName, Duration duration) {
        try {
            // SigV4 签名将 Host 纳入签名范围，必须用 presignClient（公网 endpoint 构建）生成预签名 URL，
            // 不能用内网 client 签名后再替换 host，否则 MinIO 验签时会因 Host 不匹配而返回 403。
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry((int) duration.toSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new StorageException("获取预签名URL失败: bucket=" + bucket + ", object=" + objectName, e);
        }
    }

    @Override
    public String getFileUrl(String bucket, String objectName, ImageOptions options) {
        if (options == null) {
            return getFileUrl(bucket, objectName);
        }
        options.validate();
        StorageProperties.Minio.Imgproxy imgproxy = minioProperties.getImgproxy();
        Assert.notNull(imgproxy, "未配置 fireworks.storage.minio.imgproxy，无法生成图片处理地址");
        Assert.hasText(imgproxy.getEndpoint(), "fireworks.storage.minio.imgproxy.endpoint 不能为空");
        // MinIO 自身没有图片处理能力，缩放/格式转换由部署在 MinIO 前面的 imgproxy 按需生成；
        // 这里只是构建一个 imgproxy 能识别的签名 URL，源图地址沿用 getFileUrl 的拼接规则，
        // 因此该地址必须是 imgproxy 能直接发起 HTTP 请求访问到的地址（公网可达或与 imgproxy 内网互通）。
        String sourceUrl = getFileUrl(bucket, objectName);
        return ImgproxyUrlBuilder.buildImageUrl(
                imgproxy.getEndpoint(), imgproxy.getPrefix(), imgproxy.getKey(), imgproxy.getSalt(), sourceUrl, options);
    }

    /**
     * 当 {@code fireworks.storage.minio.auto-create-bucket=true} 时，上传前检查 Bucket 是否存在，不存在则自动创建。
     * 该选项是 MinIO 专属配置（Aliyun OSS 不支持自动创建 Bucket，因此该字段不存在于 Aliyun 配置下）。
     */
    private void ensureBucketExists(String bucket) {
        if (!minioProperties.isAutoCreateBucket()) {
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket 不存在，已根据 auto-create-bucket 配置自动创建, bucket={}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("自动创建 Bucket 失败: bucket=" + bucket, e);
        }
    }

    /**
     * MinIO/S3 协议要求分片大小（除最后一片外）不得小于 5MiB，比 OSS 的 100KB 限制更严格。
     * 统一配置的 {@code fireworks.storage.part-size} 是按 OSS 的限制设计的，这里做一次安全下限保护，
     * 避免用户配置了较小的 partSize（OSS 可以接受）导致 MinIO 侧运行时报错。
     */
    private static final long MINIO_MIN_PART_SIZE = 5L * 1024 * 1024;

    private long resolvePartSize() {
        long partSize = properties.getPartSize();
        if (partSize < MINIO_MIN_PART_SIZE) {
            log.warn("配置的 part-size={} 小于 MinIO/S3 协议要求的最小分片大小 5MiB，已自动调整为 5MiB", partSize);
            return MINIO_MIN_PART_SIZE;
        }
        return partSize;
    }

    private Map<String, String> buildContentDispositionHeaders(String fileName) {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        String disposition = String.format("attachment;filename=%s;filename*=UTF-8''%s",
                encodedFileName, encodedFileName);
        return Map.of("Content-Disposition", disposition);
    }
}