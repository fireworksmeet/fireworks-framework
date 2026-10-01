package com.yzm.fireworks.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 客户端直传凭证：描述客户端“拿什么、传到哪、用什么方法、在何时之前”完成一次直传。
 * <p>
 * 当前基于 S3 协议 V4 PUT 预签名（SigV4）：客户端以 {@link #httpMethod} 指定的方法，
 * 原样携带 {@link #headers} 中的全部 Header，把整个文件体 PUT 到 {@link #uploadUrl}。
 * 任一 Header 的增删改都会导致签名不匹配（403 SignatureDoesNotMatch）。
 * <p>
 * 凭证是<b>一次性 + 绝对超时</b>的：{@link #expiration} 使用绝对时间戳而非 TTL 秒数，
 * 这样客户端与服务端时钟即使存在偏差，也能与服务端校验结果对齐（服务端校验才是最终裁定者）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectUploadCredential implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 桶名 */
    private String bucket;

    /** 后端预先生成的全局唯一对象路径 */
    private String objectKey;

    /** 上传目标 URL（含完整签名参数） */
    private String uploadUrl;

    /**
     * 上传使用的 HTTP 方法。
     * <p>
     * 目前恒为 {@link HttpMethod#PUT}。保留该字段是为了让客户端按凭证自描述地发起请求，
     * 而不是把方法硬编码在前端。
     */
    private HttpMethod httpMethod;

    /**
     * 上传时必须原样携带的请求 Header。
     * <p>
     * 这些 Header 已参与签名计算，前端增删改任一都会 403。
     * 同名多值场景取首值（SigV4 参与签名的 Header 实践中均为单值）。
     */
    private Map<String, String> headers;

    /**
     * 凭证绝对过期时间（自 Unix epoch 起的<b>秒</b>数，10 位）。
     * <p>
     * 命名对齐 AWS SDK 的 {@code expiration()} 与阿里云 OSS SDK 的 {@code getExpirationTime()}。
     * <p>
     * 采用“秒 + 绝对时间戳”与业界标准一致：JWT 的 {@code exp}、OAuth2 的 {@code expires_in}、
     * CloudFront 签名 URL 的 {@code Expires} 均为秒。
     * 客户端可用 {@code Math.floor(Date.now() / 1000) > expiration} 判断是否需要重新申请。
     */
    private long expiration;

    /**
     * 直传支持的 HTTP 方法。
     * <p>
     * 当前仅 {@link #PUT}（S3 V4 预签名直传）；PostPolicy 表单直传落地后再在此追加 {@code POST}。
     * <p>
     * 用枚举而非 {@code String + 常量}：取值范围随字段类型一同表达，Java 侧有类型安全，
     * 未来新增取值时所有使用点会在编译期暴露。
     * JSON 线上格式不变——Jackson 默认按 {@code name()} 序列化，仍是 {@code "PUT"}。
     * <p>
     * 不使用 {@code org.springframework.http.HttpMethod}：Spring 6 起它已由枚举改为普通类，
     * Jackson 无内置序列化器，会序列化成 {@code {"name":"PUT"}} 破坏前端契约。
     */
    public enum HttpMethod {
        PUT
    }
}
