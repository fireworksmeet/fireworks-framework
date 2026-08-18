package com.yzm.fireworks.storage.orphan;

import com.yzm.fireworks.common.aop.AbstractAnnotationMetadataSource;
import com.yzm.fireworks.common.util.SpelUtil;
import com.yzm.fireworks.storage.model.annotation.AutoConfirmFile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.util.StringUtils;

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

        String bucketSpel = annotation.bucket();
        String objectKeySpel = annotation.objectKey();

        // SpelUtil.parse 对空表达式会抛异常，因此仅在表达式非空时解析。
        Expression bucketExpression = StringUtils.hasText(bucketSpel) ? SpelUtil.parse(bucketSpel) : null;
        Expression objectKeyExpression = StringUtils.hasText(objectKeySpel) ? SpelUtil.parse(objectKeySpel) : null;

        return AutoConfirmFileAttribute.builder()
                .bucket(bucketSpel)
                .objectKey(objectKeySpel)
                .bucketExpression(bucketExpression)
                .objectKeyExpression(objectKeyExpression)
                .build();
    }
}

