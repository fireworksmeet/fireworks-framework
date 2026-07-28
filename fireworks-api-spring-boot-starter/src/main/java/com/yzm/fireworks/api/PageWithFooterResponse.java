package com.yzm.fireworks.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.function.Function;

/**
 * @author JYuan
 * 带页脚/汇总行的分页响应（适用于报表、财务统计等明细类与汇总类分离的场景）
 * 
 * @param <T> 明细数据类型
 * @param <F> 页脚/汇总数据类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(title = "带页脚汇总的分页响应")
public class PageWithFooterResponse<T, F> extends PageResponse<T> {

    /**
     * 页脚汇总数据（独立的汇总 VO，不污染明细 VO）
     */
    @Schema(description = "页脚/汇总数据")
    private F footer;

    // ==================== 工厂方法 ====================

    public static <R, T, F> PageWithFooterResponse<R, F> create(IPage<T> page, Function<T, R> function, F footer) {
        PageResponse<R> baseResponse = PageResponse.create(page, function);
        
        PageWithFooterResponse<R, F> response = new PageWithFooterResponse<>();
        response.setCurrent(baseResponse.getCurrent());
        response.setSize(baseResponse.getSize());
        response.setTotal(baseResponse.getTotal());
        response.setHasMore(baseResponse.getHasMore());
        response.setRecords(baseResponse.getRecords());
        response.setFooter(footer);
        return response;
    }

    public static <R, T, F> PageWithFooterResponse<R, F> ofCursor(List<T> records, Long size, Function<T, R> mapper, Function<T, String> nextCursorFunction, F footer) {
        PageResponse<R> baseResponse = PageResponse.ofCursor(records, size, mapper, nextCursorFunction);

        PageWithFooterResponse<R, F> response = new PageWithFooterResponse<>();
        response.setSize(baseResponse.getSize());
        response.setHasMore(baseResponse.getHasMore());
        response.setNextCursor(baseResponse.getNextCursor());
        response.setRecords(baseResponse.getRecords());
        response.setFooter(footer);
        return response;
    }
}