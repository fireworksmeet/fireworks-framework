package com.yzm.fireworks.api.util;

import com.yzm.fireworks.api.enums.CodeEnum;
import lombok.experimental.UtilityClass;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * 枚举工具类
 *
 * @author JYuan
 */
@UtilityClass
public class EnumUtil {

    /**
     * 基于 {@link CodeEnum} 接口的 match 方法
     * <p>
     * 通过 {@link CodeEnum#getCode()} 进行匹配
     */
    public static <T extends CodeEnum<R>, R extends Serializable> T match(Class<T> enumClass, R code) {
        return match(enumClass, code, null);
    }

    public static <T extends CodeEnum<R>, R extends Serializable> T match(Class<T> enumClass, R code, T def) {
        if (code == null) {
            return def;
        }
        return find(enumClass.getEnumConstants(), item -> code.equals(item.getCode()), def);
    }

    private static <T> T find(T[] enums, Predicate<T> predicate, T defaultValue) {
        return Arrays.stream(enums)
                .filter(predicate)
                .findFirst()
                .orElse(defaultValue);
    }
}
