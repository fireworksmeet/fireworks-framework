package com.yzm.fireworks.common.enums;

import java.util.HashSet;
import java.util.Set;

/**
 * @author JYuan
 * 异常状态
 */
public interface ExceptionStatus {
    /**
     * 异常状态编码
     */
    int getCode();

    /**
     * 异常消息
     */
    String getMessage();

    default ExceptionStatus format(Object... args) {
        return new ExceptionStatus() {
            @Override
            public int getCode() {
                return ExceptionStatus.this.getCode();
            }

            @Override
            public String getMessage() {
                return String.format(ExceptionStatus.this.getMessage(), args);
            }
        };
    }


    /**
     * 校验业务异常状态码是否唯一
     *
     * @param clazz 业务异常状态枚举类
     */
    static <T extends ExceptionStatus> void checkCodeUnique(Class<T> clazz) {
        T[] values = clazz.getEnumConstants();
        Set<Integer> codeSet = new HashSet<>();
        for (T value : values) {
            int code = value.getCode();
            if (!codeSet.add(code)) {
                throw new IllegalArgumentException(value.getClass().getName() + "存在重复状态码: " + code);
            }
        }
    }
}
