package com.yzm.fireworks.message.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.message.api.MessageListener;
import com.yzm.fireworks.message.api.MessageProvider;
import com.yzm.fireworks.message.api.MessageSender;
import com.yzm.fireworks.message.channel.email.SmtpEmailProvider;
import com.yzm.fireworks.message.channel.realtime.NchanProvider;
import com.yzm.fireworks.message.channel.sms.AliyunSmsProvider;
import com.yzm.fireworks.message.channel.sms.TencentSmsProvider;
import com.yzm.fireworks.message.properties.MessageProperties;
import com.yzm.fireworks.message.support.DefaultMessageSender;
import com.yzm.fireworks.message.support.MessageProviderRegistry;
import com.yzm.fireworks.message.template.TemplateRenderer;
import com.yzm.fireworks.message.template.ThymeleafTemplateRenderer;
import com.yzm.fireworks.common.ratelimit.RateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;

import java.util.List;

/**
 * 消息模块自动配置。
 *
 * @author JYuan
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fireworks.message", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessageProperties.class)
public class MessageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageProviderRegistry messageProviderRegistry(List<MessageProvider<?>> providers,
                                                           MessageProperties properties) {
        return new MessageProviderRegistry(providers, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageSender messageSender(MessageProviderRegistry registry,
                                       ObjectProvider<RateLimiter> rateLimiter,
                                       MessageProperties properties,
                                       ObjectProvider<MessageListener> listeners) {
        return new DefaultMessageSender(
                registry,
                rateLimiter.getIfAvailable(),
                properties,
                listeners.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(TemplateEngine.class)
    public TemplateRenderer templateRenderer(ObjectProvider<TemplateEngine> templateEngine) {
        return new ThymeleafTemplateRenderer(templateEngine.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fireworks.message.channels.email", name = "enabled", havingValue = "true")
    public SmtpEmailProvider smtpEmailProvider(JavaMailSender mailSender,
                                               ObjectProvider<TemplateRenderer> templateRenderer,
                                               MessageProperties properties,
                                               @Value("${spring.mail.username:}") String username) {
        return new SmtpEmailProvider(mailSender, templateRenderer.getIfAvailable(), properties, username);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fireworks.message.channels.realtime", name = "enabled", havingValue = "true")
    public NchanProvider nchanProvider(MessageProperties properties, RestClient.Builder restClientBuilder) {
        return new NchanProvider(properties, restClientBuilder);
    }

    /**
     * 阿里云短信：classpath 上存在阿里云 SDK 时才装配。
     *
     * <p>SDK 在 pom 中是 {@code optional}，不传递给业务——<b>引入哪个 SDK 就装配哪个实现</b>；
     * 两个都引入时，由 {@code fireworks.message.channels.sms.provider} 决定用哪个。
     * <p>用 {@code name} 而不是类字面量：SDK 缺失时条件直接不成立，避免加载 Provider 类。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.aliyun.dysmsapi20170525.Client")
    @ConditionalOnProperty(prefix = "fireworks.message.channels.sms", name = "enabled", havingValue = "true")
    static class AliyunSmsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "fireworks.message.channels.sms", name = "provider",
                havingValue = "ALIYUN", matchIfMissing = true)
        AliyunSmsProvider aliyunSmsProvider(MessageProperties properties, ObjectMapper objectMapper) {
            return new AliyunSmsProvider(properties, objectMapper);
        }
    }

    /**
     * 腾讯云短信：classpath 上存在腾讯云 SDK 时才装配。规则同 {@link AliyunSmsConfiguration}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.tencentcloudapi.sms.v20210111.SmsClient")
    @ConditionalOnProperty(prefix = "fireworks.message.channels.sms", name = "enabled", havingValue = "true")
    static class TencentSmsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "fireworks.message.channels.sms", name = "provider",
                havingValue = "TENCENT", matchIfMissing = true)
        TencentSmsProvider tencentSmsProvider(MessageProperties properties) {
            return new TencentSmsProvider(properties);
        }
    }
}
