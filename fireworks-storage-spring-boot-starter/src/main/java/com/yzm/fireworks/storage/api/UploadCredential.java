package com.yzm.fireworks.storage.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadCredential {

    private UploadType type;

    /**
     * 直传目标 URL (例如 MinIO/OSS 的预签名 PUT URL，或 POST 表单提交的目标地址)
     */
    private String url;

    /**
     * 直传时前端需要携带的额外请求头（如某些 Provider 的预签名 URL 要求携带特定 Header）。
     * 当前 Aliyun/MinIO 的预签名 PUT 实现均不强制要求额外 Header，因此默认为 null；
     * 该字段保留供未来 Provider 或自定义签名场景使用。
     */
    private Map<String, String> headers;

    /**
     * 表单直传参数（针对 POST 表单上传场景，如 policy, signature, key 等）
     */
    private Map<String, String> formData;

    /**
     * 凭证过期时间戳（毫秒），前端可据此判断凭证是否已失效
     */
    private Long expiration;

    /**
     * 文件在云存储中的唯一相对路径/标识（业务落库核心字段）
     * <p>
     * 预签名 PUT 直传时，该值为凭证生成时已确定的完整对象路径。
     * <p>
     * 示例：temp/avatar/2026/08/03/c410df2e.jpg
     */
    private String objectName;

    /**
     * 前端上传成功后的完整回显 URL（带 CDN 域名的最终展示路径）
     * <p>
     * 与 {@link #objectName} 对应：预签名 PUT 直传返回完整地址；
     * <p>
     * 示例：https://img.yourdomain.com/temp/avatar/2026/08/03/c410df2e.jpg
     */
    private String displayUrl;
}
