package com.yzm.fireworks.webfluxclient.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebClient / Reactor Netty 连接池配置
 *
 * <p>配置前缀：{@code fireworks.webflux-client}
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.webflux-client")
public class WebClientProperties {

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 3000;

    /**
     * 读取超时时间（毫秒）
     */
    private long readTimeout = 10000;

    /**
     * 写入超时时间（毫秒）
     */
    private long writeTimeout = 10000;

    /**
     * 连接池最大连接数
     */
    private int maxConnections = 500;

    /**
     * 等待获取连接的最大排队数
     * <p>当连接池满时，新请求会进入等待队列，超过此数量时直接拒绝（抛异常）。
     * 通常设为 maxConnections 的 2 倍。
     * <p>-1 表示无限制（不推荐生产使用）
     */
    private int pendingAcquireMaxCount = 1000;

    /**
     * 获取连接的最大等待时间（毫秒）
     */
    private long acquireTimeout = 1000;

    /**
     * 连接最大生命周期（毫秒）
     */
    private long maxLifeTime = 30000;

    /**
     * 连接最大空闲时间（毫秒）
     */
    private long maxIdleTime = 30000;

    /**
     * 定期驱逐空闲连接的时间间隔（毫秒）
     */
    private long evictInBackground = 60000;

    /**
     * 默认的 ObjectMapper 缓存大小（字节数）
     */
    private int maxInMemorySize = 10 * 1024 * 1024;

}
