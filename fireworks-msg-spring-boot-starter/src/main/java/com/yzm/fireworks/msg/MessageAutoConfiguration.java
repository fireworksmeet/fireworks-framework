package com.yzm.fireworks.msg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.common.decorator.MdcTaskDecorator;
import com.yzm.fireworks.common.util.ThreadPoolUtil;
import com.yzm.fireworks.msg.core.ratelimit.RateLimiter;
import com.yzm.fireworks.msg.core.ratelimit.RedissonRateLimiter;
import com.yzm.fireworks.msg.core.sender.*;
import com.yzm.fireworks.msg.enums.SmsProvider;
import com.yzm.fireworks.msg.mapper.MessageRecordMapper;
import com.yzm.fireworks.msg.properties.MessageProperties;
import com.yzm.fireworks.msg.service.MessageDeduplicationService;
import com.yzm.fireworks.msg.service.MessagePushService;
import com.yzm.fireworks.msg.service.MessageRecordService;
import com.yzm.fireworks.msg.service.MessageRouterService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 消息推送自动配置
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MessageProperties.class)
@ConditionalOnProperty(prefix = "fireworks.message.push", name = "enabled", havingValue = "true")
@MapperScan("com.yzm.fireworks.msg.mapper")
public class MessageAutoConfiguration {

    private static final String MSG_ASYNC_EXECUTOR = "msgAsyncExecutor";

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(RedissonClient redissonClient) {
        return new RedissonRateLimiter(redissonClient);
    }

    @Bean
    public MessageRouterService messageRouterService(MessageProperties properties,
                                                     @Nullable WebSocketSender webSocketSender,
                                                     @Nullable SmsSender smsSender,
                                                     @Nullable EmailSender emailSender) {
        return new MessageRouterService(properties, webSocketSender, smsSender, emailSender);
    }

    /**
     * 消息推送服务
     *
     * <p>当 {@code async.enabled=true} 时注入异步线程池，否则传 null 降级同步执行。
     */
    @Bean
    public MessagePushService messagePushService(MessageRouterService routerService,
                                                 MessageDeduplicationService deduplicationService,
                                                 MessageRecordService recordService,
                                                 @Nullable @Qualifier(MSG_ASYNC_EXECUTOR) Executor msgAsyncExecutor) {
        return new MessagePushService(routerService, deduplicationService, recordService, msgAsyncExecutor);
    }

    @Bean
    public MessageRecordService messageRecordService(MessageRecordMapper messageRecordMapper) {
        return new MessageRecordService(messageRecordMapper);
    }

    @Bean
    public MessageDeduplicationService messageDeduplicationService(MessageProperties properties) {
        return new MessageDeduplicationService(properties);
    }

    /**
     * 消息异步发送线程池
     *
     * <p>仅在 {@code fireworks.message.push.async.enabled=true} 时注册（默认启用）。
     * 关闭后 {@link MessagePushService#sendAsync} 降级为在调用方线程同步执行。
     *
     * <p>拒绝策略使用 {@link ThreadPoolExecutor.CallerRunsPolicy}：
     * 队列和线程池均满时，在调用方线程直接同步执行，不丢消息、不抛异常，
     * 同时对调用方产生自然的背压效果。
     *
     * <p>使用 {@link MdcTaskDecorator} 装饰器传递 MDC 上下文（如 traceId）到异步线程。
     */
    @Bean(name = MSG_ASYNC_EXECUTOR)
    @ConditionalOnProperty(prefix = "fireworks.message.push.async", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public Executor msgAsyncExecutor(MessageProperties properties) {
        MessageProperties.AsyncConfig async = properties.getAsync();
        int corePoolSize = async.getCorePoolSize();
        int maxPoolSize = async.getMaxPoolSize();
        if (corePoolSize <= 0) {
            corePoolSize = Runtime.getRuntime().availableProcessors() + 1;
        }
        if (maxPoolSize <= 0) {
            maxPoolSize = Runtime.getRuntime().availableProcessors() << 1;
        }
        ThreadPoolTaskExecutor executor = ThreadPoolUtil.createThreadPoolTaskExecutor(
                corePoolSize,
                maxPoolSize,
                async.getKeepAliveSeconds(),
                async.getQueueCapacity(),
                async.getThreadNamePrefix(),
                new ThreadPoolExecutor.CallerRunsPolicy(),
                async.getAwaitTerminationSeconds(),
                new MdcTaskDecorator()
        );
        log.info("[MessagePush] 异步线程池初始化完成 — corePoolSize={}, maxPoolSize={}, queueCapacity={}, taskDecorator=MdcTaskDecorator",
                corePoolSize, maxPoolSize, async.getQueueCapacity());
        return executor;
    }

    @Bean
    @ConditionalOnProperty(prefix = "fireworks.message.push.email", name = "enabled", havingValue = "true")
    public EmailSender emailSender(JavaMailSender mailSender, TemplateEngine templateEngine,
                                   MessageProperties properties, RateLimiter rateLimiter) {
        return new EmailSender(mailSender, templateEngine, properties, rateLimiter);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fireworks.message.push.sms", name = "enabled", havingValue = "true")
    public SmsSender smsSender(MessageProperties properties, ObjectMapper objectMapper,
                               RateLimiter rateLimiter) throws Exception {
        SmsProvider provider = properties.getSms().getProvider();
        if (SmsProvider.ALIYUN.equals(provider)) {
            return new AliyunSmsSender(properties, objectMapper, rateLimiter);
        } else if (SmsProvider.TENCENT.equals(provider)) {
            return new TencentSmsSender(properties, rateLimiter);
        }
        throw new IllegalStateException(
                "Miss SMS provider configuration. Current supported providers: ALIYUN, TENCENT");
    }

    @Bean
    @ConditionalOnProperty(prefix = "fireworks.message.push.websocket", name = "enabled", havingValue = "true")
    public WebSocketSender webSocketSender(MessageProperties properties, RestClient.Builder restClientBuilder) {
        return new WebSocketSender(properties, restClientBuilder);
    }
}