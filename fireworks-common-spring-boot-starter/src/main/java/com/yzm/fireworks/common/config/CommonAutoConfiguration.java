package com.yzm.fireworks.common.config;

import com.yzm.fireworks.common.decorator.MdcTaskDecorator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * 通用基础自动配置
 *
 * @author JYuan
 */
@AutoConfiguration
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
