package com.yzm.fireworks.export;

import com.yzm.fireworks.export.core.ExcelExporter;
import com.yzm.fireworks.export.core.ExcelExporterImpl;
import com.yzm.fireworks.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 导出器
     * <p>
     * 应用名在此处以 {@code @Value} 显式声明为方法参数，而非在
     * {@link ExcelExporterImpl} 内使用字段注入，使依赖全部经由构造器传入、风格统一。
     * StorageService 为可选依赖，未引入 storage 模块时注入 {@code null}。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelExporter excelExporter(ExportProperties exportProperties,
                                       @Autowired(required = false) StorageService storageService,
                                       @Value("${spring.application.name:fireworks-export}") String applicationName) {
        return new ExcelExporterImpl(exportProperties, storageService, applicationName);
    }
}