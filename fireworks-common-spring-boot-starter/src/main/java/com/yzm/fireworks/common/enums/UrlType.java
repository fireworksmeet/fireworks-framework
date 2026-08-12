package com.yzm.fireworks.common.enums;

/**
 * 对象存储 URL 类型
 *
 * @author JYuan
 */
public enum UrlType {

    /**
     * 公开直连地址（如 CDN / Public Bucket 地址）
     */
    PUBLIC,

    /**
     * 云厂商 S3 临时预签名地址
     */
    PRESIGNED,

    /**
     * 框架自研 HMAC 签名防盗链网关地址
     */
    GATEWAY
}
