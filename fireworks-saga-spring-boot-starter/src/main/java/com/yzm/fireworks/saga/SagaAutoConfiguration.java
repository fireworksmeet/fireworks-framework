package com.yzm.fireworks.saga;


import com.yzm.fireworks.saga.mapper.SagaLogMapper;
import com.yzm.fireworks.saga.service.SagaLogService;
import com.yzm.fireworks.saga.step.SagaCoordinator;
import com.yzm.fireworks.saga.step.SagaStepRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author JYuan
 */
@AutoConfiguration
@ComponentScan("com.yzm.fireworks.saga")
@EnableConfigurationProperties(SagaProperties.class)
@MapperScan("com.yzm.fireworks.saga.mapper")
public class SagaAutoConfiguration {

    @ConditionalOnMissingBean(SagaStepRegistry.class)
    @Bean
    public SagaStepRegistry getSagaStepRegistry(ApplicationContext context) {
        return new SagaStepRegistry(context);
    }

    @ConditionalOnMissingBean(SagaLogService.class)
    @Bean
    public SagaLogService getSagaLogService(SagaLogMapper sagaLogMapper, SagaProperties properties, SagaStepRegistry registry) {
        return new SagaLogService(sagaLogMapper, properties, registry);
    }

    @ConditionalOnMissingBean(SagaCoordinator.class)
    @Bean
    public SagaCoordinator getSagaCoordinator(SagaLogService sagaLogService) {
        return new SagaCoordinator(sagaLogService);
    }
}
