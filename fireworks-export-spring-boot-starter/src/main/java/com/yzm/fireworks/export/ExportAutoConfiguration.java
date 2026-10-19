package com.yzm.fireworks.export;

import com.yzm.fireworks.export.core.DefaultExcelExporter;
import com.yzm.fireworks.export.core.ExcelExporter;
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
     * {@link DefaultExcelExporter} 内使用字段注入，使依赖全部经由构造器传入、风格统一。
     * 文件落点不在此装配：由调用方传入 {@code ExportFileStore}，本模块不依赖任何存储实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelExporter excelExporter(ExportProperties exportProperties,
                                       @Value("${spring.application.name:fireworks-export}") String applicationName) {
        return new DefaultExcelExporter(exportProperties, applicationName);
    }
}