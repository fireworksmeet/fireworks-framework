package com.yzm.fireworks.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.resource.ClientResources;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 按 {@link MultiRedisProperties.DataSource} 构建 {@link LettuceConnectionFactory}。
 *
 * <p><b>为什么不用 Boot 的 {@code LettuceConnectionConfiguration}</b>：它是包级私有的，
 * 且只服务单个 {@code spring.data.redis.*}；多数据源需要按每份配置各建一个工厂。
 *
 * <p>因此只用公开 API（{@link RedisStandaloneConfiguration}、{@link RedisClusterConfiguration}、
 * {@link LettuceClientConfiguration}）与 Boot 的公开扩展点（{@link LettuceClientConfigurationBuilderCustomizer}），
 * 支持范围与 {@link MultiRedisProperties.DataSource} 完全一致：单机 / 集群 / SSL / 超时 / 连接池。
 * 不走 Boot 内部实现，Boot 升级时由编译期暴露签名变化，不需要人工比对源码。
 *
 * @author JYuan
 */
final class LettuceConnectionFactories {

    /**
     * commons-pool2 是否在 classpath：决定是否默认开启连接池。
     */
    private static final boolean POOL_AVAILABLE =
            ClassUtils.isPresent("org.apache.commons.pool2.ObjectPool",
                    LettuceConnectionFactories.class.getClassLoader());

    private LettuceConnectionFactories() {
        throw new AssertionError("Cannot instantiate LettuceConnectionFactories class");
    }

    /**
     * 构建连接工厂（尚未 {@code afterPropertiesSet()}，由调用方初始化）。
     */
    static LettuceConnectionFactory create(
            MultiRedisProperties.DataSource dataSource,
            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers,
            ClientResources clientResources) {

        Assert.notNull(dataSource, "数据源配置不能为空");

        LettuceClientConfiguration clientConfiguration =
                clientConfiguration(dataSource, builderCustomizers, clientResources);

        RedisClusterConfiguration clusterConfiguration = clusterConfiguration(dataSource);
        if (clusterConfiguration != null) {
            return new LettuceConnectionFactory(clusterConfiguration, clientConfiguration);
        }
        return new LettuceConnectionFactory(standaloneConfiguration(dataSource), clientConfiguration);
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(MultiRedisProperties.DataSource dataSource) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(dataSource.getHost(), dataSource.getPort());
        configuration.setDatabase(dataSource.getDatabase());
        if (StringUtils.hasText(dataSource.getUsername())) {
            configuration.setUsername(dataSource.getUsername());
        }
        if (StringUtils.hasText(dataSource.getPassword())) {
            configuration.setPassword(RedisPassword.of(dataSource.getPassword()));
        }
        return configuration;
    }

    private static RedisClusterConfiguration clusterConfiguration(MultiRedisProperties.DataSource dataSource) {
        MultiRedisProperties.Cluster cluster = dataSource.getCluster();
        if (cluster == null || cluster.getNodes() == null || cluster.getNodes().isEmpty()) {
            return null;
        }
        RedisClusterConfiguration configuration = new RedisClusterConfiguration(cluster.getNodes());
        if (cluster.getMaxRedirects() != null) {
            configuration.setMaxRedirects(cluster.getMaxRedirects());
        }
        if (StringUtils.hasText(dataSource.getUsername())) {
            configuration.setUsername(dataSource.getUsername());
        }
        if (StringUtils.hasText(dataSource.getPassword())) {
            configuration.setPassword(RedisPassword.of(dataSource.getPassword()));
        }
        return configuration;
    }

    private static LettuceClientConfiguration clientConfiguration(
            MultiRedisProperties.DataSource dataSource,
            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers,
            ClientResources clientResources) {

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = baseBuilder(dataSource);

        if (dataSource.getSsl() != null && dataSource.getSsl().isEnabled()) {
            builder.useSsl();
        }
        if (dataSource.getTimeout() != null) {
            builder.commandTimeout(dataSource.getTimeout());
        }
        if (StringUtils.hasText(dataSource.getClientName())) {
            builder.clientName(dataSource.getClientName());
        }

        builder.clientOptions(clientOptions(dataSource));
        builder.clientResources(clientResources);
        // 保留 Boot 的公开扩展点：业务仍可用 customizer 调整超时、SSL、线程池等
        builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    private static LettuceClientConfiguration.LettuceClientConfigurationBuilder baseBuilder(
            MultiRedisProperties.DataSource dataSource) {
        MultiRedisProperties.Pool pool = dataSource.getPool();
        if (isPoolEnabled(pool)) {
            return new PoolBuilderFactory().create(pool);
        }
        return LettuceClientConfiguration.builder();
    }

    private static boolean isPoolEnabled(MultiRedisProperties.Pool pool) {
        if (pool == null) {
            return false;
        }
        Boolean enabled = pool.getEnabled();
        return enabled != null ? enabled : POOL_AVAILABLE;
    }

    private static ClientOptions clientOptions(MultiRedisProperties.DataSource dataSource) {
        boolean cluster = dataSource.getCluster() != null && !dataSource.getCluster().getNodes().isEmpty();
        ClientOptions.Builder builder = cluster ? ClusterClientOptions.builder() : ClientOptions.builder();

        if (dataSource.getConnectTimeout() != null) {
            builder.socketOptions(SocketOptions.builder()
                    .connectTimeout(dataSource.getConnectTimeout())
                    .build());
        }
        return builder.timeoutOptions(TimeoutOptions.enabled()).build();
    }

    /**
     * 单独成类，避免 commons-pool2 不在 classpath 时加载到 {@link LettucePoolingClientConfiguration}。
     */
    private static final class PoolBuilderFactory {

        LettuceClientConfiguration.LettuceClientConfigurationBuilder create(MultiRedisProperties.Pool pool) {
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(pool.getMaxActive());
            poolConfig.setMaxIdle(pool.getMaxIdle());
            poolConfig.setMinIdle(pool.getMinIdle());
            if (pool.getTimeBetweenEvictionRuns() != null) {
                poolConfig.setTimeBetweenEvictionRuns(pool.getTimeBetweenEvictionRuns());
            }
            if (pool.getMaxWait() != null) {
                poolConfig.setMaxWait(pool.getMaxWait());
            }
            return LettucePoolingClientConfiguration.builder().poolConfig(poolConfig);
        }
    }
}
