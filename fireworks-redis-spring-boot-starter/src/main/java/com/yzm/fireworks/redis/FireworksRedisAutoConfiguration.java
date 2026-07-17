package com.yzm.fireworks.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import io.lettuce.core.resource.ClientResources;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.yzm.fireworks.common.constants.StringPool.COLON;
import static com.yzm.fireworks.redis.Constants.*;


/**
 * Redis 自动配置类
 *
 * @author JYuan
 */
@AutoConfiguration
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(MultiRedisProperties.class)
public class FireworksRedisAutoConfiguration {

    /**
     * 单数据源模式：直接绑定唯一的 RedissonClient。
     * 多数据源模式：绑定 primary 数据源的 RedissonClient 为默认，
     * 同时持有全部数据源的 clientMap，支持 {@code on("datasource")} 切换。
     */
    @Bean
    @ConditionalOnMissingBean
    public LockService lockService(RedissonClient redissonClient,
                                   ObjectProvider<MultiRedisManager> multiRedisManagerProvider) {
        MultiRedisManager manager = multiRedisManagerProvider.getIfAvailable();
        if (manager != null) {
            return new LockService(redissonClient, manager.getRedissonMap());
        }
        return new LockService(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public static DistributedLockAspect distributedLockAspect(@Lazy LockService lockService) {
        return new DistributedLockAspect(lockService);
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonRedisTemplate getJsonRedisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                  ObjectMapper objectMapper) {
        return new JsonRedisTemplate(redisConnectionFactory, objectMapper);
    }

    /**
     * 单数据源模式：直接用默认的 {@link StringRedisTemplate} 和 {@link JsonRedisTemplate}。
     * 多数据源模式：额外注入 {@link MultiRedisManager}，让 {@code on()} 路由可用。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisUtil redisUtil(JsonRedisTemplate jsonRedisTemplate,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectProvider<MultiRedisManager> multiRedisManagerProvider) {
        MultiRedisManager manager = multiRedisManagerProvider.getIfAvailable();
        if (manager != null) {
            return new RedisUtil(jsonRedisTemplate, stringRedisTemplate, manager.getContextMap());
        }
        return new RedisUtil(jsonRedisTemplate, stringRedisTemplate);
    }

    /**
     * 多数据源管理器：为每个数据源创建独立的连接工厂和操作上下文。
     *
     * <p>同时将 primary 数据源的 {@link LettuceConnectionFactory} 注册为 {@code @Primary}，
     * 使 Spring 默认的 {@link StringRedisTemplate}、{@link JsonRedisTemplate} 等 Bean
     * 都绑定到 primary 数据源，与 {@link RedisUtil} 的 defaultContext 保持一致。
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.data.redis.multi", name = "enabled")
    public MultiRedisManager multiRedisManager(
            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers,
            ClientResources clientResources,
            MultiRedisProperties multiRedisProperties,
            ObjectMapper objectMapper
    ) {
        String primary = multiRedisProperties.getPrimary();
        Assert.hasText(primary, "spring.data.redis.multi.primary must be configured");

        Map<String, LettuceConnectionFactory> factoryMap = Maps.newHashMap();
        Map<String, RedisContext> contextMap = Maps.newHashMap();
        Map<String, RedissonClient> redissonMap = Maps.newHashMap();

        multiRedisProperties.getDatasource().forEach((name, props) -> {
            LettuceConnectionFactory factory = createConnectionFactory(
                    props, builderCustomizers, clientResources
            );
            factory.afterPropertiesSet();
            factoryMap.put(name, factory);

            JsonRedisTemplate jsonTemplate = new JsonRedisTemplate(factory, objectMapper);
            StringRedisTemplate stringTemplate = new StringRedisTemplate(factory);
            contextMap.put(name, new RedisContext(jsonTemplate, stringTemplate));

            RedissonClient redissonClient = createRedisson(props);
            redissonMap.put(name, redissonClient);
        });

        return new MultiRedisManager(factoryMap, contextMap, redissonMap);
    }

    /**
     * 将 primary 数据源的连接工厂暴露为 {@code @Primary} Bean，
     * 供框架默认的 {@link StringRedisTemplate} 等组件注入。
     */
    @Bean(destroyMethod = "")   // ← 禁止 Spring 自动调用 destroy
    @Primary
    @ConditionalOnProperty(prefix = "spring.data.redis.multi", name = "enabled")
    public LettuceConnectionFactory primaryLettuceConnectionFactory(MultiRedisManager multiRedisManager,
                                                                    MultiRedisProperties multiRedisProperties) {
        String primary = multiRedisProperties.getPrimary();
        LettuceConnectionFactory factory = multiRedisManager.getFactoryMap().get(primary);
        Assert.notNull(factory, "Primary datasource '" + primary + "' not found in MultiRedisManager");
        return factory;
    }

    /**
     * 将 primary 数据源的 RedissonClient 暴露为 {@code @Primary} Bean，
     * 供 {@link LockService} 等组件注入，同时阻止单数据源的 {@code redisson()} 创建多余实例。
     */
    @Bean(destroyMethod = "")   // ← 禁止 Spring 自动调用 shutdown
    @Primary
    @ConditionalOnProperty(prefix = "spring.data.redis.multi", name = "enabled")
    public RedissonClient primaryRedisson(MultiRedisManager multiRedisManager,
                                          MultiRedisProperties multiRedisProperties) {
        String primary = multiRedisProperties.getPrimary();
        RedissonClient client = multiRedisManager.getRedissonMap().get(primary);
        Assert.notNull(client, "Primary RedissonClient '" + primary + "' not found");
        return client;
    }

    /**
     * 单数据源模式：创建绑定到 Spring 默认 Redis 配置的 RedissonClient。
     * 多数据源模式下由 {@link #multiRedisManager} 统一管理，此 Bean 不创建。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean({RedissonClient.class})
    public RedissonClient redisson(RedisProperties redisProperties) {
        return createRedisson(redisProperties);
    }

    // ── 私有工具 ────────────────────────────────────────────────────────────────

    private RedissonClient createRedisson(RedisProperties redisProperties) {
        Duration duration = redisProperties.getTimeout();
        int timeout = ObjectUtils.isEmpty(duration) ? CONNECT_TIME_OUT : (int) duration.toMillis();

        String username = redisProperties.getUsername();
        Config config = new Config();
        RedisProperties.Cluster cluster = redisProperties.getCluster();
        if (!ObjectUtils.isEmpty(cluster)) {
            List<String> nodes = cluster.getNodes();
            config.useClusterServers()
                    .addNodeAddress(nodes.toArray(new String[0]))
                    .setConnectTimeout(timeout)
                    .setUsername(username)
                    .setPassword(redisProperties.getPassword());
        } else {
            String prefix = redisProperties.getSsl().isEnabled() ? REDISS_PROTOCOL_PREFIX : REDIS_PROTOCOL_PREFIX;
            config.useSingleServer()
                    .setAddress(prefix + redisProperties.getHost() + COLON + redisProperties.getPort())
                    .setConnectTimeout(timeout)
                    .setDatabase(redisProperties.getDatabase())
                    .setUsername(username)
                    .setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }

    private LettuceConnectionFactory createConnectionFactory(
            RedisProperties props,
            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers,
            ClientResources clientResources) {

        LettuceConnectionConfiguration config = new LettuceConnectionConfiguration(
                props,
                emptyProvider(),
                emptyProvider(),
                emptyProvider()
        );
        return config.redisConnectionFactory(builderCustomizers, clientResources);
    }

    /**
     * 空 ObjectProvider，getIfAvailable() 永远返回 null，
     * 强制 LettuceConnectionConfiguration 从 RedisProperties 构建连接配置。
     */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        return (ObjectProvider<T>) EmptyObjectProvider.INSTANCE;
    }

    private static class EmptyObjectProvider<T> implements ObjectProvider<T> {

        static final EmptyObjectProvider<?> INSTANCE = new EmptyObjectProvider<>();

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            throw new NoSuchBeanDefinitionException("EmptyObjectProvider has no bean");
        }

        @Override
        public T getObject(Object... args) {
            throw new NoSuchBeanDefinitionException("EmptyObjectProvider has no bean");
        }

        @Override
        public Stream<T> stream() {
            return Stream.empty();
        }
    }
}