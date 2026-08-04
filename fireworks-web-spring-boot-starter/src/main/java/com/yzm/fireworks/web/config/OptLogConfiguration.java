package com.yzm.fireworks.web.config;

import com.yzm.fireworks.api.annotation.OptLog;
import com.yzm.fireworks.common.aop.MetadataSourcePointcut;
import com.yzm.fireworks.web.aop.OptLogInterceptor;
import com.yzm.fireworks.web.aop.OptLogMetadataSource;
import com.yzm.fireworks.web.service.OptLogService;
import com.yzm.fireworks.web.spi.OptLogOperatorProvider;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;

/**
 * 操作日志配置类
 *
 * @author JYuan
 */
public class OptLogConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public OptLogMetadataSource optLogMetadataSource() {
        return new OptLogMetadataSource();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public OptLogInterceptor optLogInterceptor(
            OptLogMetadataSource optLogMetadataSource,
            @Autowired(required = false) OptLogOperatorProvider operatorProvider,
            @Autowired(required = false) OptLogService optLogService) {
        return new OptLogInterceptor(optLogMetadataSource, operatorProvider, optLogService);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DefaultBeanFactoryPointcutAdvisor optLogAdvisor(OptLogInterceptor interceptor) {
        DefaultBeanFactoryPointcutAdvisor advisor = new DefaultBeanFactoryPointcutAdvisor();
        advisor.setPointcut(AnnotationMatchingPointcut.forMethodAnnotation(OptLog.class));
        advisor.setAdvice(interceptor);
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AbstractBeanFactoryPointcutAdvisor optLogAdvisor(
            OptLogInterceptor optLogInterceptor,
            OptLogMetadataSource optLogMetadataSource) {

        AbstractBeanFactoryPointcutAdvisor advisor = new AbstractBeanFactoryPointcutAdvisor() {
            // 构建自定义的 Pointcut
            private final Pointcut pointcut = new MetadataSourcePointcut<>(optLogMetadataSource);

            @Override
            @NonNull
            public Pointcut getPointcut() {
                return this.pointcut;
            }
        };

        advisor.setAdvice(optLogInterceptor);
        // 如果需要在事务之后记录日志，确保 Order 值比事务的大（LOWEST_PRECEDENCE 通常能满足）
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }
}