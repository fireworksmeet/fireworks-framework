package com.yzm.fireworks.storage.orphan;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code @AutoConfirmFile} 注解元数据载体，由 {@link AutoConfirmFileMetadataSource} 解析并交给父类缓存。
 * <p>
 * 此处只保存注解上的表达式<b>原文</b>，表达式的解析与缓存由
 * {@code SpelEvaluator} 统一负责，避免在元数据中重复持有 {@code Expression}。
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
}
