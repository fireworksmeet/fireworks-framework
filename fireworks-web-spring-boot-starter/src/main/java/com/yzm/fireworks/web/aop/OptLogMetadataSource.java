package com.yzm.fireworks.web.aop;

import com.yzm.fireworks.api.annotation.OptLog;
import com.yzm.fireworks.common.aop.AbstractAnnotationMetadataSource;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.AnnotatedElement;

public class OptLogMetadataSource extends AbstractAnnotationMetadataSource<OptLogAttribute> {

    @Override
    protected OptLogAttribute findAnnotationMetadata(AnnotatedElement element) {
        // 这里原生支持了 Spring 的合并注解、元注解寻址
        OptLog annotation = AnnotatedElementUtils.findMergedAnnotation(element, OptLog.class);
        if (annotation == null) {
            return null;
        }

        // 将注解解析为 Attribute 对象并返回，交给父类缓存
        return OptLogAttribute.builder()
                .module(annotation.module())
                .type(annotation.type())
                .description(annotation.description())
                .recordArgs(annotation.recordArgs())
                .recordResult(annotation.recordResult())
                .build();
    }
}