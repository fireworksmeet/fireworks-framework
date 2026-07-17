package com.yzm.fireworks.web.sort;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author JYuan
 * 排序定义
 */
@Getter
public class SortDefinition {

    private final List<OrderItem> items = new ArrayList<>();

    void add(OrderItem item) {
        this.items.add(item);
    }

}