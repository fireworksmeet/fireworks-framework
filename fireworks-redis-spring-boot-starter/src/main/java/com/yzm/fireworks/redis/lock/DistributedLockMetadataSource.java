package com.yzm.fireworks.redis.lock;

import com.yzm.fireworks.common.aop.AbstractAnnotationMetadataSource;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.AnnotatedElement;

public class DistributedLockMetadataSource extends AbstractAnnotationMetadataSource<DistributedLockAttribute> {

    @Override
    protected DistributedLockAttribute findAnnotationMetadata(AnnotatedElement element) {
        // 这里原生支持了 Spring 的合并注解、元注解寻址
        DistributedLock annotation = AnnotatedElementUtils.findMergedAnnotation(element, DistributedLock.class);
        if (annotation == null) {
            return null;
        }

        // 将注解解析为 Attribute 对象并返回，交给父类缓存
        return DistributedLockAttribute.builder()
                .prefixKey(annotation.prefixKey())
                .key(annotation.key())
                .waitTime(annotation.waitTime())
                .unit(annotation.unit())
                .datasource(annotation.datasource())
                .build();
    }
}