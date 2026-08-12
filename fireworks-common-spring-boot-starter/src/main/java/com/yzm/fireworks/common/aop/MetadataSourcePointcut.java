package com.yzm.fireworks.common.aop;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 通用元数据切点
 * 
 * <p>只要 MetadataSource 能够解析出元数据，就认为 Pointcut 匹配成功。
 */
public class MetadataSourcePointcut<T> extends StaticMethodMatcherPointcut {

    private final AbstractAnnotationMetadataSource<T> metadataSource;

    public MetadataSourcePointcut(AbstractAnnotationMetadataSource<T> metadataSource) {
        Objects.requireNonNull(metadataSource, "MetadataSource must not be null");
        this.metadataSource = metadataSource;
    }

    @Override
    @NonNull
    public ClassFilter getClassFilter() {
        return ClassFilter.TRUE;
    }

    @Override
    public boolean matches(@NonNull Method method, @Nullable Class<?> targetClass) {
        // 如果能拿到元数据，说明标注了注解，予以拦截
        return this.metadataSource.getMetadata(method, targetClass) != null;
    }
}