package com.yzm.fireworks.storage.core;

import com.yzm.fireworks.storage.api.StorageProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "fireworks.storage")
@Validated
@Data
public class StorageProperties {

    /**
     * 是否启用存储模块自动装配，默认 true。
     * 设置为 false 时，StorageService 不会被注册。
     */
    private boolean enabled = true;

    /**
     * 存储供应商类型，必填。
     * 可选值：minio、aliyun
     */
    private StorageProvider provider;

    /**
     * 分片大小（字节），默认 5MB。
     * <p>
     * 该值会直接传给各 Provider SDK 自身的分片上传能力，而不是由本框架自己实现分片逻辑：
     * <ul>
     *     <li>Aliyun OSS：传给 {@code UploadFileRequest.setPartSize}，由 OSS SDK 的断点续传上传
     *     ({@code ossClient.uploadFile}) 自行决定是否需要分片、如何分片。</li>
     *     <li>MinIO：传给 {@code PutObjectArgs.stream(in, contentLength, partSize)} 的 partSize 参数，
     *     由 MinIO SDK 自行决定是否需要分片。</li>
     * </ul>
     */
    private long partSize = 5 * 1024 * 1024L;

    /**
     * 表单直传（PostPolicy）凭证允许的最大文件大小（字节），默认 1GB（1073741824 字节）。
     * 仅作为浏览器端表单直传的安全限制条件，与上传时是否分片无关。
     */
    private long directUploadMaxSize = 1024L * 1024 * 1024;

    /**
     * 直传凭证中 {@code displayUrl}（上传成功后回显地址）的有效期，默认 24 小时。
     * <p>
     * 独立于上传凭证的时效：上传凭证通常给几分钟（防止凭证滥用），而回显地址需要覆盖
     * "用户上传后继续填写其他表单信息"的整段过程，因此回显时效应明显更长。
     */
    private Duration displayUrlTtl = Duration.ofHours(24);

    /**
     * 公开访问的终端地址，设置后 getFileUrl() 返回此地址 + "/" + bucket + "/" + objectKey。
     * 适用于 CDN 或自定义域名场景，不配置时自动从 endpoint 拼接。
     * 示例：https://cdn.xxx.com
     */
    private String publicEndpoint;

    private Aliyun aliyun;

    private Minio minio;

    @Data
    public static class Aliyun {

        /**
         * 阿里云 OSS 地域节点，如 oss-cn-shanghai.aliyuncs.com
         * 需在对应 Bucket 概览页获取
         */
        private String endpoint;

        /**
         * 阿里云 RAM 用户 AccessKey ID
         */
        private String accessKey;

        /**
         * 阿里云 RAM 用户 AccessKey Secret
         */
        private String secretKey;

        /**
         * 上传回调地址（必须为公网地址）。
         * OSS 会在文件上传完成后将文件信息通过此回调 URL 发送给应用服务器。
         */
        private String callbackUrl;

        /**
         * 分片上传的并发线程数，默认 4。
         * 该选项是 Aliyun OSS 的专属能力：传给 {@code UploadFileRequest.setTaskNum}，
         * 由 OSS SDK 的断点续传上传（{@code ossClient.uploadFile}）内部并行处理分片；
         * MinIO SDK 的 {@code putObject}/{@code uploadObject} 没有暴露并发度配置，因此该字段不放在 Minio 配置下。
         */
        private int multipartConcurrency = 4;
    }

    @Data
    public static class Minio {

        /**
         * MinIO 服务地址，如 localhost:9000
         */
        private String endpoint;

        /**
         * MinIO Access Key
         */
        private String accessKey;

        /**
         * MinIO Secret Key
         */
        private String secretKey;

        /**
         * 是否使用 HTTPS 连接 MinIO，默认 false。
         * 开发环境通常为 false，生产环境建议配合证书开启。
         */
        private boolean secure;

        /**
         * 上传时如果目标 Bucket 不存在是否自动创建，默认 false。
         * 该选项是 MinIO 的专属能力：Aliyun OSS 出于权限模型限制，普通 RAM 用户无法自动创建 Bucket，
         * 因此该字段只存在于 Minio 配置下，不会出现在 Aliyun 配置中，避免"配了不生效"的歧义。
         */
        private boolean autoCreateBucket;
    }
}
