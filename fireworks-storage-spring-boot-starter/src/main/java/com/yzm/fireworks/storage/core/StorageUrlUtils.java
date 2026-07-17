package com.yzm.fireworks.storage.core;

import com.yzm.fireworks.common.util.StrUtil;

import static com.yzm.fireworks.common.constants.StringPool.*;

public final class StorageUrlUtils {

    private StorageUrlUtils() {
    }

    public static String buildEndpointUrl(String endpoint, boolean secure) {
        if (endpoint.contains(HOST_PREFIX)) {
            return endpoint;
        }
        return (secure ? HTTPS_HOST_PREFIX : HTTP_HOST_PREFIX) + endpoint;
    }

    /**
     * 替换 URL 的 scheme+host+port 部分，保留原始 path 和 query string。
     * <p>
     * 用于将内网地址生成的 URL 中的 host 替换为公网地址，仅适用于签名不覆盖 Host 的场景：
     * <ul>
     *     <li>阿里云 OSS V1 签名：CanonicalizedResource 只含路径，不含 Host，替换安全。</li>
     *     <li>MinIO/S3 PostPolicy：签名只覆盖 policy 文档，不含上传目标 URL，替换安全。</li>
     *     <li>MinIO/S3 SigV4 预签名 URL：Host 在签名范围内，<b>不能使用本方法</b>，
     *     必须在签名时就使用公网地址构建 {@code MinioClient}。</li>
     * </ul>
     *
     * @param originalUrl 含内网 host 的原始 URL
     * @param newHost     公网 host，如 {@code https://bucket.oss-cn-shanghai.aliyuncs.com}，末尾不要带斜杠
     */
    public static String replaceHost(String originalUrl, String newHost) {
        String host = StrUtil.removeSuffix(newHost, SLASH);

        int schemeEnd = originalUrl.indexOf(HOST_PREFIX);
        if (schemeEnd < 0) {
            return host + SLASH + originalUrl;
        }
        int pathStart = originalUrl.indexOf(SLASH, schemeEnd + 3);
        if (pathStart < 0) {
            return host;
        }
        return host + originalUrl.substring(pathStart);
    }

    /**
     * 构建形如 {scheme}://{bucket}.{host} 的桶访问地址。
     * 与 {@link #buildEndpointUrl} 保持一致的 scheme 推断规则：
     * 若 endpoint 已包含协议头则复用该协议头，否则根据 secure 参数推断为 http/https。
     *
     * @param bucket   桶名
     * @param endpoint 地域节点，如 oss-cn-shanghai.aliyuncs.com 或 https://oss-cn-shanghai.aliyuncs.com
     * @param secure   当 endpoint 未携带协议头时使用的默认协议（true=https，false=http）
     */
    public static String buildBucketUrl(String bucket, String endpoint, boolean secure) {
        String scheme;
        String host;
        int schemeIndex = endpoint.indexOf(HOST_PREFIX);
        if (schemeIndex >= 0) {
            scheme = endpoint.substring(0, schemeIndex + 3);
            host = endpoint.substring(schemeIndex + 3);
        } else {
            scheme = secure ? HTTPS_HOST_PREFIX : HTTP_HOST_PREFIX;
            host = endpoint;
        }
        return scheme + bucket + DOT + host;
    }
}