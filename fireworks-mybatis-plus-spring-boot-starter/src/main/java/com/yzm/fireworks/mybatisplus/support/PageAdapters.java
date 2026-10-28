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
 * <b>只适配 Offset 模式</b>：它需要从 {@link IPage} 取 {@code records / current / size / total}，
 * 是真正的字段搬运。Cursor 模式没有页号与总数，{@link PageResponse#ofCursor} 接收
 * 「记录列表 + 请求页大小」即可，<b>不需要 {@code IPage}</b>——多查一条后直接调用它即可。
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
        Assert.notNull(page, "IPage 不能为空");
        // 复用 PageWithFooterResponse 的装配逻辑，避免父类新增字段时这里漏拷
        return PageWithFooterResponse.ofOffset(page.getRecords(), page.getCurrent(), page.getSize(),
                page.getTotal(), mapper, footer);
    }

}
