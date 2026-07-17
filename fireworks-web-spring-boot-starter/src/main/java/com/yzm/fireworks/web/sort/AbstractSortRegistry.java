package com.yzm.fireworks.web.sort;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.EnumMap;
import java.util.Map;

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

    public void apply(E type, IPage<?> page) {
        SortDefinition definition = registry.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("不支持的排序类型：" + type);
        }
        // 关键点：面向 IPage 接口操作 orders 集合
        if (page != null && definition.getItems() != null && !definition.getItems().isEmpty()) {
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

}