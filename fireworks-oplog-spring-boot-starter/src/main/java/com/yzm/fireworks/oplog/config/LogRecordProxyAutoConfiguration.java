package com.yzm.fireworks.oplog.config;

import com.yzm.fireworks.common.aop.MetadataSourcePointcut;
import com.yzm.fireworks.oplog.service.ILogRecordService;
import com.yzm.fireworks.oplog.service.IOperatorGetService;
import com.yzm.fireworks.oplog.service.impl.DefaultLogRecordServiceImpl;
import com.yzm.fireworks.oplog.service.impl.DefaultOperatorGetServiceImpl;
import com.yzm.fireworks.oplog.support.aop.LogRecordInterceptor;
import com.yzm.fireworks.oplog.support.aop.LogRecordOperationSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * 操作日志自动配置。
 *
 * <p>只需引入依赖 + 配置 {@code fireworks.oplog.*}，不需要任何 {@code @EnableXxx} 注解。
 *
 * @author muzhantong
 */
@AutoConfiguration
@EnableConfigurationProperties(LogRecordProperties.class)
@ConditionalOnProperty(prefix = "fireworks.oplog", name = "enabled", havingValue = "true", matchIfMissing = true)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Slf4j
public class LogRecordProxyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LogRecordOperationSource logRecordOperationSource() {
        return new LogRecordOperationSource();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LogRecordInterceptor logRecordInterceptor(LogRecordOperationSource logRecordOperationSource,
                                                     LogRecordProperties properties,
                                                     BeanFactory beanFactory) {
        LogRecordInterceptor interceptor = new LogRecordInterceptor(beanFactory);
        interceptor.setLogRecordOperationSource(logRecordOperationSource);
        interceptor.setTenant(properties.getTenant());
        interceptor.setJoinTransaction(properties.isJoinTransaction());
        return interceptor;
    }

    /**
     * 把 {@link LogRecordInterceptor} 织入标注了 {@code @LogRecord} 的方法。
     *
     * <p>用标准 {@link DefaultPointcutAdvisor} 即可：切点是
     * {@link MetadataSourcePointcut}，通知是拦截器实例，两者之间没有循环依赖，
     * 也不需要“按 Bean 名懒加载 Advice”。
     */
    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DefaultPointcutAdvisor logRecordAdvisor(LogRecordOperationSource logRecordOperationSource,
                                                   LogRecordInterceptor logRecordInterceptor,
                                                   LogRecordProperties properties) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
                new MetadataSourcePointcut<>(logRecordOperationSource), logRecordInterceptor);
        advisor.setOrder(properties.getOrder());
        return advisor;
    }

    @Bean
    @ConditionalOnMissingBean(IOperatorGetService.class)
    @Role(BeanDefinition.ROLE_APPLICATION)
    public IOperatorGetService operatorGetService() {
        return new DefaultOperatorGetServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(ILogRecordService.class)
    @Role(BeanDefinition.ROLE_APPLICATION)
    public ILogRecordService recordService() {
        return new DefaultLogRecordServiceImpl();
    }
}
