package com.yzm.fireworks.export;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Excel 导出自动配置
 *
 * @author JYuan
 */
@AutoConfiguration
@EnableConfigurationProperties(ExportProperties.class)
@EnableScheduling
public class ExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExportService exportService(ExportProperties exportProperties) {
        return new ExportServiceImpl(exportProperties);
    }
}