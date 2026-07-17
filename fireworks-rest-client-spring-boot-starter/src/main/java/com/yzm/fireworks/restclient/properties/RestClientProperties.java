package com.yzm.fireworks.restclient.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RestClient / HttpClient 连接池配置
 *
 * <p>配置前缀：{@code fireworks.rest-client}
 *
 * <pre>
 * fireworks:
 *   rest-client:
 *     connect-timeout: 3000        # TCP 握手超时（ms）
 *     socket-timeout: 10000        # 读取数据超时（ms）
 *     connection-request-timeout: 800  # 从连接池获取连接超时（ms），建议设短，快速失败
 *     max-conn-total: 500          # 连接池总容量
 *     max-conn-per-route: 200      # 单个目标主机最大连接数
 *     keep-alive: 30000            # 连接空闲保活时长（ms），建议小于服务端 keepAlive 配置
 *     evict-idle-connections: 60000  # 多久回收一次空闲连接（ms）
 * </pre>
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.rest-client")
public class RestClientProperties {

    // ==================== 超时配置 ====================

    /**
     * TCP 三次握手建立连接的超时时间（毫秒）
     * <p>推荐值：内网 1000~2000ms，外网 3000~5000ms
     */
    private long connectTimeout = 3000;

    /**
     * Socket 读取数据超时时间（毫秒），即等待服务端响应的最大时间
     * <p>推荐值：根据下游接口响应时间 P99 的 2~3 倍设置；
     * 慢接口（报表/文件）建议单独创建 RestClient 实例并使用更大的值
     */
    private long socketTimeout = 10000;

    /**
     * 从连接池获取连接的等待超时（毫秒）
     * <p>建议设置比 connectTimeout 短，快速失败优于长时间排队。
     * 若频繁超时说明连接池容量不足，应调大 maxConnPerRoute。
     * <p>推荐值：500~1000ms
     */
    private long connectionRequestTimeout = 800;

    // ==================== 连接池容量 ====================

    /**
     * 连接池总最大连接数
     * <p>推荐值 = Σ(各目标主机的 maxConnPerRoute)；
     * 若只调用一个目标主机，与 maxConnPerRoute 保持相等即可。
     * <p>推荐值：200~500
     */
    private int maxConnTotal = 500;

    /**
     * 单个目标主机（Route）的最大连接数
     * <p>推导公式：maxConnPerRoute ≥ 峰值QPS × 平均响应时间(秒) × 缓冲系数(1.5)
     * <p>示例：峰值 500QPS，平均响应 200ms → 500 × 0.2 × 1.5 = 150，取 200
     * <p>推荐值：100~200
     */
    private int maxConnPerRoute = 200;

    // ==================== 连接保活与回收 ====================

    /**
     * 连接空闲保活时长（毫秒）
     * <p>必须小于目标服务端的 keepAlive 超时设置，否则服务端关闭连接后
     * 客户端仍持有"僵尸连接"，发送请求时会报 {@code Connection reset}。
     * <p>Nginx 默认 keepalive_timeout=75s，推荐此值设为 30000~60000ms
     */
    private long keepAlive = 30000;

    /**
     * 后台线程检测并回收空闲连接的间隔（毫秒）
     * <p>推荐值：30000~60000ms
     */
    private long evictIdleConnections = 60000;
}
