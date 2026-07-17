package com.yzm.fireworks.storage.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.Assert;

/**
 * {@link StorageService#getFileUrl(String, String, ImageOptions)} 的图片处理选项。
 * <p>
 * 三个字段都是可选的，且可以任意组合：
 * <ul>
 *     <li>{@code width}、{@code height} 都指定：等比缩放后裁剪填充到目标宽高（效果与旧版
 *     {@code getThumbnailUrl} 一致）。</li>
 *     <li>只指定 {@code width} 或只指定 {@code height} 其中一个：按比例缩放，缺失的一边自动按原图宽高比计算，
 *     不会裁剪图片内容。</li>
 *     <li>只指定 {@code format}，不指定宽高：原图尺寸不变，仅转换图片格式（例如 PNG 转 WebP）。</li>
 *     <li>三者同时指定：先按宽高处理，再转换为目标格式。</li>
 * </ul>
 * 整个 {@code options} 为 {@code null} 时不会经过本类，{@link StorageService#getFileUrl(String, String, ImageOptions)}
 * 会直接返回原图地址（等价于调用 {@link StorageService#getFileUrl(String, String)}）。
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public final class ImageOptions {

    /**
     * 期望宽度（像素），可为空。为空且 {@code height} 不为空时按 {@code height} 等比缩放。
     */
    private Integer width;

    /**
     * 期望高度（像素），可为空。为空且 {@code width} 不为空时按 {@code width} 等比缩放。
     */
    private Integer height;

    /**
     * 期望输出的图片格式，可为空。为空时保持原图格式不变。
     */
    private ImageFormat format;

    /**
     * 是否指定了任意一边的目标尺寸（只要 width/height 任一不为空即可，不要求两者同时存在）。
     */
    public boolean hasResize() {
        return width != null || height != null;
    }

    /**
     * 是否指定了目标输出格式。
     */
    public boolean hasFormat() {
        return format != null;
    }

    /**
     * 校验字段合法性，并保证至少指定了 resize 或 format 中的一项，避免传入一个“什么都不做”的空 options。
     */
    public void validate() {
        Assert.isTrue(hasResize() || hasFormat(), "ImageOptions 至少需要指定 width、height 或 format 中的一项");
        if (width != null) {
            Assert.isTrue(width > 0, "width 必须大于0");
        }
        if (height != null) {
            Assert.isTrue(height > 0, "height 必须大于0");
        }
    }
}