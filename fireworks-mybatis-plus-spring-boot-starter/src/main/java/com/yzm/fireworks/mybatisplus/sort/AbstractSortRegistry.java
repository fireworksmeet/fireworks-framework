package com.yzm.fireworks.mybatisplus.sort;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;

/**
 * 抽象排序注册类：把「业务排序枚举」映射为「一组排序项」。
 * <p>
 * 前端只传枚举（如 {@code SortType.LATEST}），具体排哪些列、什么方向由服务端注册决定——
 * 列名不来自请求参数，因此不存在排序字段注入的问题。
 * <p>
 * 排序项直接是 MyBatis-Plus 的 {@link OrderItem}，可原样应用到分页对象或查询条件：
 * <pre>{@code
 * public class OrderSortRegistry extends AbstractSortRegistry<SortType> {
 *     @Override
 *     protected void register() {
 *         register(SortType.LATEST, SortBuilder.builder().desc("create_time").desc("id").build());
 *     }
 * }
 *
 * registry.apply(SortType.LATEST, page);              // 分页查询
 * registry.apply(SortType.LATEST, queryWrapper);      // 条件查询
 * String orderBy = registry.getOrderBySql(SortType.LATEST);  // "create_time DESC, id DESC"（手写 SQL）
 * }</pre>
 *
 * @author JYuan
 */
public abstract class AbstractSortRegistry<E extends Enum<E>> {

    private final Map<E, SortDefinition> registry;

    protected AbstractSortRegistry(Class<E> clazz) {
        registry = new EnumMap<>(clazz);
        register();
    }

    /**
     * 注册排序枚举与排序项的映射关系。
     */
    protected abstract void register();

    protected void register(E type, SortDefinition definition) {
        registry.put(type, definition);
    }

    /**
     * 应用到分页对象（{@code selectPage}）。
     * <p>
     * <b>追加语义</b>：不会清除 page 上已有的排序项。同一查询只应调用一次；
     * 确需替换请先 {@code page.orders().clear()} 再调用，或改用显式的替换逻辑。
     */
    public void apply(E type, IPage<?> page) {
        if (page == null) {
            return;
        }
        List<OrderItem> items = getDefinition(type).getItems();
        if (ObjectUtils.isEmpty(items)) {
            return;
        }
        List<OrderItem> orders = page.orders();
        Assert.notNull(orders, "当前 IPage 实现的 orders 集合未初始化");
        orders.addAll(copyOf(items));
    }

    /**
     * 应用到 {@link QueryWrapper}（{@code selectList}）。
     * <p>
     * <b>追加语义</b>：wrapper 没有"只清空 orderBy"的公开 API，因此这里一律追加。
     * 同一查询只应调用一次。
     */
    @SuppressWarnings("rawtypes")
    public void apply(E type, QueryWrapper<?> queryWrapper) {
        apply(type, (AbstractWrapper) queryWrapper);
    }

    /**
     * 应用到 {@link LambdaQueryWrapper}（{@code selectList}），追加语义，同
     * {@link #apply(Enum, QueryWrapper)}。
     */
    @SuppressWarnings("rawtypes")
    public void apply(E type, LambdaQueryWrapper<?> queryWrapper) {
        apply(type, (AbstractWrapper) queryWrapper);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void apply(E type, AbstractWrapper queryWrapper) {
        if (queryWrapper == null) {
            return;
        }
        List<OrderItem> items = getDefinition(type).getItems();
        if (ObjectUtils.isEmpty(items)) {
            return;
        }
        for (OrderItem item : items) {
            queryWrapper.orderBy(true, item.isAsc(), item.getColumn());
        }
    }

    /**
     * 取某排序类型对应的排序项（副本，可安全交给调用方）。
     */
    public List<OrderItem> getSortItems(E type) {
        return copyOf(getDefinition(type).getItems());
    }

    /**
     * 导出为纯 SQL 的 {@code ORDER BY} 子句，例如 {@code "create_time DESC, id ASC"}。
     * <p>无排序项时返回空串（调用方据此决定是否拼接 {@code ORDER BY}）。
     */
    public String getOrderBySql(E type) {
        List<OrderItem> items = getDefinition(type).getItems();
        if (ObjectUtils.isEmpty(items)) {
            return EMPTY;
        }
        return items.stream()
                .map(item -> item.getColumn() + (item.isAsc() ? " ASC" : " DESC"))
                .collect(Collectors.joining(", "));
    }

    private SortDefinition getDefinition(E type) {
        SortDefinition definition = registry.get(type);
        Assert.notNull(definition, "不支持的排序类型：" + type);
        return definition;
    }

    /**
     * {@link OrderItem} 是可变的，对外一律给副本，避免调用方改到注册表里的共享状态。
     */
    private static List<OrderItem> copyOf(List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItem().setColumn(item.getColumn()).setAsc(item.isAsc()))
                .toList();
    }

}
