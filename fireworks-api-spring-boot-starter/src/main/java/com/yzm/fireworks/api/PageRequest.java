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
     * 当前页
     */
    @Schema(description = "当前页")
    @Builder.Default
    private Long current = 1L;

    /**
     * pageSize
     */
    @Schema(description = "pageSize")
    @Builder.Default
    private Long size = 10L;

    /**
     * 是否进行 count 查询
     */
    @Builder.Default
    @Schema(description = "是否进行 count 查询")
    private boolean searchCount = true;
}
