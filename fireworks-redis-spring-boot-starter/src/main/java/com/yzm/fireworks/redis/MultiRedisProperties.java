package com.yzm.fireworks.redis;

import lombok.Data;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "spring.data.redis.multi")
public class MultiRedisProperties {

    /**
     * 是否开启多数据源
     */
    private boolean enabled;
    /**
     * 默认数据源名称
     */
    private String primary;
    /**
     * 多个数据源配置
     */
    private Map<String, RedisProperties> datasource;

}