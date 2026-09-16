package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数组工具类
 * <p>
 * 解决 Java 数组的一个常见陷阱：{@code (Object[]) } 强转对<b>原始类型数组</b>
 * （{@code long[]}、{@code int[]}、{@code double[]} 等）会抛出
 * {@link ClassCastException}，因为原始类型数组与引用数组在 JVM 中是不同的类型。
 * 本类统一使用 {@link Array} 逐元素读取，对任意数组类型均可用。
 *
 * @author JYuan
 */
@UtilityClass
public class ArrayUtil {

    // ==================== 转换 ====================

    /**
     * 将任意数组转为 {@link List}
     * <p>
     * 支持原始类型数组，元素会自动装箱：
     *
     * <pre>{@code
     * ArrayUtil.toList(new long[]{1L, 2L});        // [1L, 2L]
     * ArrayUtil.toList(new String[]{"a", "b"});    // ["a", "b"]
     * }</pre>
     *
     * @param array 任意数组，可为 {@code null}
     * @return 元素列表；{@code array} 为 {@code null} 时返回<b>空列表</b>
     */
    public static List<Object> toList(Object array) {
        if (array == null || !array.getClass().isArray()) {
            return Collections.emptyList();
        }
        int length = Array.getLength(array);
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(Array.get(array, i));
        }
        return list;
    }

    /**
     * 将集合或任意数组统一转为 {@link Iterable}
     * <p>
     * 用于「入参可能是集合、也可能是数组（含原始类型数组）」的场景，
     * 使调用方可以用同一套逻辑处理两者，无需区分分支：
     *
     * <pre>{@code
     * Iterable<?> iterable = ArrayUtil.toIterable(value);
     * if (iterable != null) {
     *     StreamSupport.stream(iterable.spliterator(), false)
     *             .filter(Objects::nonNull)
     *             ...
     * }
     * }</pre>
     *
     * @param value 集合或任意数组，可为 {@code null}
     * @return {@link Iterable}；既不是 {@link Iterable} 也不是数组时返回 {@code null}
     */
    public static Iterable<?> toIterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value == null || !value.getClass().isArray()) {
            return null;
        }
        return toList(value);
    }
}
