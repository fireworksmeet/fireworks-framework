package com.yzm.fireworks.storage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * 存储文件统一元数据实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageFile implements Serializable {

    /** 桶名 */
    private String bucket;

    /** 对象完整路径 (e.g. "avatar/2026/08/11/user_123.png") */
    private String objectKey;

    /** 文件大小（字节），未知时为 -1 */
    private long size;

    /** 内容类型 (e.g. "image/png") */
    private String contentType;

    /** 文件 ETag / MD5 哈希值 */
    private String etag;

    /** 公网/私网可访问 URL */
    private String url;

    /** 用户自定义元数据 (X-Amz-Meta-* / X-Oss-Meta-*) */
    private Map<String, String> userMetadata;

    /** 最后修改时间（绝对时间点，跨时区一致） */
    private Instant lastModified;
}