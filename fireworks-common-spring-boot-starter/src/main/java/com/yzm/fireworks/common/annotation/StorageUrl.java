package com.yzm.fireworks.common.annotation;

import com.yzm.fireworks.common.enums.UrlType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 对象存储访问 URL 注解。
 *
 * <p>标注在实体 / DTO 字段上，序列化时由存储模块的 {@code StorageUrlJsonSerializer}
 * 将 ObjectKey 自动转换为可访问的 URL，免去业务层手动拼接 URL。</p>
 *
 * <p>该注解位于 common 模块，业务分层中的 api 层即可直接使用（无需依赖 storage 模块）。
 * 实际 URL 解析由 storage 模块在运行时通过 {@code StorageUrlModule} 绑定生效；
 * 若应用未引入 storage 模块，字段将原样输出。</p>
 *
 * @author JYuan
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
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
