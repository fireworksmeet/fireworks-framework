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

    private String url;

    /**
     * 直传时前端需要携带的额外请求头（如某些 Provider 的预签名 URL 要求携带特定 Header）。
     * 当前 Aliyun/MinIO 的预签名 PUT 实现均不强制要求额外 Header，因此默认为 null；
     * 该字段保留供未来 Provider 或自定义签名场景使用。
     */
    private Map<String, String> headers;

    private Map<String, String> formData;

    /**
     * 凭证过期时间戳（毫秒），前端可据此判断凭证是否已失效
     */
    private Long expiration;
}
