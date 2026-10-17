package com.yzm.fireworks.api;


import com.yzm.fireworks.api.enums.CodeEnum;
import com.yzm.fireworks.api.enums.ExposedEnum;
import com.yzm.fireworks.api.enums.ExposedEnumScanProperties;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;


/**
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ExposedEnumScanProperties.class)
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

    /**
     * 扫描标注 {@link ExposedEnum} 的枚举，供业务侧组装前端字典 / 选项接口。
     * <p>
     * 【为什么按注解筛选】"有业务编码"与"要对外暴露"是两个正交维度：前者由 {@link CodeEnum} 保证，
     * 几乎所有业务枚举都需要；后者只适用于一部分枚举（内部流转状态不该出现在前端字典里）。
     * 用注解表达后者，避免为此多造一个接口。
     * <p>
     * 【为什么仍然需要包路径】classpath 扫描必须指定起点：注解决定<b>选谁</b>，
     * 包路径决定<b>从哪找</b>，二者不可互替。未配置 {@code scan-packages} 时取启动类所在包。
     */
    @Bean
    @ConditionalOnProperty(name = "fireworks.exposed-enum.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    @SuppressWarnings("unchecked")
    public List<Class<? extends CodeEnum<?>>> exposedEnumClasses(ExposedEnumScanProperties properties,
                                                                 BeanFactory beanFactory) throws ClassNotFoundException {
        List<String> scanPackages = properties.getScanPackages();
        if (scanPackages == null || scanPackages.isEmpty()) {
            scanPackages = defaultScanPackages(beanFactory);
            if (scanPackages.isEmpty()) {
                log.warn("暴露枚举扫描已启用，但无法推导扫描包，请配置 fireworks.exposed-enum.scan-packages");
                return List.of();
            }
            log.debug("未配置 scan-packages，使用启动类所在包: {}", scanPackages);
        }

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ExposedEnum.class));

        List<Class<? extends CodeEnum<?>>> result = new ArrayList<>();
        for (String scanPackage : scanPackages) {
            if (scanPackage == null || scanPackage.isBlank()) {
                continue;
            }
            for (var beanDefinition : scanner.findCandidateComponents(scanPackage)) {
                Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
                if (!clazz.isEnum() || !CodeEnum.class.isAssignableFrom(clazz)) {
                    // 契约缺失时立即暴露，而不是等到业务侧遍历字典时抛 NoSuchMethodException
                    log.warn("@ExposedEnum 标注在非 CodeEnum 枚举上，已跳过: {}", clazz.getName());
                    continue;
                }
                result.add((Class<? extends CodeEnum<?>>) clazz);
                log.debug("扫描到暴露枚举: {}", clazz.getSimpleName());
            }
        }
        log.info("暴露枚举扫描完成，共扫描到 {} 个枚举", result.size());
        return result;
    }

    /**
     * 启动类所在包；非 Spring Boot 环境（无 {@code @AutoConfigurationPackage}）返回空列表，
     * 由调用方给出配置缺失的告警。
     */
    private List<String> defaultScanPackages(BeanFactory beanFactory) {
        try {
            return AutoConfigurationPackages.get(beanFactory);
        } catch (IllegalStateException e) {
            return List.of();
        }
    }
}
