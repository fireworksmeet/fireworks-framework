package com.yzm.fireworks.storage.aliyun;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import com.yzm.fireworks.storage.api.DirectUploadService;
import com.yzm.fireworks.storage.api.UploadCredential;
import com.yzm.fireworks.storage.api.UploadType;
import com.yzm.fireworks.storage.core.AbstractStorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import com.yzm.fireworks.storage.core.StorageUrlUtils;
import com.yzm.fireworks.storage.core.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阿里云 OSS 直传凭证实现。
 * <p>
 * 目录归一化逻辑统一复用 {@link AbstractStorageService#normalizeDir(String)}，
 * 与 {@code AliyunStorageService} / {@code MinioStorageService} 保持完全一致的 objectKey 规则
 * （不带前导斜杠、带末尾斜杠），避免直传上传的对象和普通上传的对象落在不同的“虚拟目录”下。
 */
@Slf4j
public class AliyunDirectUploadService extends AbstractStorageService implements DirectUploadService {

    private static final long ZERO_BYTE = 0;

    // 以下均为阿里云 OSS PostPolicy 表单直传协议规定的固定字段名/取值，不是业务自定义命名。
    private static final String FORM_FIELD_ACCESS_KEY_ID = "OSSAccessKeyId";
    private static final String FORM_FIELD_SIGNATURE = "signature";
    private static final String FORM_FIELD_POLICY = "policy";
    private static final String FORM_FIELD_KEY = "key";
    private static final String FORM_FIELD_SUCCESS_ACTION_STATUS = "success_action_status";
    private static final String FORM_FIELD_CALLBACK = "callback";
    private static final String SUCCESS_ACTION_STATUS_OK = "200";
    /** OSS 会在上传时将该占位符替换为客户端提交的真实文件名。 */
    private static final String FILENAME_PLACEHOLDER = "${filename}";

    private final OSS client;
    private final StorageProperties properties;

    public AliyunDirectUploadService(OSS client, StorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public UploadCredential getUploadCredential(String bucket, String objectName, Duration duration) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.hasText(objectName, "objectName 不能为空");
        Assert.notNull(duration, "duration 不能为空");
        Date expiration = new Date(System.currentTimeMillis() + duration.toMillis());
        // 注意：参数版 generatePresignedUrl(bucket, key, expiration) 默认生成的是 GET 方法的预签名 URL
        // （等价于 getPresignedUrl，用于下载），并不是用于直传的 PUT 签名。这里必须通过
        // GeneratePresignedUrlRequest 显式指定 HttpMethod.PUT，否则生成的凭证根本无法用于上传。
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectName, HttpMethod.PUT);
        request.setExpiration(expiration);
        URL url = client.generatePresignedUrl(request);
        // OSS V1 签名不含 Host，签名后替换为公网地址安全有效。
        String urlStr = url.toString();
        String publicEndpoint = properties.getPublicEndpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            urlStr = StorageUrlUtils.replaceHost(urlStr, StorageUrlUtils.buildBucketUrl(bucket, publicEndpoint, true));
        }
        log.info("获取阿里云直传凭证成功, bucket={}, object={}", bucket, objectName);
        return UploadCredential.builder()
                .type(UploadType.PRESIGNED_PUT)
                .url(urlStr)
                .expiration(expiration.getTime())
                .build();
    }

    @Override
    public UploadCredential getUploadCredentialByPostPolicy(String bucket, String dir, Duration duration) {
        Assert.hasText(bucket, "bucket 不能为空");
        Assert.notNull(duration, "duration 不能为空");
        // 复用与 AliyunStorageService 完全一致的目录归一化规则：不带前导斜杠、带末尾斜杠。
        String normalizedDir = normalizeDir(dir);

        Date expirationDate = new Date(System.currentTimeMillis() + duration.toMillis());
        String postPolicy = generatePolicy(normalizedDir, expirationDate);
        String signature;
        try {
            signature = client.calculatePostSignature(postPolicy);
        } catch (Exception e) {
            throw new StorageException("生成阿里云表单直传签名失败, bucket=" + bucket, e);
        }
        String encodedPolicy = BinaryUtil.toBase64String(postPolicy.getBytes(StandardCharsets.UTF_8));

        Map<String, String> formData = new LinkedHashMap<>();
        formData.put(FORM_FIELD_ACCESS_KEY_ID, properties.getAliyun().getAccessKey());
        formData.put(FORM_FIELD_SIGNATURE, signature);
        formData.put(FORM_FIELD_POLICY, encodedPolicy);
        formData.put(FORM_FIELD_KEY, normalizedDir + FILENAME_PLACEHOLDER);
        // 不设置该字段时 OSS 默认返回 204/301，浏览器端表单直传（如 XHR/Fetch 监听响应）通常需要明确的 200 响应。
        formData.put(FORM_FIELD_SUCCESS_ACTION_STATUS, SUCCESS_ACTION_STATUS_OK);

        String callbackUrl = properties.getAliyun().getCallbackUrl();
        if (StringUtils.hasText(callbackUrl)) {
            formData.put(FORM_FIELD_CALLBACK, buildCallbackParam(callbackUrl));
        } else {
            log.warn("fireworks.storage.aliyun.callback-url 未配置，OSS 不会在上传完成后回调应用服务器，"
                    + "前端需要自行在表单上传成功后再调用业务接口确认文件信息");
        }

        log.info("获取阿里云表单直传凭证成功, bucket={}, dir={}", bucket, normalizedDir);
        return UploadCredential.builder()
                .type(UploadType.POST_POLICY)
                .url(buildHost(bucket))
                .formData(formData)
                .expiration(expirationDate.getTime())
                .build();
    }

    /**
     * 构建 OSS 上传回调参数（callback 表单字段），上传成功后 OSS 会以该回调体 POST 通知应用服务器。
     * 参考阿里云文档：callbackBody 中的占位符会在 OSS 侧被替换为真实值。
     */
    private String buildCallbackParam(String callbackUrl) {
        String callbackBody = "bucket=${bucket}&object=${object}&size=${size}"
                + "&mimeType=${mimeType}&etag=${etag}";
        String callbackJson = String.format(
                "{\"callbackUrl\":\"%s\",\"callbackBody\":\"%s\",\"callbackBodyType\":\"application/x-www-form-urlencoded\"}",
                callbackUrl, callbackBody);
        return BinaryUtil.toBase64String(callbackJson.getBytes(StandardCharsets.UTF_8));
    }

    private String generatePolicy(String dir, Date expiration) {
        PolicyConditions policyConditions = new PolicyConditions();
        policyConditions.addConditionItem(
                PolicyConditions.COND_CONTENT_LENGTH_RANGE, ZERO_BYTE, properties.getDirectUploadMaxSize());
        policyConditions.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);
        return client.generatePostPolicy(expiration, policyConditions);
    }

    private String buildHost(String bucketName) {
        // PostPolicy 的上传目标 URL 与 getFileUrl/getPresignedUrl 保持一致的地址策略：
        // 配置了 publicEndpoint 就用公网地址，否则从内网 endpoint 拼接。
        String publicEndpoint = properties.getPublicEndpoint();
        String endpoint = StringUtils.hasText(publicEndpoint)
                ? publicEndpoint
                : properties.getAliyun().getEndpoint();
        return StorageUrlUtils.buildBucketUrl(bucketName, endpoint, true);
    }
}