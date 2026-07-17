package com.yzm.fireworks.web.sort;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * @author JYuan
 * 排序构造器
 */
public class SortBuilder {

    private final SortDefinition definition = new SortDefinition();

    public static SortBuilder builder() {
        return new SortBuilder();
    }

    // 核心：直接向 definition 注入 MP 的 OrderItem
    public SortBuilder asc(String column) {
        definition.add(OrderItem.asc(column));
        return this;
    }

    public SortBuilder desc(String column) {
        definition.add(OrderItem.desc(column));
        return this;
    }

    public <T> SortBuilder asc(SFunction<T, ?> column) {
        return asc(getFieldName(column));
    }

    public <T> SortBuilder desc(SFunction<T, ?> column) {
        return desc(getFieldName(column));
    }

    public SortDefinition build() {
        return definition;
    }

    private <T> String getFieldName(SFunction<T, ?> column) {
        LambdaMeta meta = LambdaUtils.extract(column);
        return StringUtils.camelToUnderline(PropertyNamer.methodToProperty(meta.getImplMethodName()));
    }

}