package com.yzm.fireworks.export;

import com.yzm.fireworks.export.core.ExcelExporter;
import com.yzm.fireworks.export.core.ExcelExporterImpl;
import com.yzm.fireworks.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Excel 导出自动配置
 *
 * @author JYuan
 */
@AutoConfiguration
@EnableConfigurationProperties(ExportProperties.class)
public class ExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExcelExporter excelExporter(ExportProperties exportProperties, @Autowired(required = false) StorageService storageService) {
        return new ExcelExporterImpl(exportProperties, storageService);
    }
}