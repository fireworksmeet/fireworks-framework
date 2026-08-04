package com.yzm.fireworks.redis.lock;

import com.yzm.fireworks.common.util.SpelUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

import static com.yzm.fireworks.common.constants.StringPool.COLON;


/**
 * 分布式锁拦截器，只关注业务逻辑
 * <p>
 * 拦截带有 {@link DistributedLock} 注解的方法，自动加锁和释放锁。
 *
 * @author JYuan
 */
public class DistributedLockInterceptor implements MethodInterceptor {

    private final DistributedLockMetadataSource metadataSource;
    private final LockService lockService;

    public DistributedLockInterceptor(DistributedLockMetadataSource metadataSource, LockService lockService) {
        this.metadataSource = metadataSource;
        this.lockService = lockService;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Class<?> targetClass = invocation.getThis() != null ? invocation.getThis().getClass() : null;

        DistributedLockAttribute attribute = metadataSource.getMetadata(method, targetClass);
        if (attribute == null) {
            return invocation.proceed();
        }
        String lockKey = buildLockKey(method, invocation.getArguments(), attribute);

        // 根据注解中的 datasource 属性选择对应的 LockService
        LockService targetLockService = StringUtils.hasText(attribute.getDatasource())
                ? lockService.on(attribute.getDatasource())
                : lockService;

        return targetLockService.executeWithLock(lockKey, attribute.getWaitTime(), attribute.getUnit(), invocation::proceed);
    }

    private String buildLockKey(Method method, Object[] args, DistributedLockAttribute attribute) {
        String prefix = StringUtils.hasText(attribute.getPrefixKey())
                ? attribute.getPrefixKey()
                : SpelUtil.getMethodKey(method);
        if (!StringUtils.hasText(attribute.getKey())) {
            return prefix;
        }
        String suffix = SpelUtil.parseSpEl(method, args, attribute.getKey());
        return StringUtils.hasText(suffix) ? prefix + COLON + suffix : prefix;
    }
}