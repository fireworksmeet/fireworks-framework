package com.yzm.fireworks.restclient.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * HC5 连接池配置。
 *
 * <p>配置前缀：{@code fireworks.rest-client}。只包含 <b>Spring Boot 未提供</b>的池化参数；
 * connect / read 超时、重定向、SSL 请使用 Spring Boot 的 {@code spring.http.client.*}。
 *
 * <pre>
 * fireworks:
 *   rest-client:
 *     acquire-timeout: 1s                 # 从连接池取连接的等待上限，快速失败
 *     max-connections: 500                # 连接池总容量
 *     max-connections-per-route: 200      # 单个目标主机最大连接数
 *     max-life-time: 5m                   # 连接自建立起的总存活时间（不是空闲时间）
 *     max-idle-time: 30s                  # 空闲多久被回收
 *     validate-after-inactivity: 2s       # 连接闲置多久后重新校验（防服务端已断开）
 * </pre>
 *
 * <p>键名与 {@code fireworks.webflux-client} 对齐：{@code max-connections} /
 * {@code acquire-timeout} / {@code max-life-time} / {@code max-idle-time} 在两个客户端里同名同义，
 * 业务不必记两套名字。
 *
 * <p>取值语义与 HC5 一致：{@code -1} 表示不限制（如 {@code max-life-time: -1ms}）。
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.rest-client")
public class RestClientProperties {

    /**
     * 从连接池获取连接的等待上限，超时抛 {@code ConnectionPoolTimeoutException}。
     * <p>快速失败优于无限排队；频繁超时说明 {@link #maxConnectionsPerRoute} 不足。
     */
    private Duration acquireTimeout = Duration.ofSeconds(1);

    /**
     * 连接池总最大连接数。
     * <p>推荐值 = Σ(各目标主机的 {@link #maxConnectionsPerRoute})；只调一个目标主机时两者可相等。
     */
    private int maxConnections = 500;

    /**
     * 单个目标主机（Route）的最大连接数。
     * <p>推导公式：{@code maxConnectionsPerRoute ≥ 峰值QPS × 平均响应时间(秒) × 1.5}。
     * <p>示例：峰值 500QPS、平均响应 200ms → 150，取 200。
     */
    private int maxConnectionsPerRoute = 200;

    /**
     * 连接自建立起的总存活时间，与是否空闲无关。
     * <p>设得太小会在高 QPS 下周期性重建连接（重复 TCP/TLS 握手），只应作为
     * 让 DNS / 负载均衡变更逐步生效的手段；空闲连接的回收由 {@link #maxIdleTime} 负责。
     */
    private Duration maxLifeTime = Duration.ofMinutes(5);

    /**
     * 连接空闲多久后被回收，须小于服务端 keepAlive。
     * <p>Nginx 默认 {@code keepalive_timeout=75s}，推荐 30s 左右。
     */
    private Duration maxIdleTime = Duration.ofSeconds(30);

    /**
     * 连接闲置超过该时长后，再次使用前先做一次校验。
     * <p>用于覆盖「服务端已关闭、客户端仍持有」的僵尸连接（表现为
     * {@code NoHttpResponseException} / {@code Connection reset}）。
     * <p>推荐值：1~2s；设为 {@code -1ms} 关闭校验。
     */
    private Duration validateAfterInactivity = Duration.ofSeconds(2);
}
