package com.yzm.fireworks.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.outbox.api.OutboxHandler;
import com.yzm.fireworks.outbox.api.OutboxHandlerRegistry;
import com.yzm.fireworks.outbox.api.OutboxService;
import com.yzm.fireworks.outbox.properties.OutboxProperties;
import com.yzm.fireworks.outbox.repository.JdbcOutboxEventRepository;
import com.yzm.fireworks.outbox.repository.OutboxEventRepository;
import com.yzm.fireworks.outbox.retry.ExponentialOutboxRetryPolicy;
import com.yzm.fireworks.outbox.retry.OutboxRetryPolicy;
import com.yzm.fireworks.outbox.service.DefaultOutboxService;
import com.yzm.fireworks.outbox.worker.OutboxDispatcher;
import com.yzm.fireworks.outbox.worker.OutboxLane;
import com.yzm.fireworks.outbox.worker.OutboxLaneFactory;
import com.yzm.fireworks.outbox.worker.OutboxRecoveryWorker;
import com.yzm.fireworks.outbox.worker.OutboxWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

/**
 * Outbox 自动配置。
 *
 * <p>默认 {@code fireworks.outbox.enabled=true}：引入依赖即生效。投递 Worker 与恢复任务可分别关闭，
 * 便于在测试环境只入库不投递。
 *
 * <p><b>依赖边界</b>：只依赖 Spring JDBC 与一个主 {@link DataSource}，<b>不依赖任何 ORM、也不按数据库分方言</b>。
 * 建表脚本按统一列类型提供（{@code event_id VARCHAR(32)}、{@code payload TEXT}），
 * 因此同一套 SQL 在 PostgreSQL 12+ 与 MySQL 8.0.1+ 上都能执行；两者都支持
 * {@code FOR UPDATE SKIP LOCKED}，领取语义不需要分叉。
 *
 * <p>关键扩展点均以 {@link ConditionalOnMissingBean} 暴露：
 * {@link OutboxEventRepository}、{@link OutboxHandlerRegistry}、
 * {@link OutboxRetryPolicy}、{@link OutboxService}、{@link OutboxDispatcher}。
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(prefix = "fireworks.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnSingleCandidate(DataSource.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventRepository outboxEventRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                       PlatformTransactionManager transactionManager) {
        return new JdbcOutboxEventRepository(jdbcTemplate, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxHandlerRegistry outboxHandlerRegistry(List<OutboxHandler> handlers) {
        OutboxHandlerRegistry registry = new OutboxHandlerRegistry(handlers);
        // 注册表不对外暴露内部映射，注册情况在这里输出，便于启动时确认"哪些事件有人处理"
        List<String> eventTypes = handlers == null ? List.of()
                : handlers.stream().map(OutboxHandler::eventType).sorted().toList();
        log.info("[Outbox] 已注册 {} 个 Handler：{}", eventTypes.size(), eventTypes);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRetryPolicy outboxRetryPolicy(OutboxProperties properties) {
        return new ExponentialOutboxRetryPolicy(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxEventRepository outboxEventRepository,
                                       ObjectMapper objectMapper,
                                       OutboxProperties properties) {
        return new DefaultOutboxService(outboxEventRepository, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxDispatcher outboxDispatcher(OutboxEventRepository outboxEventRepository,
                                             OutboxHandlerRegistry handlerRegistry,
                                             OutboxRetryPolicy retryPolicy,
                                             OutboxProperties properties) {
        return new OutboxDispatcher(outboxEventRepository, handlerRegistry, retryPolicy, properties);
    }

    /**
     * 投递 Worker：按 {@code fireworks.outbox.lanes} 拆成多个道，每道独立领取条件与线程池。
     * <p>未配置分道时只有一个默认道，行为与之前一致。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fireworks.outbox.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxWorker outboxWorker(OutboxEventRepository outboxEventRepository,
                                     OutboxDispatcher outboxDispatcher,
                                     OutboxProperties properties,
                                     ObjectProvider<TaskDecorator> taskDecorators,
                                     @Value("${spring.application.name:application}") String applicationName) {
        List<OutboxLane> lanes = OutboxLaneFactory.build(properties, taskDecorators.getIfUnique());
        log.info("[Outbox] 投递分道: {}", lanes.stream()
                .map(lane -> "%s(batch=%d, concurrency=%d)".formatted(
                        lane.name(), lane.batchSize(), lane.executor().getCorePoolSize()))
                .toList());
        return new OutboxWorker(outboxEventRepository, outboxDispatcher, properties, lanes, applicationName);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fireworks.outbox.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRecoveryWorker outboxRecoveryWorker(OutboxEventRepository outboxEventRepository,
                                                     OutboxProperties properties) {
        return new OutboxRecoveryWorker(outboxEventRepository, properties);
    }
}
