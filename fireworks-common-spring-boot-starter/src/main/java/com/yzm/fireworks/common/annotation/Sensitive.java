package com.yzm.fireworks.common.annotation;


import com.yzm.fireworks.common.enums.SensitiveType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏声明：序列化时按指定类型对 String 字段脱敏。
 *
 * <p>必须显式给出脱敏类型，不设默认值——脱敏规则选错比漏写更难发现，宁可编译失败。
 *
 * @author JYuan
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏类型，算法见 {@link SensitiveType}
     */
    SensitiveType value();
}
