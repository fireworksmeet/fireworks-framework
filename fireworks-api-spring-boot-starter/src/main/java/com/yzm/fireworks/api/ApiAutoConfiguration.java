package com.yzm.fireworks.api;


import com.yzm.fireworks.api.enums.IOptionEnum;
import com.yzm.fireworks.api.enums.OptionEnumScanProperties;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
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

    /**
     * 开启校验快速失败：遇到第一个约束违规即返回，不再校验剩余字段，以减少校验开销。
     * <p>
     * 通过 Spring Boot 官方的 {@link ValidationConfigurationCustomizer} 扩展点实现，
     * 由 {@code ValidationAutoConfiguration} 创建的 {@code LocalValidatorFactoryBean}
     * 自动应用该定制，因此：
     * <ul>
     *     <li>不需要自行创建 {@code ValidatorFactory}，其生命周期交由 Spring 容器管理</li>
     *     <li>不会与 {@code ValidationAutoConfiguration} 的 {@code Validator} Bean
     *         产生 {@code @ConditionalOnMissingBean} 竞争，避免快速失败是否生效取决于
     *         自动配置处理顺序的问题</li>
     * </ul>
     * 注意该配置<b>全局生效</b>，会影响所有 {@code @Valid} 校验的行为。
     */
    @Bean
    @ConditionalOnProperty(name = "fireworks.api.validator.fail-fast", havingValue = "true", matchIfMissing = true)
    public ValidationConfigurationCustomizer failFastValidationCustomizer() {
        return configuration -> {
            // failFast 是 Hibernate Validator 的扩展能力，不在 jakarta.validation 标准接口上，
            // 因此需要先转换为 HibernateValidatorConfiguration 再调用
            if (configuration instanceof HibernateValidatorConfiguration hibernateConfiguration) {
                hibernateConfiguration.failFast(true);
            } else {
                log.warn("当前校验实现不是 Hibernate Validator，快速失败配置未生效，实际实现：{}",
                        configuration.getClass().getName());
            }
        };
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
