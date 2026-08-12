package com.yzm.fireworks.storage.model.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.yzm.fireworks.storage.advisor.StorageUrlJsonSerializer;
import com.yzm.fireworks.storage.model.enums.UrlType;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = StorageUrlJsonSerializer.class)
public @interface StorageUrl {

    /**
     * 关联的 ObjectKey 源字段名。
     * 若为空，表示当前字段本身就是 ObjectKey，直接将其值转换为访问 URL 输出。
     */
    String source() default "";

    /**
     * 目标 Bucket（为空使用默认桶）
     */
    String bucket() default "";

    /**
     * 生成的 URL 类型
     */
    UrlType type() default UrlType.PUBLIC;

    /**
     * 签名有效时长（单位：秒）
     */
    long durationSeconds() default 7200;

    /**
     * 多图分隔符（默认逗号）
     */
    String delimiter() default ",";
}