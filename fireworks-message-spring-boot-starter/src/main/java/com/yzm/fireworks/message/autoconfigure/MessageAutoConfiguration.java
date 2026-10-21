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
import com.yzm.fireworks.redis.ratelimit.RateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
    @ConditionalOnProperty(prefix = "fireworks.message.channels.sms", name = "enabled", havingValue = "true")
    public AliyunSmsProvider aliyunSmsProvider(MessageProperties properties, ObjectMapper objectMapper) {
        return new AliyunSmsProvider(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fireworks.message.channels.sms", name = "enabled", havingValue = "true")
    public TencentSmsProvider tencentSmsProvider(MessageProperties properties) {
        return new TencentSmsProvider(properties);
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
}
