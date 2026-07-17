package com.yzm.fireworks.api.util;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.yzm.fireworks.api.enums.IOptionEnum;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * 枚举工具类
 *
 * @author JYuan
 */
public class EnumUtil {

    /**
     * 基于 IEnum 接口的 match 方法
     * <p>
     * 适用于实现了 {@link IEnum} 的枚举，通过 {@link IEnum#getValue()} 进行匹配
     */
    public static <T extends IEnum<R>, R extends Serializable> T match(Class<T> enumClass, R value) {
        return match(enumClass, value, null);
    }

    public static <T extends IEnum<R>, R extends Serializable> T match(Class<T> enumClass, R value, T def) {
        if (value == null) {
            return def;
        }
        return find(enumClass.getEnumConstants(), item -> value.equals(item.getValue()), def);
    }

    /**
     * 基于 IOptionEnum 接口的 match 方法
     * <p>
     * 适用于实现了 {@link IOptionEnum} 的选项枚举，通过 {@link IOptionEnum#getValue()} 进行匹配
     */
    public static <T extends Enum<T> & IOptionEnum> T match(Class<T> enumClass, Integer value) {
        return match(enumClass, value, null);
    }

    public static <T extends Enum<T> & IOptionEnum> T match(Class<T> enumClass, Integer value, T def) {
        if (value == null) {
            return def;
        }
        return find(enumClass.getEnumConstants(), item -> value.equals(item.getValue()), def);
    }

    private static <T> T find(T[] enums, Predicate<T> predicate, T defaultValue) {
        return Arrays.stream(enums)
                .filter(predicate)
                .findFirst()
                .orElse(defaultValue);
    }
}
