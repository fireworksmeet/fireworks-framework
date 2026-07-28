package com.yzm.fireworks.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author JYuan
 * 分页响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(title = "分页响应")
public class PageResponse<T> {

    @Schema(title = "分页数据")
    private List<T> records;

    @Schema(description = "当前页（Offset模式）")
    private Long current;

    @Schema(title = "分页大小")
    private Long size;

    @Schema(description = "总数（searchCount=false 时为 null）")
    private Long total;

    @Schema(description = "是否有下一页")
    private Boolean hasMore;

    @Schema(description = "下一页游标（Cursor模式使用）")
    private String nextCursor;

    // ==================== 1. 传统 Offset 模式工厂方法（兼容 MyBatis-Plus） ====================
    public static <R, T> PageResponse<R> empty(IPage<T> page) {
        PageResponse<R> result = new PageResponse<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setHasMore(false);
        result.setRecords(new ArrayList<>());
        return result;
    }

    public static <T> PageResponse<T> create(IPage<T> page) {
        return create(page, Function.identity());
    }

    public static <R, T> PageResponse<R> create(IPage<T> page, Function<T, R> function) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());

        List<R> records = ObjectUtils.isEmpty(page.getRecords()) ? Collections.emptyList() :
                page.getRecords().stream()
                        .map(function)
                        .filter(Objects::nonNull)
                        .toList();
        response.setRecords(records);
        response.setHasMore(calcHasMore(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
        return response;
    }

    // ==================== 2. App / 海量数据 Cursor 模式工厂方法 ====================

    /**
     * 构建游标分页响应（适用于瀑布流、ES search_after、基于 ID/时间游标）
     * 采用业务标准的 Limit size + 1 技巧自动算 hasMore 和截断
     *
     * @param records            查出的原始数据（建议 SQL limit size + 1）
     * @param size               请求的 pageSize
     * @param nextCursorFunction 提取最后一条记录生成 nextCursor 的函数
     */
    public static <T> PageResponse<T> ofCursor(List<T> records, Long size, Function<T, String> nextCursorFunction) {
        return ofCursor(records, size, Function.identity(), nextCursorFunction);
    }

    /**
     * 游标模式带类型转换（PO -> VO）
     */
    public static <R, T> PageResponse<R> ofCursor(List<T> records, Long size, Function<T, R> mapper, Function<T, String> nextCursorFunction) {
        PageResponse<R> response = new PageResponse<>();
        response.setSize(size);

        if (ObjectUtils.isEmpty(records)) {
            response.setRecords(Collections.emptyList());
            response.setHasMore(false);
            return response;
        }

        // 判断是否有下一页（如果查出来的数量大于请求的 size，说明有下一页）
        boolean hasMore = records.size() > size;
        List<T> realRecords = hasMore ? records.subList(0, size.intValue()) : records;

        // 执行 PO -> VO 转换
        List<R> mappedRecords = realRecords.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .toList();

        response.setRecords(mappedRecords);
        response.setHasMore(hasMore);

        // 如果有下一页，拿截取后最后一条记录提取出 nextCursor
        if (hasMore && nextCursorFunction != null && !realRecords.isEmpty()) {
            T lastRecord = realRecords.getLast();
            response.setNextCursor(nextCursorFunction.apply(lastRecord));
        }

        return response;
    }

    // 计算 Offset 模式下是否有下一页
    protected static <T> Boolean calcHasMore(Long current, Long size, Long total, List<T> records) {
        if (total != null && total >= 0) {
            return (current * size) < total;
        }
        return records != null && records.size() >= size;
    }

}
