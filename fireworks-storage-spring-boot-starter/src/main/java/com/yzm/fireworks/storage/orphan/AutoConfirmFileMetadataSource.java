package com.yzm.fireworks.storage.orphan;

import com.yzm.fireworks.common.aop.AbstractAnnotationMetadataSource;
import com.yzm.fireworks.storage.model.annotation.AutoConfirmFile;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.AnnotatedElement;

/**
 * {@code @AutoConfirmFile} 注解元数据源：把注解解析为 {@link AutoConfirmFileAttribute}。
 * <p>
 * 继承 {@link AbstractAnnotationMetadataSource}，原生支持 Spring 的合并注解、元注解寻址，
 * 以及泛型桥接方法解析；解析结果由父类缓存，首次命中后后续 0 反射。
 */
public class AutoConfirmFileMetadataSource extends AbstractAnnotationMetadataSource<AutoConfirmFileAttribute> {

    @Override
    protected AutoConfirmFileAttribute findAnnotationMetadata(AnnotatedElement element) {
        AutoConfirmFile annotation = AnnotatedElementUtils.findMergedAnnotation(element, AutoConfirmFile.class);
        if (annotation == null) {
            return null;
        }
        return AutoConfirmFileAttribute.builder()
                .bucket(annotation.bucket())
                .objectKey(annotation.objectKey())
                .build();
    }
}
