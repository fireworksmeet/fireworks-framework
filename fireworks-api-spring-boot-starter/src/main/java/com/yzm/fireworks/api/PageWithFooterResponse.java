package com.yzm.fireworks.api;

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
 * 带页脚/汇总行的分页响应（适用于报表、财务统计等明细类与汇总类分离的场景）
 * <p>
 * <b>与任何 ORM 无关</b>：不出现持久化框架类型。
 * <p>
 * 【方法命名】与父类保持一致：{@code ofOffset} / {@code ofCursor}，带类型转换的重载多一个 {@code mapper} 参数。
 *
 * @param <T> 明细数据类型
 * @param <F> 页脚/汇总数据类型
 * @author JYuan
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

    // ==================== Offset 模式 ====================

    /**
     * 由 Offset 分页数据构建（含页脚汇总）。
     */
    public static <T, F> PageWithFooterResponse<T, F> ofOffset(List<T> records, long current, long size, long total,
                                                               F footer) {
        return withFooter(PageResponse.ofOffset(records, current, size, total), footer);
    }

    /**
     * 由 Offset 分页数据构建（含页脚汇总），并把记录做 PO -> VO 映射。
     */
    public static <T, R, F> PageWithFooterResponse<R, F> ofOffset(List<T> records, long current, long size, long total,
                                                                  Function<T, R> mapper, F footer) {
        return withFooter(PageResponse.ofOffset(records, current, size, total, mapper), footer);
    }

    // ==================== Cursor 模式 ====================

    /**
     * 由游标分页数据构建（含页脚汇总）。
     */
    public static <T, F> PageWithFooterResponse<T, F> ofCursor(List<T> records, long size,
                                                               Function<T, String> nextCursorFunction, F footer) {
        return withFooter(PageResponse.ofCursor(records, size, nextCursorFunction), footer);
    }

    /**
     * 由游标分页数据构建（含页脚汇总），并把记录做 PO -> VO 映射。
     */
    public static <T, R, F> PageWithFooterResponse<R, F> ofCursor(List<T> records, long size, Function<T, R> mapper,
                                                                  Function<T, String> nextCursorFunction, F footer) {
        return withFooter(PageResponse.ofCursor(records, size, mapper, nextCursorFunction), footer);
    }

    /**
     * 复用父类的分页元信息，仅追加页脚。
     */
    private static <T, F> PageWithFooterResponse<T, F> withFooter(PageResponse<T> base, F footer) {
        PageWithFooterResponse<T, F> response = new PageWithFooterResponse<>();
        response.setCurrent(base.getCurrent());
        response.setSize(base.getSize());
        response.setTotal(base.getTotal());
        response.setHasMore(base.getHasMore());
        response.setNextCursor(base.getNextCursor());
        response.setRecords(base.getRecords());
        response.setFooter(footer);
        return response;
    }
}
