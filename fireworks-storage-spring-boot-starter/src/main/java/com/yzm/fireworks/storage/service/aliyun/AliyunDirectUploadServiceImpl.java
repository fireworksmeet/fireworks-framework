package com.yzm.fireworks.storage.service.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.yzm.fireworks.common.util.TimeUtil;
import com.yzm.fireworks.storage.exception.StorageException;
import com.yzm.fireworks.storage.orphan.OrphanCleanupProperties;
import com.yzm.fireworks.storage.orphan.OrphanFileGuard;
import com.yzm.fireworks.storage.service.AbstractDirectUploadService;
import com.yzm.fireworks.storage.service.AbstractStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;

/**
 * 阿里云 OSS 直传凭证实现。
 * <p>
 * 签发凭证时，若孤儿清理能力已启用（存在 {@link OrphanFileGuard}）且配置
 * {@code fireworks.storage.orphan-cleanup.auto-mark-pending=true}（默认），会自动登记待确认记录，
 * 业务方无需手动调用 {@link OrphanFileGuard#pending}，避免漏写导致孤儿文件保护失效。
 */
@Slf4j
public class AliyunDirectUploadServiceImpl extends AbstractDirectUploadService {
    private final OSS ossClient;

    public AliyunDirectUploadServiceImpl(OSS ossClient,
                                         AbstractStorageService storageService,
                                         @Nullable OrphanFileGuard orphanFileGuard,
                                         OrphanCleanupProperties orphanCleanupProperties) {
        super(storageService, orphanFileGuard, orphanCleanupProperties);
        this.ossClient = Objects.requireNonNull(ossClient, "ossClient 不能为 null");
    }

    @Override
    protected String doIssueCredential(String bucket, String objectKey, String contentType, Duration duration) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Date expiration = TimeUtil.toDate(now.plus(duration));

            // 1. 创建 PUT 方法的预签名请求
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.PUT);
            request.setExpiration(expiration);

            // 如果指定了 ContentType，将其加入签名范围内，规范客户端上传行为
            if (StringUtils.hasText(contentType)) {
                request.setContentType(contentType);
            }

            // 2. 生成预签名直传 URL
            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            throw new StorageException("签发阿里云 OSS 直传凭证失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }
}