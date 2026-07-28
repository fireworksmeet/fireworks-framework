package com.yzm.fireworks.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author JYuan
 * 分页请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class PageRequest {

    /**
     * 当前页（Offset模式使用，从 1 开始）
     */
    @Schema(description = "当前页（Offset模式使用）", example = "1")
    @Builder.Default
    private Long current = 1L;

    /**
     * pageSize
     */
    @Schema(description = "每页大小", example = "10")
    @Builder.Default
    private Long size = 10L;

    /**
     * 上一页返回的游标（Cursor模式使用，如自增ID、加密字符串等）
     */
    @Schema(description = "上一页返回的游标（Cursor模式使用）")
    private String cursor;

    /**
     * 是否进行 count 查询（大表/深分页优化可设为 false）
     */
    @Schema(description = "是否进行 count 查询", example = "true")
    @Builder.Default
    private boolean searchCount = true;
}
