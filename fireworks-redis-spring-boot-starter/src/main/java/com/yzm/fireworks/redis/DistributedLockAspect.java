package com.yzm.fireworks.redis;

import com.yzm.fireworks.common.util.SpelUtil;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

import static com.yzm.fireworks.common.constants.StringPool.COLON;


/**
 * 分布式锁切面
 *
 * <p>拦截带有 {@link DistributedLock} 注解的方法，自动加锁和释放锁。
 *
 * <p><b>执行顺序</b>：实现 {@link PriorityOrdered} 确保在所有实现普通 {@link org.springframework.core.Ordered}
 * 的 Advisor（包括 {@code @Transactional}）之前执行，保证锁在事务外层，
 * 避免事务提交前锁已释放导致的并发问题。
 *
 * @author JYuan
 */
public class DistributedLockAspect extends StaticMethodMatcherPointcutAdvisor
        implements MethodInterceptor, PriorityOrdered {

    private final LockService lockService;

    public DistributedLockAspect(LockService lockService) {
        this.setAdvice(this);
        this.lockService = lockService;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(method, DistributedLock.class);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object target = invocation.getThis();
        Class<?> targetClass = target != null ? target.getClass() : null;
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);

        // matches() 已保证注解存在，此处直接用 AnnotatedElementUtils 取合并后的注解
        DistributedLock annotation = AnnotatedElementUtils.findMergedAnnotation(specificMethod, DistributedLock.class);
        String lockKey = buildLockKey(specificMethod, invocation.getArguments(), annotation);

        // 根据注解中的 datasource 属性选择对应的 LockService
        LockService targetLockService = StringUtils.hasText(annotation.datasource())
                ? lockService.on(annotation.datasource())
                : lockService;

        return targetLockService.executeWithLock(lockKey, annotation.waitTime(), annotation.unit(), invocation::proceed);
    }

    private String buildLockKey(Method method, Object[] args, DistributedLock annotation) {
        String prefix = StringUtils.hasText(annotation.prefixKey())
                ? annotation.prefixKey()
                : SpelUtil.getMethodKey(method);
        if (!StringUtils.hasText(annotation.key())) {
            return prefix;
        }
        String suffix = SpelUtil.parseSpEl(method, args, annotation.key());
        return StringUtils.hasText(suffix) ? prefix + COLON + suffix : prefix;
    }
}