package com.yzm.fireworks.webfluxclient.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Reactor Netty 连接池配置。
 *
 * <p>配置前缀：{@code fireworks.webflux-client}。只包含 <b>Spring Boot 未提供</b>的池化参数；
 * connect / read 超时请使用 {@code spring.http.reactiveclient.*}，编解码缓冲上限请使用
 * {@code spring.codec.max-in-memory-size}。
 *
 * <pre>
 * fireworks:
 *   webflux-client:
 *     max-connections: 500           # 连接池上限
 *     pending-acquire-max-count: 1000 # 池满时允许排队的请求数，-1 为不限
 *     acquire-timeout: 1s            # 排队等待上限
 *     max-life-time: 30m             # 连接自建立起的总存活时间
 *     max-idle-time: 30s             # 空闲多久被回收
 *     evict-in-background: 60s       # 后台驱逐检查间隔
 * </pre>
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.webflux-client")
public class WebClientProperties {

    /**
     * 连接池最大连接数。
     * <p>推导方式与 HC5 一致：{@code ≥ 峰值QPS × 平均响应时间(秒) × 1.5}。
     */
    private int maxConnections = 500;

    /**
     * 池满时允许排队的请求数，超过即直接拒绝（{@code PoolAcquirePendingLimitException}）。
     * <p>{@code -1} 表示不限，生产不建议。
     */
    private int pendingAcquireMaxCount = 1000;

    /**
     * 排队获取连接的最长等待时间，超时抛 {@code PoolAcquireTimeoutException}。
     */
    private Duration acquireTimeout = Duration.ofSeconds(1);

    /**
     * 连接自建立起的总存活时间，与是否空闲无关。
     * <p>用于让 DNS / 负载均衡变更逐步生效；设得太小会造成周期性重建连接。
     */
    private Duration maxLifeTime = Duration.ofMinutes(30);

    /**
     * 连接空闲多久后被回收，须小于服务端 keepAlive。
     */
    private Duration maxIdleTime = Duration.ofSeconds(30);

    /**
     * 后台驱逐过期连接的执行间隔。
     */
    private Duration evictInBackground = Duration.ofSeconds(60);
}
