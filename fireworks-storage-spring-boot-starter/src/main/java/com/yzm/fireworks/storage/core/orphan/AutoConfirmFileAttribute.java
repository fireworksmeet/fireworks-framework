package com.yzm.fireworks.storage.core.orphan;

import lombok.Builder;
import lombok.Getter;
import org.springframework.expression.Expression;

/**
 * {@code @AutoConfirmFile} 注解元数据载体，由 {@link AutoConfirmFileMetadataSource} 解析并交给父类缓存。
 */
@Getter
@Builder
public class AutoConfirmFileAttribute {

    /**
     * 桶名 SpEL 表达式字符串（可为空）。
     */
    private final String bucket;

    /**
     * 对象名 SpEL 表达式字符串（可为空）。
     */
    private final String objectKey;

    /**
     * 预解析后的桶名 SpEL Expression 实例（可为 null）。
     */
    private final Expression bucketExpression;

    /**
     * 预解析后的对象名 SpEL Expression 实例（可为 null）。
     */
    private final Expression objectKeyExpression;
}

