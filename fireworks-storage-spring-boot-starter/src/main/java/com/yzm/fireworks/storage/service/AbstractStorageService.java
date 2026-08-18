package com.yzm.fireworks.storage.service;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.common.util.HashUtil;
import com.yzm.fireworks.storage.config.properties.StorageProperties;
import com.yzm.fireworks.storage.exception.StorageException;
import com.yzm.fireworks.storage.model.dto.StorageFile;
import com.yzm.fireworks.storage.model.util.ObjectKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.yzm.fireworks.common.constants.StringPool.SLASH;
import static com.yzm.fireworks.storage.model.util.ObjectKeyUtil.normalizeObjectKey;

/**
 * 存储服务抽象基类：收口公共控制流、路径规范化、默认桶兜底与流安全管理
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractStorageService implements StorageService {

    protected final StorageProperties properties;

    /**
     * 获取有效的桶名称（为空时自动退回配置的默认桶）
     */
    protected String getEffectiveBucket(String bucket) {
        if (StringUtils.hasText(bucket)) {
            return bucket.trim();
        }
        String defaultBucket = properties.getDefaultBucket();
        Assert.hasText(defaultBucket, "默认 Bucket 未配置，且当前请求未传入 bucket");
        return defaultBucket;
    }

    /**
     * 统一捕获与转换云厂商 Native 异常为框架通用的 StorageException
     */
    protected <T> T executeWithExceptionTranslation(String action, String bucket, String objectKey, StorageSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("执行存储操作 [{}] 失败, bucket: {}, key: {}, msg: {}", action, bucket, objectKey, e.getMessage(), e);
            throw new StorageException(String.format("存储操作 [%s] 失败: %s", action, e.getMessage()), e);
        }
    }

    @FunctionalInterface
    protected interface StorageSupplier<T> {
        T get() throws Exception;
    }

    // ─── 2. 核心接口统一模板实现 ─────────────────────────────────────

    @Override
    public StorageFile upload(String objectKey, File file, String contentType) throws IOException {
        return upload(null, objectKey, file, contentType);
    }

    @Override
    public StorageFile upload(String bucket, String objectKey, File file, String contentType) throws IOException {
        Assert.notNull(file, "待上传文件不能为空");
        Assert.isTrue(file.exists() && file.isFile(), "待上传文件不存在或不是标注文件: " + file.getAbsolutePath());
        try (InputStream is = new FileInputStream(file)) {
            return upload(bucket, objectKey, is, file.length(), contentType);
        }
    }

    @Override
    public StorageFile upload(String bucket, String objectKey, byte[] bytes, String contentType) {
        Assert.notNull(bytes, "上传字节数组不能为空");
        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        return upload(bucket, objectKey, is, bytes.length, contentType);
    }

    @Override
    public StorageFile upload(String bucket, String objectKey, InputStream inputStream, String contentType) {
        return upload(bucket, objectKey, inputStream, -1, contentType);
    }

    @Override
    public StorageFile upload(String bucket, String objectKey, InputStream inputStream, long contentLength, String contentType) {
        Assert.notNull(inputStream, "上传输入流不能为空");
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);

        return executeWithExceptionTranslation("文件上传", targetBucket, targetKey, () ->
                doUpload(targetBucket, targetKey, inputStream, contentLength, contentType)
        );
    }

    @Override
    public void readStream(String bucket, String objectKey, Consumer<InputStream> streamConsumer) {
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        Assert.notNull(streamConsumer, "streamConsumer 不能为空");

        executeWithExceptionTranslation("读取文件流", targetBucket, targetKey, () -> {
            // try-with-resources 强行收口，自动关闭云厂商返回的底层 HttpInputStream
            try (InputStream is = doGetInputStream(targetBucket, targetKey)) {
                Assert.notNull(is, "获取到的文件流为空: " + targetKey);
                streamConsumer.accept(is);
            }
            return null;
        });
    }

    @Override
    public byte[] downloadBytes(String bucket, String objectKey) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        readStream(bucket, objectKey, is -> {
            try {
                is.transferTo(bos);
            } catch (Exception e) {
                throw new StorageException("读取流字节失败: " + objectKey, e);
            }
        });
        return bos.toByteArray();
    }

    @Override
    public Optional<StorageFile> getMetadata(String bucket, String objectKey) {
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        return executeWithExceptionTranslation("获取文件元数据", targetBucket, targetKey, () ->
                doGetMetadata(targetBucket, targetKey)
        );
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        return executeWithExceptionTranslation("检查文件是否存在", targetBucket, targetKey, () ->
                doExists(targetBucket, targetKey)
        );
    }

    @Override
    public void deleteFile(String bucket, String objectKey) {
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        executeWithExceptionTranslation("删除文件", targetBucket, targetKey, () -> {
            doDeleteFile(targetBucket, targetKey);
            return null;
        });
    }

    @Override
    public void deleteFiles(String bucket, List<String> objectKeys) {
        String targetBucket = getEffectiveBucket(bucket);
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        List<String> normalizedKeys = objectKeys.stream().map(ObjectKeyUtil::normalizeObjectKey).toList();
        executeWithExceptionTranslation("批量删除文件", targetBucket, "batch(" + normalizedKeys.size() + ")", () -> {
            doDeleteFiles(targetBucket, normalizedKeys);
            return null;
        });
    }

    // ─── 3. URL 访问体系模版 ───────────────────────────────────────

    @Override
    public String getPublicUrl(String bucket, String objectKey) {
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        // 如果用户自定义配置了 publicEndpoint，优先使用自定义网关/CDN 域名
        String publicEndpoint = properties.getPublicEndpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            if (publicEndpoint.endsWith(SLASH)) {
                publicEndpoint = publicEndpoint.substring(0, publicEndpoint.length() - 1);
            }
            return publicEndpoint + SLASH + bucket + SLASH + objectKey;
        }
        return doGetPublicUrl(targetBucket, targetKey);
    }

    @Override
    public String getPresignedUrl(String bucket, String objectKey, Duration duration) {
        String targetBucket = getEffectiveBucket(bucket);
        String targetKey = normalizeObjectKey(objectKey);
        Duration effectiveDuration = duration != null ? duration : Duration.ofMinutes(15);
        return doGetPresignedUrl(targetBucket, targetKey, effectiveDuration);
    }

    @Override
    public String getGatewayUrl(String bucket, String objectKey, Duration duration) {
        String gatewaySecretKey = properties.getGatewaySecretKey();
        Assert.hasText(gatewaySecretKey, "未配置 fireworks.storage.gateway-secret-key，无法生成签名网关地址！");

        // 1. 获取基础公开 URL（例如: https://api.example.com/my-bucket/avatar.jpg）
        String baseUrl = getPublicUrl(bucket, objectKey);

        // 2. 计算绝对过期时间戳（10位 Unix 秒级时间戳）
        long expires = Instant.now().plus(duration).getEpochSecond();

        // 3. 对 (baseUrl + ":" + expires) 进行 HMAC-SHA256 签名
        String signature = HashUtil.hashWithSalt(baseUrl + StringPool.COLON + expires, gatewaySecretKey);

        // 4. 拼接干净的 URL 返回给前端
        return String.format("%s?t=%d&signature=%s", baseUrl, expires, signature);
    }

    // ─── 4. 留给各子类实现的抽象 Hook 方法 ─────────────────────────────

    protected abstract StorageFile doUpload(String bucket, String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception;

    protected abstract InputStream doGetInputStream(String bucket, String objectKey) throws Exception;

    protected abstract Optional<StorageFile> doGetMetadata(String bucket, String objectKey) throws Exception;

    protected abstract boolean doExists(String bucket, String objectKey) throws Exception;

    protected abstract void doDeleteFile(String bucket, String objectKey) throws Exception;

    protected abstract void doDeleteFiles(String bucket, List<String> objectKeys) throws Exception;

    protected abstract String doGetPublicUrl(String bucket, String objectKey);

    protected abstract String doGetPresignedUrl(String bucket, String objectKey, Duration duration);
}
