package com.yzm.fireworks.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * 存储文件元数据。
 * <p>
 * 字段填充契约：{@code putObject(...)} 填充 bucket、objectKey、url、etag、contentType 及可获得的 size；
 * {@code headObject(...)} 额外填充 lastModified、userMetadata。未填充的字段为 {@code null}，
 * 调用方不应假定所有字段都有值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 桶名 */
    private String bucket;

    /** 对象完整路径，如 {@code "avatar/2026/10/01/3f2a...e91b.png"} */
    private String objectKey;

    /**
     * 文件大小（字节）。
     * <p>
     * {@code null} 表示未知——例如流式上传时调用方未提供长度且服务端未回传。
     * 不要用 {@code 0} 或 {@code -1} 之类的哨兵值代替“未知”。
     */
    private Long size;

    /** 内容类型，如 {@code "image/png"} */
    private String contentType;

    /**
     * 服务端返回的 ETag。
     * <p>
     * 语义随上传方式而变：单次上传通常为文件 MD5；分片上传为 {@code "<hash>-<partCount>"}；
     * 启用 SSE 加密时不再是内容 MD5。因此<b>不要把它当作内容摘要使用</b>。
     */
    private String etag;

    /**
     * 可访问 URL。
     * <p>
     * <b>派生字段</b>：由 {@code publicEndpoint}、{@code enableBucketInUrl} 等部署配置决定，
     * 不是对象的固有属性。换 CDN 域名、换桶、改 URL 风格后该值立即失效，
     * 因此<b>不要持久化</b>；需要长期保存时请只存 bucket + objectKey，用时再调
     * {@code S3StorageService#getUrl} 现算。
     */
    private String url;

    /** 用户自定义元数据（对齐 S3 {@code x-amz-meta-*}） */
    private Map<String, String> userMetadata;

    /** 对象最后修改时间（存储服务记录的绝对时间点） */
    private Instant lastModified;
}
