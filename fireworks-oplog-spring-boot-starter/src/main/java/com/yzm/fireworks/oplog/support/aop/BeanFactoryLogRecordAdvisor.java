package com.yzm.fireworks.oplog.support.aop;

import com.yzm.fireworks.common.aop.MetadataSourcePointcut;
import com.yzm.fireworks.oplog.beans.LogRecordOps;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;

import java.util.Collection;

/**
 * 操作日志切面装配器
 * <p>
 * 复用 common 的 {@link MetadataSourcePointcut} 作为切点，
 * 将 {@code LogRecordInterceptor} 织入标注了 {@code @LogRecord} 的方法。
 * <p>
 * 继承 {@link AbstractBeanFactoryPointcutAdvisor} 而非普通 Advisor，是为了支持
 * <b>Advice 懒加载</b>（仅持有 Bean 名称，首次调用时才从容器获取），
 * 避免与拦截器形成循环依赖。
 *
 * @author mzt.
 */
public class BeanFactoryLogRecordAdvisor extends AbstractBeanFactoryPointcutAdvisor {

    private final MetadataSourcePointcut<Collection<LogRecordOps>> pointcut;

    public BeanFactoryLogRecordAdvisor(LogRecordOperationSource operationSource) {
        this.pointcut = new MetadataSourcePointcut<>(operationSource);
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }
}
