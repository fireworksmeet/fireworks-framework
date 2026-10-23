package com.yzm.fireworks.mybatisplus.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yzm.fireworks.api.PageResponse;
import com.yzm.fireworks.api.PageWithFooterResponse;
import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页对象 -> 框架分页响应的适配。
 * <p>
 * 两种模式成对提供：
 * <ul>
 *   <li>{@link #offset(IPage)}：Offset 模式，依赖 {@code total}（必须允许 count 查询）；</li>
 *   <li>{@link #cursor(IPage, Function)}：Cursor 模式，查询「size + 1」条，不需要 count。</li>
 * </ul>
 * <b>为什么放在本模块</b>：{@code PageResponse} 本身与 ORM 无关（见 {@code api} 模块），
 * 而「把 {@link IPage} 转成响应」这一步天然只服务于 MyBatis-Plus 用户，
 * 因此适配代码归到本粘合层，而不是污染 {@code api}。
 *
 * @author JYuan
 */
@UtilityClass
public class PageAdapters {

    /**
     * Offset 模式（不做类型映射）。
     */
    public static <T> PageResponse<T> offset(IPage<T> page) {
        return offset(page, Function.identity());
    }

    /**
     * Offset 模式，并把记录做 PO -> VO 映射。
     * <p>
     * <b>{@code IPage} 必须允许 count 查询</b>：Offset 模式的下一页由调用方的 {@code total} 推导，
     * 而 MP 在 {@code searchCount=false} 时 {@code getTotal()} 恒为 {@code 0}，会让客户端误判为"没有数据"。
     * 不想付出 count 代价时，请改用 {@link #cursor(IPage, Function)}。
     */
    public static <R, T> PageResponse<R> offset(IPage<T> page, Function<T, R> mapper) {
        Assert.notNull(page, "IPage 不能为空");
        List<T> records = page.getRecords();
        long current = page.getCurrent();
        long size = page.getSize();
        long total = page.getTotal();

        return PageResponse.ofOffset(records, current, size, total, mapper);
    }

    /**
     * Offset 模式，并附加页脚汇总。
     */
    public static <R, T, F> PageWithFooterResponse<R, F> offset(IPage<T> page, Function<T, R> mapper, F footer) {
        PageResponse<R> base = offset(page, mapper);

        PageWithFooterResponse<R, F> response = new PageWithFooterResponse<>();
        response.setCurrent(base.getCurrent());
        response.setSize(base.getSize());
        response.setTotal(base.getTotal());
        response.setRecords(base.getRecords());
        response.setFooter(footer);
        return response;
    }

    /**
     * 页大小从 {@code page.getSize() - 1} 还原，因此调用方无需重复传 size。
     */
    public static <T> PageResponse<T> cursor(IPage<T> page, Function<T, String> nextCursorFunction) {
        return cursor(page, Function.identity(), nextCursorFunction);
    }

    /**
     * Cursor 模式，并对记录做 PO -> VO 映射。
     */
    public static <T, R> PageResponse<R> cursor(IPage<T> page, Function<T, R> mapper,
                                                Function<T, String> nextCursorFunction) {
        Assert.notNull(page, "IPage 不能为空");
        long size = page.getSize() - 1;
        Assert.isTrue(size > 0, "构造 Cursor 模式时，由于 size = size + 1。因此 IPage 中的 size 必须大于 1。");
        return PageResponse.ofCursor(page.getRecords(), size, mapper, nextCursorFunction);
    }
}
