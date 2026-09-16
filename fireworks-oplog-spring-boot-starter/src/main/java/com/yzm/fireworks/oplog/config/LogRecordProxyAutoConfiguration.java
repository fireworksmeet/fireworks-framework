package com.yzm.fireworks.oplog.config;

import com.yzm.fireworks.oplog.annotation.EnableLogRecord;
import com.yzm.fireworks.oplog.service.ILogRecordService;
import com.yzm.fireworks.oplog.service.IOperatorGetService;
import com.yzm.fireworks.oplog.service.impl.DefaultLogRecordServiceImpl;
import com.yzm.fireworks.oplog.service.impl.DefaultOperatorGetServiceImpl;
import com.yzm.fireworks.oplog.support.aop.BeanFactoryLogRecordAdvisor;
import com.yzm.fireworks.oplog.support.aop.LogRecordInterceptor;
import com.yzm.fireworks.oplog.support.aop.LogRecordOperationSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author muzhantong
 */
@Configuration
@EnableConfigurationProperties({LogRecordProperties.class})
@Slf4j
public class LogRecordProxyAutoConfiguration implements ImportAware {

    private AnnotationAttributes enableLogRecord;


    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LogRecordOperationSource logRecordOperationSource() {
        return new LogRecordOperationSource();
    }

    @DependsOn("logRecordInterceptor")
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public BeanFactoryLogRecordAdvisor logRecordAdvisor(LogRecordInterceptor logRecordInterceptor) {
        BeanFactoryLogRecordAdvisor advisor = new BeanFactoryLogRecordAdvisor(logRecordOperationSource());
        advisor.setAdvice(logRecordInterceptor);
        advisor.setOrder(enableLogRecord.getNumber("order"));
        return advisor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LogRecordInterceptor logRecordInterceptor(LogRecordProperties logRecordProperties) {
        LogRecordInterceptor interceptor = new LogRecordInterceptor();
        interceptor.setLogRecordOperationSource(logRecordOperationSource());
        interceptor.setTenant(enableLogRecord.getString("tenant"));
        interceptor.setJoinTransaction(enableLogRecord.getBoolean("joinTransaction"));
        interceptor.setSkipUnrenderedLog(logRecordProperties.getSkipUnrenderedLog());
        return interceptor;
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

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.enableLogRecord = AnnotationAttributes.fromMap(
                importMetadata.getAnnotationAttributes(EnableLogRecord.class.getName(), false));
        if (this.enableLogRecord == null) {
            log.info("EnableLogRecord is not present on importing class");
        }
    }
}
