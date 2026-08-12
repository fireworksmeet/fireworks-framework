package com.yzm.fireworks.storage.orphan;

import com.yzm.fireworks.common.aop.MetadataSourcePointcut;
import com.yzm.fireworks.redis.RedisUtil;
import com.yzm.fireworks.storage.service.StorageService;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 孤儿文件清理自动装配。
 * <p>
 * 提供基于 Redis ZSet 的 {@link OrphanRegistry}（默认实现 {@link RedisOrphanRegistry}）、
 * {@link OrphanFileGuard} 门面与 {@link OrphanFileCleaner} 清理器。仅在存储模块启用、且存在
 * {@link StorageService} 与 Redis 环境（存在 {@link RedisUtil} / {@link StringRedisTemplate}）时生效。
 * <p>
 * {@code @AutoConfirmFile} 声明式自动确认采用与 OptLog / 分布式锁一致的
 * <b>{@code MetadataSource + Advisor}</b> 三件套模式：
 * {@link AutoConfirmFileMetadataSource}（解析并缓存注解元数据）→ {@link AutoConfirmFileInterceptor}（业务逻辑）
 * → {@link AbstractBeanFactoryPointcutAdvisor}（绑定 Pointcut）。
 * <p>
 * <b>框架不调度任何定时任务</b>：由业务侧通过 XXL-Job、PowerJob 或 Spring {@code @Scheduled} 等
 * 调度器周期性调用 {@link OrphanFileCleaner#cleanExpired()}。
 * <p>
 * 若需替换待确认注册表实现（如使用独立的 Redis 键、换用其他存储），实现 {@link OrphanRegistry} 后
 * 声明同名类型的 @Bean，即可被 {@link ConditionalOnMissingBean} 识别并自动取代默认实现。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fireworks.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "fireworks.storage.orphan-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OrphanCleanupProperties.class)
public class OrphanFileAutoConfiguration {


    @Bean
    @ConditionalOnClass(RedisUtil.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(OrphanRegistry.class)
    public OrphanRegistry pendingFileRegistry(OrphanCleanupProperties properties) {
        return new RedisOrphanRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OrphanRegistry.class)
    public OrphanFileGuard orphanFileGuard(OrphanRegistry registry) {
        return new OrphanFileGuard(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({StorageService.class, OrphanRegistry.class})
    public OrphanFileCleaner orphanFileCleaner(OrphanRegistry registry, StorageService storageService,
                                               OrphanCleanupProperties properties) {
        return new OrphanFileCleaner(registry, storageService, properties);
    }

    // ── @AutoConfirmFile 声明式自动确认（MetadataSource + Advisor 三件套） ──────────

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass({TransactionSynchronizationManager.class, MethodInterceptor.class})
    @ConditionalOnBean(OrphanFileGuard.class)
    public AutoConfirmFileMetadataSource autoConfirmFileMetadataSource() {
        return new AutoConfirmFileMetadataSource();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass({TransactionSynchronizationManager.class, MethodInterceptor.class})
    @ConditionalOnBean(OrphanFileGuard.class)
    public AutoConfirmFileInterceptor autoConfirmFileInterceptor(
            AutoConfirmFileMetadataSource metadataSource,
            OrphanFileGuard orphanFileGuard,
            OrphanCleanupProperties properties) {
        return new AutoConfirmFileInterceptor(metadataSource, orphanFileGuard, properties);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass({TransactionSynchronizationManager.class, MethodInterceptor.class})
    @ConditionalOnBean(OrphanFileGuard.class)
    public AbstractBeanFactoryPointcutAdvisor autoConfirmFileAdvisor(
            AutoConfirmFileInterceptor interceptor,
            AutoConfirmFileMetadataSource metadataSource) {
        AbstractBeanFactoryPointcutAdvisor advisor = new AbstractBeanFactoryPointcutAdvisor() {
            private final Pointcut pointcut = new MetadataSourcePointcut<>(metadataSource);

            @Override
            @NonNull
            public Pointcut getPointcut() {
                return pointcut;
            }
        };
        advisor.setAdvice(interceptor);
        // 确认逻辑需在事务提交之后执行，使用最低优先级（最后执行），保证事务已提交。
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }
}
