package com.yzm.fireworks.mybatisplus.sort;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * 排序构造器。
 * <p>
 * 支持两种写法：字符串列名（手写 SQL 场景）与方法引用（编译期可校验字段，避免拼错列名）。
 *
 * <pre>{@code
 * SortDefinition definition = SortBuilder.builder()
 *         .desc("create_time")
 *         .asc(User::getId)          // 自动转为 id
 *         .build();
 * }</pre>
 *
 * <p><b>方法引用的局限</b>：列名按「属性名 → 驼峰转下划线」推导（{@code getCreateTime → create_time}），
 * 不读取 {@code @TableField} 上自定义的列名，也不感知全局的列名策略。
 * 实体存在自定义列名（如 {@code @TableField("ext_info")}）时，请用字符串重载显式写列名。
 *
 * @author JYuan
 */
public class SortBuilder {

    private final SortDefinition definition = new SortDefinition();

    public static SortBuilder builder() {
        return new SortBuilder();
    }

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

    /**
     * 返回当前排序的快照；之后再调用 {@code asc/desc} 不会影响已经 build 的结果。
     */
    public SortDefinition build() {
        return definition.copy();
    }

    /**
     * 方法引用 → 数据库列名：{@code User::getCreateTime} → {@code create_time}。
     */
    private <T> String getFieldName(SFunction<T, ?> column) {
        LambdaMeta meta = LambdaUtils.extract(column);
        return StringUtils.camelToUnderline(PropertyNamer.methodToProperty(meta.getImplMethodName()));
    }

}
