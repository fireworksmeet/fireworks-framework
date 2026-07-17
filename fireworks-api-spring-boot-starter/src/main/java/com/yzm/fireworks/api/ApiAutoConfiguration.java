package com.yzm.fireworks.api;


import com.yzm.fireworks.api.enums.IOptionEnum;
import com.yzm.fireworks.api.enums.OptionEnumScanProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.HibernateValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.ArrayList;
import java.util.List;


/**
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(OptionEnumScanProperties.class)
@ComponentScan("com.yzm.fireworks.api")
public class ApiAutoConfiguration {

    @ConditionalOnMissingBean(Validator.class)
    @ConditionalOnProperty(name = "fireworks.api.validator.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public Validator validator() {
        try (ValidatorFactory validatorFactory = Validation
                .byProvider(HibernateValidator.class)
                .configure()
                .failFast(true)
                .buildValidatorFactory()) {
            return validatorFactory.getValidator();
        }
    }

    @Bean
    @ConditionalOnProperty(name = "fireworks.option-enum.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    @SuppressWarnings("unchecked")
    public List<Class<? extends IOptionEnum>> optionEnumClasses(OptionEnumScanProperties properties) throws ClassNotFoundException {
        List<String> scanPackages = properties.getScanPackages();
        if (scanPackages == null || scanPackages.isEmpty()) {
            log.warn("选项枚举扫描已启用，但未配置 scan-packages，请在配置中指定 fireworks.option-enum.scan-packages");
            return List.of();
        }

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(IOptionEnum.class));

        List<Class<? extends IOptionEnum>> result = new ArrayList<>();
        for (String scanPackage : scanPackages) {
            if (scanPackage == null || scanPackage.isBlank()) {
                continue;
            }
            for (var beanDefinition : scanner.findCandidateComponents(scanPackage)) {
                String className = beanDefinition.getBeanClassName();
                Class<?> clazz = Class.forName(className);
                if (clazz.isEnum()) {
                    result.add((Class<? extends IOptionEnum>) clazz);
                    log.debug("扫描到选项枚举: {}", clazz.getSimpleName());
                }
            }
        }
        log.info("选项枚举扫描完成，共扫描到 {} 个枚举", result.size());
        return result;
    }
}
