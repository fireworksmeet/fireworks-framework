package com.yzm.fireworks.redis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多数据源配置。
 *
 * <p><b>{@code ignoreUnknownFields = false}</b>：{@code datasource.*} 只支持下面列出的键，
 * 写错或写了不支持的键（如 {@code sentinel}、{@code url}、{@code lettuce.*}）会在启动期失败，
 * 而不是被静默忽略。声明成自己的类型而不是复用 Boot 的 {@code RedisProperties}，
 * 也是为了让 IDE 提示与真实支持范围一致。
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.redis.multi", ignoreUnknownFields = false)
public class MultiRedisProperties {

    /**
     * 默认数据源名称，必须是 {@link #datasource} 中的一个 key。
     * <p>该数据源会被注册为 {@code @Primary}，作为 {@code RedisUtil} 与 {@code LockService} 的默认上下文。
     */
    private String primary;

    /**
     * 数据源名称 → 连接配置；名称即 {@code RedisUtil.on(name)} / {@code LockService.on(name)} 的参数。
     */
    private Map<String, DataSource> datasource;

    /**
     * 单个数据源的连接配置（支持单机与集群，不支持 sentinel）。
     */
    @Data
    public static class DataSource {

        /**
         * Redis 主机名
         */
        private String host = "localhost";

        /**
         * Redis 端口
         */
        private int port = 6379;

        /**
         * ACL 用户名（Redis 6+），不需要时留空
         */
        private String username;

        /**
         * 密码，不需要时留空
         */
        private String password;

        /**
         * 数据库序号（仅单机模式有意义）
         */
        private int database;

        /**
         * 命令超时（读写等待）；为空表示不限制
         */
        private Duration timeout;

        /**
         * 建立连接超时；为空时使用 Lettuce 默认值
         */
        private Duration connectTimeout;

        /**
         * 客户端名称，会出现在 {@code CLIENT LIST} 中，便于排查连接来源
         */
        private String clientName;

        /**
         * 是否启用 TLS 连接（{@code rediss://}）
         */
        private Ssl ssl = new Ssl();

        /**
         * 集群配置；{@code nodes} 非空时按集群模式连接
         */
        private Cluster cluster = new Cluster();

        /**
         * 连接池配置；classpath 有 commons-pool2 时默认启用
         */
        private Pool pool = new Pool();
    }

    @Data
    public static class Ssl {

        /**
         * 是否启用 SSL/TLS
         */
        private boolean enabled;
    }

    @Data
    public static class Cluster {

        /**
         * 集群节点列表（{@code host:port}）；留空表示单机模式
         */
        private List<String> nodes = new ArrayList<>();

        /**
         * 最大重定向次数；为空时使用 Spring Data Redis 默认值
         */
        private Integer maxRedirects;
    }

    @Data
    public static class Pool {

        /**
         * 是否启用连接池；为空时按 commons-pool2 是否在 classpath 决定
         */
        private Boolean enabled;

        /**
         * 连接池最大连接数
         */
        private int maxActive = 8;

        /**
         * 最大空闲连接数
         */
        private int maxIdle = 8;

        /**
         * 最小空闲连接数
         */
        private int minIdle;

        /**
         * 取连接的最长等待时间；负值表示无限等待
         */
        private Duration maxWait = Duration.ofMillis(-1);

        /**
         * 空闲连接回收线程的运行间隔；为空时按 commons-pool2 默认值
         */
        private Duration timeBetweenEvictionRuns;
    }
}
