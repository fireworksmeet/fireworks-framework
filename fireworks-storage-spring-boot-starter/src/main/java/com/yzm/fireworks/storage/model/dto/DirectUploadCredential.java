package com.yzm.fireworks.storage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 客户端直传凭证（抹平 S3 PUT 与 OSS/COS POST 表单的差异）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectUploadCredential implements Serializable {

    /** 桶名 */
    private String bucket;

    /** 后端预先生成的全局唯一对象路径 */
    private String objectKey;

    /** 上传目标 URL（S3 为带有签名的完整 PUT URL，OSS/COS 为 Bucket Host） */
    private String uploadUrl;

    /** 上传使用的 HTTP 方法，固定为"PUT" */
    @Builder.Default
    private String httpMethod = "PUT";

    /** 
     * 上传需要携带的 HTTP Header 集合（主要用于 S3/MinIO PUT 直传）
     * e.g. Content-Type, x-amz-acl
     */
    private Map<String, String> headers;

    /**
     * uploadUrl 的绝对过期时间戳（秒，10位 Unix 时间戳）
     * 前端判定：Math.floor(Date.now() / 1000) > expireAt
     */
    private long expireAt;
}