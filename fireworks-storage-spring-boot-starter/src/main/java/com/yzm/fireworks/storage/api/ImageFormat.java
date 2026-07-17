package com.yzm.fireworks.storage.api;

import lombok.Getter;

/**
 * 图片输出格式，用于 {@link ImageOptions#getFormat()}，指定后会将图片实时转码为目标格式再返回。
 * <p>
 * 各 Provider 的具体实现：
 * <ul>
 *     <li>Aliyun OSS：拼接 {@code image/format,<ext>} 图片处理参数。</li>
 *     <li>MinIO：拼接 imgproxy 的 {@code f:<ext>} 处理参数。</li>
 * </ul>
 */
@Getter
public enum ImageFormat {

    JPG("jpg"),

    PNG("png"),

    WEBP("webp"),

    AVIF("avif"),

    GIF("gif");

    /**
     * -- GETTER --
     *  各 Provider 拼接图片处理参数时使用的格式扩展名（小写，不带点）。
     */
    private final String extension;

    ImageFormat(String extension) {
        this.extension = extension;
    }

}