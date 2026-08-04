package com.yzm.fireworks.common.aop;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodClassKey;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用注解元数据源基类 (借鉴 Spring TransactionAttributeSource 设计理念)
 *
 * <p>核心职责：
 * 1. 负责在类、接口、方法的继承树中寻找指定注解。
 * 2. 解决泛型擦除产生的 Bridge Method 问题。
 * 3. 缓存解析结果，确保目标方法在第一次被调用后，后续调用 0 反射。
 * 4. 解决缓存穿透问题（对于没有注解的方法，缓存一个空对象占位）。
 *
 * @param <T> 解析后的元数据对象类型
 */
public abstract class AbstractAnnotationMetadataSource<T> {

    /**
     * 元数据缓存：Key 为 Method + Class，Value 为解析后的属性对象
     */
    private final Map<MethodClassKey, Object> attributeCache = new ConcurrentHashMap<>(256);

    /**
     * 空对象占位符：防止未标注注解的方法每次调用都触发全量反射查找（防止缓存穿透）
     */
    private static final Object NULL_ATTRIBUTE = new Object();

    /**
     * 获取元数据（主入口，包含缓存逻辑）
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T getMetadata(@NonNull Method method, @Nullable Class<?> targetClass) {
        // 1. 构造与 Spring 行为完全一致的 CacheKey
        Class<?> keyClass = (targetClass != null ? targetClass : method.getDeclaringClass());
        MethodClassKey cacheKey = new MethodClassKey(method, keyClass);

        // 2. 查缓存
        Object cached = this.attributeCache.get(cacheKey);
        if (cached != null) {
            return (cached == NULL_ATTRIBUTE ? null : (T) cached);
        }

        // 3. 缓存未命中，执行深度查找逻辑
        T attribute = computeMetadata(method, targetClass);

        // 4. 放入缓存
        this.attributeCache.put(cacheKey, attribute != null ? attribute : NULL_ATTRIBUTE);
        
        return attribute;
    }

    /**
     * 执行层级与桥接方法的查找逻辑
     */
    @Nullable
    private T computeMetadata(Method method, @Nullable Class<?> targetClass) {
        // 1. 核心大招：AopUtils 一步到位！
        // 内部已完成：CGLIB 剥离 -> 接口方法转实现类方法 -> resolveBridgeMethod(泛型桥接解析)
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        // 2. 检查：具体实现类的具体方法
        T attr = findAnnotationMetadata(specificMethod);
        if (attr != null) {
            return attr;
        }

        // 3. 检查：具体实现类上的注解（本身）
        attr = findAnnotationMetadata(specificMethod.getDeclaringClass());
        if (attr != null) {
            return attr;
        }

        // 4. 兜底检查：原始接口 / 抽象类
        // 如果 specificMethod 和 method 不同，说明 method 是接口/父类上的原方法
        // 既然实现类上没标注解，那就回头看看老祖宗（接口/父类）身上有没有标
        if (specificMethod != method) {
            // 检查：原始接口方法
            attr = findAnnotationMetadata(method);
            if (attr != null) {
                return attr;
            }
            // 检查：原始接口 本身
            return findAnnotationMetadata(method.getDeclaringClass());
        }

        return null;
    }

    /**
     * 留给子类实现：如何从具体的 Element (Method 或 Class) 上提取并构造你的元数据对象
     * 建议子类使用 AnnotatedElementUtils.findMergedAnnotation 提取
     * 
     * @param element Method 或 Class
     * @return 你的元数据对象，没有对应注解则返回 null
     */
    @Nullable
    protected abstract T findAnnotationMetadata(AnnotatedElement element);
}