package com.yzm.fireworks.storage;

import com.yzm.fireworks.storage.advisor.StorageUrlModule;
import com.yzm.fireworks.storage.config.properties.StorageProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "fireworks.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    /**
     * 注册 @StorageUrl 序列化模块到 ObjectMapper。
     * 注解声明于 common，URL 解析逻辑位于 storage，通过 BeanSerializerModifier 在运行时绑定。
     */
    @Bean
    @ConditionalOnClass(Jackson2ObjectMapperBuilderCustomizer.class)
    public Jackson2ObjectMapperBuilderCustomizer storageUrlJackson2ObjectMapperBuilderCustomizer() {
        return builder -> builder.modules(new StorageUrlModule());
    }
}
