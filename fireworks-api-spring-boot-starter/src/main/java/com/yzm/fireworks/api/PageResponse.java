package com.yzm.fireworks.api;

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

/**
 * 分页响应。
 * <p>
 * <b>与任何 ORM 无关</b>：不出现 {@code IPage} 之类的持久化框架类型，
 * 只接收「记录列表 + 分页元信息」，因此 MyBatis-Plus / JPA / jOOQ / 手写 SQL 都能直接使用。
 * <p>
 * 【方法命名】按<b>模式</b>成对命名，避免调用方靠对照记忆区分：
 * <ul>
 *   <li>构建：{@link #ofOffset}（offset）/ {@link #ofCursor}（cursor）；</li>
 *   <li>空结果：{@link #emptyOffset} / {@link #emptyCursor}。</li>
 * </ul>
 * 带类型转换的重载把 {@code mapper} 放在参数表末尾，不转换时省略即可。
 *
 * @author JYuan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(title = "分页响应")
public class PageResponse<T> {

    @Schema(title = "分页数据")
    private List<T> records;

    @Schema(description = "当前页（Offset模式）")
    private Long current;

    @Schema(title = "分页大小")
    private Long size;

    @Schema(description = "总数（Offset 模式必填）")
    private Long total;

    @Schema(description = "是否有下一页（Cursor 模式）")
    private Boolean hasMore;

    @Schema(description = "下一页游标（Cursor模式使用）")
    private String nextCursor;

    // ==================== 1. Offset 模式 ====================

    /**
     * 构建空的 Offset 分页响应（总数确认为 0）。
     *
     * @param current 当前页
     * @param size    分页大小
     */
    public static <T> PageResponse<T> emptyOffset(long current, long size) {
        PageResponse<T> response = new PageResponse<>();
        response.setCurrent(current);
        response.setSize(size);
        response.setTotal(0L);
        response.setRecords(new ArrayList<>());
        return response;
    }

    /**
     * 构建 Offset 模式分页响应（不转换记录类型）。
     */
    public static <T> PageResponse<T> ofOffset(List<T> records, long current, long size, long total) {
        return ofOffset(records, current, size, total, Function.identity());
    }

    /**
     * 构建 Offset 模式分页响应，并做 PO -> VO 转换。
     * <p>
     * <b>不设置 {@code hasMore}</b>：Offset 模式的下一页由调用方用 {@code current × size < total} 推导，
     * 再返回一个布尔值既是冗余，也可能与 {@code total} 给出矛盾结论。不想统计总数时请改用 Cursor 模式——
     * 那里的 {@code hasMore} 由 {@code limit size + 1} 得出，是可靠信号。
     *
     * @param records 当前页记录
     * @param current 当前页
     * @param size    分页大小
     * @param total   总数（Offset 模式必须提供）
     * @param mapper  记录转换函数
     */
    public static <T, R> PageResponse<R> ofOffset(List<T> records, long current, long size, long total,
                                                  Function<T, R> mapper) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(current);
        response.setSize(size);
        response.setTotal(total);

        List<R> mapped = ObjectUtils.isEmpty(records) ? Collections.emptyList()
                : records.stream()
                        .map(mapper)
                        .filter(Objects::nonNull)
                        .toList();
        response.setRecords(mapped);
        return response;
    }

    // ==================== 2. Cursor 模式 ====================

    /**
     * 构建空的 Cursor 分页响应。
     *
     * @param size 分页大小
     */
    public static <T> PageResponse<T> emptyCursor(long size) {
        PageResponse<T> response = new PageResponse<>();
        response.setSize(size);
        response.setHasMore(false);
        response.setRecords(new ArrayList<>());
        return response;
    }

    /**
     * 构建游标分页响应（适用于瀑布流、ES search_after、基于 ID/时间游标）
     * 采用业务标准的 Limit size + 1 技巧自动算 hasMore 和截断
     *
     * @param records            查出的原始数据（建议 SQL limit size + 1）
     * @param size               请求的 pageSize
     * @param nextCursorFunction 提取最后一条记录生成 nextCursor 的函数
     */
    public static <T> PageResponse<T> ofCursor(List<T> records, long size, Function<T, String> nextCursorFunction) {
        return ofCursor(records, size, Function.identity(), nextCursorFunction);
    }

    /**
     * 游标模式带类型转换（PO -> VO）
     */
    public static <T, R> PageResponse<R> ofCursor(List<T> records, long size, Function<T, R> mapper,
                                                  Function<T, String> nextCursorFunction) {
        PageResponse<R> response = new PageResponse<>();
        response.setSize(size);

        if (ObjectUtils.isEmpty(records)) {
            response.setRecords(Collections.emptyList());
            response.setHasMore(false);
            return response;
        }

        // 判断是否有下一页（如果查出来的数量大于请求的 size，说明有下一页）
        boolean hasMore = records.size() > size;
        List<T> realRecords = hasMore ? records.subList(0, (int) size) : records;

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

}
