package com.yzm.fireworks.storage.config.properties;

import com.yzm.fireworks.storage.model.enums.StorageProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
     * 系统默认存储桶（Bucket）。
     */
    private String defaultBucket;

    /**
     * 公开访问的终端地址，设置后 getFileUrl() 返回此地址 + "/" + bucket + "/" + objectKey。
     * 适用于 CDN 或自定义域名场景，不配置时自动从 endpoint 拼接。
     * 示例：https://cdn.xxx.com
     */
    private String publicEndpoint;

    /**
     * 应用网关 URL 防盗链签名专用密钥（HMAC-SHA256）
     * 存储模块与网关共享此密钥，用于生成与校验网关防盗链地址
     */
    private String gatewaySecretKey;

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
