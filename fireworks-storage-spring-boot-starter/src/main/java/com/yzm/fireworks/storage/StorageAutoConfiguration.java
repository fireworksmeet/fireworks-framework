package com.yzm.fireworks.storage;

import com.yzm.fireworks.storage.config.properties.StorageProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@ConditionalOnProperty(prefix = "fireworks.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {
}
