package com.yzm.fireworks.web.sort;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.yzm.fireworks.common.constants.StringPool;
import org.springframework.util.ObjectUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;

/**
 * @author JYuan
 * 抽象排序注册类
 */
public abstract class AbstractSortRegistry<E extends Enum<E>> {
    private final Map<E, SortDefinition> registry;

    protected AbstractSortRegistry(Class<E> clazz) {
        registry = new EnumMap<>(clazz);
        register();
    }

    protected abstract void register();

    protected void register(E type, SortDefinition definition) {
        registry.put(type, definition);
    }

    // ================= 1. 保留给传统 IPage 使用（兼容老代码） =================
    public void apply(E type, IPage<?> page) {
        SortDefinition definition = getDefinition(type);
        // 关键点：面向 IPage 接口操作 orders 集合
        if (page != null && !ObjectUtils.isEmpty(definition.getItems())) {
            // IPage 的 orders() 方法在默认实现中一般不会为 null，保险起见可以做个非空校验或直接初始化
            if (page.orders() == null) {
                // 如果为空（极端情况），可以通过反射或强转注入，但 MP 默认的 Page() 初始化时 orders 已经是一个 ArrayList 了
                // 大多数情况下直接 addAll 即可
                throw new IllegalStateException("当前 IPage 实现类的 orders 集合未初始化");
            }
            // 将组件定义的 OrderItem 批量追加到分页对象的排序列表中
            page.orders().addAll(definition.getItems());
        }
    }

    // ================= 2. 💡 新增：支持 QueryWrapper (用于 selectList / 游标分页) =================
    @SuppressWarnings({"rawtypes"})
    public void apply(E type, QueryWrapper<?> queryWrapper) {
        apply(type, (AbstractWrapper) queryWrapper);
    }

    // ================= 3. 💡 新增：支持 LambdaQueryWrapper (用于 selectList / 游标分页) =================
    @SuppressWarnings({"rawtypes"})
    public void apply(E type, LambdaQueryWrapper<?> queryWrapper) {
        apply(type, (AbstractWrapper) queryWrapper);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void apply(E type, AbstractWrapper queryWrapper) {
        SortDefinition definition = getDefinition(type);
        if (queryWrapper != null && !ObjectUtils.isEmpty(definition.getItems())) {
            for (OrderItem item : definition.getItems()) {
                queryWrapper.orderBy(true, item.isAsc(), item.getColumn());
            }
        }
    }

    // ================= 4. 💡 通用导出：直接导出为纯 SQL 的 ORDER BY 子句 =================
    // 例如返回："create_time DESC, id DESC"
    public String getOrderBySql(E type) {
        SortDefinition definition = getDefinition(type);
        List<OrderItem> items = definition.getItems();
        if (ObjectUtils.isEmpty(items)) {
            return EMPTY;
        }
        return items.stream()
                .map(item -> item.getColumn() + (item.isAsc() ? " ASC" : " DESC"))
                .collect(Collectors.joining(", "));
    }

    private SortDefinition getDefinition(E type) {
        SortDefinition definition = registry.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("不支持的排序类型：" + type);
        }
        return definition;
    }

}