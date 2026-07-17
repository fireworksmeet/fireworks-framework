package com.yzm.fireworks.common.config;

import com.yzm.fireworks.common.handler.IJackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 自动配置类
 * 自动注册 ObjectMapper 定制器，包括脱敏模块
 *
 * @author JYuan
 */
@AutoConfiguration
@ConditionalOnClass(Jackson2ObjectMapperBuilderCustomizer.class)
public class JacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IJackson2ObjectMapperBuilderCustomizer.class)
    public IJackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return new IJackson2ObjectMapperBuilderCustomizer();
    }
}
