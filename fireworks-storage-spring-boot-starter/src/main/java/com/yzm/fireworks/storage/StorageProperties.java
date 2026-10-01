package com.yzm.fireworks.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 存储模块统一配置：基于 AWS S3 协议标准。
 * <p>
 * 不再区分厂商，所有 S3 兼容存储（MinIO / SeaweedFS / RustFS / Ceph RGW /
 * 阿里云 OSS 兼容模式 / AWS S3 / 腾讯云 COS / 华为云 OBS）共用同一套配置。
 * 换厂商只需修改 endpoint，无需更换代码。
 */
@ConfigurationProperties(prefix = "fireworks.storage")
@Validated
@Data
public class StorageProperties {

    /**
     * 是否启用存储模块自动装配，默认 true
     */
    private boolean enabled = true;

    /**
     * 系统默认存储桶（Bucket），调用上传/删除/获取URL 时 bucket 留空时使用此值
     */
    private String defaultBucket;

    /**
     * S3 协议兼容的 Endpoint（必填）。
     * <p>
     * 各厂商的 S3 兼容域名不同，常见示例：
     * <ul>
     *   <li>阿里云 OSS：{@code https://s3.oss-<region>.aliyuncs.com}，如 {@code https://s3.oss-cn-shanghai.aliyuncs.com}
     *       —— 注意有 {@code s3.} 前缀，用原生 {@code oss-<region>.aliyuncs.com} 访问 S3 协议可能被拒绝</li>
     *   <li>AWS S3：{@code https://s3.<region>.amazonaws.com}</li>
     *   <li>MinIO / 自建：{@code http://minio.example.com:9000}</li>
     * </ul>
     */
    private String endpoint;

    /**
     * S3 Region，默认 us-east-1。
     * <p>
     * 阿里云 OSS 建议填 Bucket 所在地域 ID（如 {@code cn-shanghai}）：OSS 不支持跨地域访问，
     * 地域与实际 Bucket 不一致时会报 {@code NoSuchBucket} 或 {@code SignatureDoesNotMatch}。
     */
    private String region = "us-east-1";

    /**
     * Access Key
     */
    private String accessKey;

    /**
     * Secret Key
     */
    private String secretKey;

    /**
     * 是否使用 HTTPS（endpoint 未带协议头时生效）
     */
    private boolean secure = false;

    /**
     * 是否使用 Path-Style 访问。
     * <ul>
     *   <li>{@code true}（默认）：{@code https://endpoint/bucket/key}</li>
     *   <li>{@code false}：Virtual-Hosted Style，{@code https://bucket.endpoint/key}</li>
     * </ul>
     * <b>该值必须与存储服务端的要求一致，否则直接 403</b>：
     * <ul>
     *   <li>阿里云 OSS 要求 Virtual-Hosted Style，配成 Path-Style 会报
     *       {@code Please use virtual hosted style to access}</li>
     *   <li>AWS S3、腾讯云 COS、华为云 OBS 同样以 Virtual-Hosted Style 为默认</li>
     *   <li>MinIO / Ceph 等自建服务若未配置泛域名 DNS（{@code *.your-domain}），需使用 Path-Style</li>
     * </ul>
     * Virtual-Hosted Style 下 Bucket 名必须符合 DNS 规范（不能含下划线 {@code _}）。
     */
    private boolean pathStyleAccess = true;

    /**
     * 上传时如果目标 Bucket 不存在是否自动创建，默认 false
     */
    private boolean autoCreateBucket = false;

    /**
     * 公开访问的终端地址（CDN / 自定义域名），可选
     */
    private String publicEndpoint;

    /**
     * 自定义域名场景下，是否在 URL 中拼接 Bucket 前缀。
     * <ul>
     *   <li>true（默认）：生成 {@code https://cdn.example.com/bucket/key}，适用于多 Bucket 共用一个 CDN 的场景</li>
     *   <li>false：生成 {@code https://cdn.example.com/key}，适用于 CDN 已绑定到特定 Bucket 的场景（如 img.example.com 专门服务 avatar-bucket）</li>
     * </ul>
     * 本选项仅在配置了 publicEndpoint 时生效；未配置时由 AWS SDK 根据 pathStyleAccess 自动选择 URL 风格。
     */
    private boolean enableBucketInUrl = true;
}