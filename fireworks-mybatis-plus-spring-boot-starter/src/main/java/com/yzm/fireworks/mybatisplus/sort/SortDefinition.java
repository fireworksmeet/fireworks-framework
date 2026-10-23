package com.yzm.fireworks.mybatisplus.sort;

import com.baomidou.mybatisplus.core.metadata.OrderItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 排序定义：一组有序的 {@link OrderItem}。
 * <p>
 * 直接复用 MyBatis-Plus 的排序项模型，因此既能导出 SQL 片段，也能原样交给
 * {@code IPage} / {@code Wrapper}，无需在「框架模型」与「MP 模型」之间转换。
 *
 * @author JYuan
 */
public class SortDefinition {

    private final List<OrderItem> items = new ArrayList<>();

    void add(OrderItem item) {
        this.items.add(item);
    }

    /**
     * 排序项只读视图：调用方不能增删注册表里的条目（{@link OrderItem} 自身仍可变，使用时请取副本）。
     */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
