package com.yzm.fireworks.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.yzm.fireworks.common.aop.MetadataSourcePointcut;
import com.yzm.fireworks.redis.lock.DistributedLockInterceptor;
import com.yzm.fireworks.redis.lock.DistributedLockMetadataSource;
import com.yzm.fireworks.redis.lock.LockService;
import io.lettuce.core.resource.ClientResources;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.yzm.fireworks.common.constants.StringPool.COLON;
import static com.yzm.fireworks.redis.RedisConstants.*;


/**
 * Redis 自动配置类。
 *
 * <p><b>类名带 {@code Fireworks} 前缀是故意的</b>：Spring Boot 已有一个
 * {@link RedisAutoConfiguration}，同名会让日志、异常栈和排查时的第一反应都指向错误的那一个。
 * 类似地，与 Boot 既有自动配置同名的类不做“风格统一”式重命名。
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
        // 空 Map 与单数据源构造器等价：on() 会给出“多数据源未开启”的提示
        return new LockService(redissonClient, manager == null ? Map.of() : manager.redissonMap());
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DistributedLockMetadataSource distributedLockMetadataSource() {
        return new DistributedLockMetadataSource();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DistributedLockInterceptor distributedLockInterceptor(DistributedLockMetadataSource metadataSource,
                                                                 LockService lockService) {
        return new DistributedLockInterceptor(metadataSource, lockService);
    }

    /**
     * 分布式锁 advisor：切点来自 {@link MetadataSourcePointcut}，通知是拦截器实例。
     *
     * <p>用标准 {@link DefaultPointcutAdvisor}：创建时就能拿到 advice，不需要按 Bean 名懒加载。
     * <b>也不要在它上面按 {@code AbstractBeanFactoryPointcutAdvisor} 判类型的条件</b>——
     * 事务 advisor 同样是该类型的子类，条件会让分布式锁静默失效。
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DefaultPointcutAdvisor distributedLockAdvisor(DistributedLockInterceptor interceptor,
                                                         DistributedLockMetadataSource metadataSource) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
                new MetadataSourcePointcut<>(metadataSource), interceptor);
        // 保持最高优先级（最低可用 Ordered.LOWEST_PRECEDENCE）
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonRedisTemplate getJsonRedisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                  ObjectMapper objectMapper) {
        return new JsonRedisTemplate(redisConnectionFactory, objectMapper);
    }

    /**
     * 多数据源模式：模板 Bean 由框架提供，绑定到 primary 数据源。
     *
     * <p>不提供的话，{@link RedisUtil} 只能依赖 Boot 的 {@code RedisAutoConfiguration} 创建模板——
     * 业务一旦排除该自动配置（只用 {@code fireworks.redis.multi} 配置数据源），启动时就缺 Bean。
     * 本类排在 Boot 之前，Boot 的 {@code @ConditionalOnMissingBean} 会自然让位。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnMultiRedis
    public StringRedisTemplate stringRedisTemplate(MultiRedisManager multiRedisManager) {
        // 复用管理器里 primary 数据源的模板实例，避免同一连接工厂上出现两个 StringRedisTemplate
        return multiRedisManager.primaryContext().getStringRedisTemplate();
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
        return new RedisUtil(jsonRedisTemplate, stringRedisTemplate,
                manager == null ? Map.of() : manager.contextMap());
    }

    /**
     * 多数据源管理器：为每个数据源创建独立的连接工厂和操作上下文。
     *
     * <p>同时将 primary 数据源的 {@link LettuceConnectionFactory} 注册为 {@code @Primary}，
     * 使 Spring 默认的 {@link StringRedisTemplate}、{@link JsonRedisTemplate} 等 Bean
     * 都绑定到 primary 数据源，与 {@link RedisUtil} 的 defaultContext 保持一致。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMultiRedis
    public MultiRedisManager multiRedisManager(
            ObjectProvider<LettuceClientConfigurationBuilderCustomizer> builderCustomizers,
            ClientResources clientResources,
            MultiRedisProperties multiRedisProperties,
            ObjectMapper objectMapper
    ) {
        Assert.notEmpty(multiRedisProperties.getDatasource(),
                "fireworks.redis.multi.enabled=true 时必须配置 datasource（至少一个数据源）");
        String primary = multiRedisProperties.getPrimary();
        Assert.hasText(primary, "fireworks.redis.multi.primary must be configured");

        // LinkedHashMap：保证启动顺序与配置顺序一致，排查日志时更直观
        Map<String, LettuceConnectionFactory> factoryMap = Maps.newLinkedHashMap();
        Map<String, RedisContext> contextMap = Maps.newHashMap();
        Map<String, RedissonClient> redissonMap = Maps.newHashMap();

        try {
            multiRedisProperties.getDatasource().forEach((name, props) -> {
                LettuceConnectionFactory factory =
                        LettuceConnectionFactories.create(props, builderCustomizers, clientResources);
                factory.afterPropertiesSet();
                factoryMap.put(name, factory);

                JsonRedisTemplate jsonTemplate = new JsonRedisTemplate(factory, objectMapper);
                StringRedisTemplate stringTemplate = new StringRedisTemplate(factory);
                contextMap.put(name, new RedisContext(jsonTemplate, stringTemplate));

                redissonMap.put(name, createRedisson(Endpoint.of(props)));
            });
        } catch (RuntimeException e) {
            // 初始化不是原子的：任一数据源失败就回滚已创建的连接工厂与 Redisson，
            // 否则它们会一直持有线程与连接，且不会被容器管理（Bean 创建失败后不会进入销毁流程）
            MultiRedisManager.release(factoryMap, redissonMap, "rollback");
            throw e;
        }

        // primary 必须真的存在：否则要等到 primaryLettuceConnectionFactory 时才报错，信息更含糊
        Assert.isTrue(factoryMap.containsKey(primary),
                () -> "fireworks.redis.multi.primary=" + primary
                        + " 未在 datasource 中定义，已定义的数据源：" + factoryMap.keySet());

        return new MultiRedisManager(primary, factoryMap, contextMap, redissonMap);
    }

    /**
     * 将 primary 数据源的连接工厂暴露为 {@code @Primary} Bean，
     * 供框架默认的 {@link StringRedisTemplate} 等组件注入。
     */
    @Bean(destroyMethod = "")   // ← 禁止 Spring 自动调用 destroy
    @Primary
    @ConditionalOnMultiRedis
    public LettuceConnectionFactory primaryLettuceConnectionFactory(MultiRedisManager multiRedisManager) {
        return multiRedisManager.primaryFactory();
    }

    /**
     * 将 primary 数据源的 RedissonClient 暴露为 {@code @Primary} Bean，
     * 供 {@link LockService} 等组件注入，同时阻止单数据源的 {@code redisson()} 创建多余实例。
     */
    @Bean(destroyMethod = "")   // ← 禁止 Spring 自动调用 shutdown
    @Primary
    @ConditionalOnMultiRedis
    public RedissonClient primaryRedisson(MultiRedisManager multiRedisManager) {
        return multiRedisManager.primaryRedisson();
    }

    /**
     * 单数据源模式：创建绑定到 Spring 默认 Redis 配置的 RedissonClient。
     * 多数据源模式下由 {@link #multiRedisManager} 统一管理，此 Bean 不创建。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean({RedissonClient.class})
    // 显式表达“多数据源未开启”，不依赖与 primaryRedisson 的注册顺序
    @ConditionalOnProperty(prefix = "fireworks.redis.multi", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public RedissonClient redisson(RedisProperties redisProperties) {
        return createRedisson(Endpoint.of(redisProperties));
    }

    // ── 私有工具 ────────────────────────────────────────────────────────────────

    /**
     * 构建 Redisson 客户端（两种配置来源最终都归一化成 {@link Endpoint}）。
     */
    private RedissonClient createRedisson(Endpoint endpoint) {
        // 连接超时与命令超时是两个参数：之前把命令超时误当成连接超时
        int connectTimeout = ObjectUtils.isEmpty(endpoint.connectTimeout())
                ? CONNECT_TIME_OUT : (int) endpoint.connectTimeout().toMillis();
        int commandTimeout = ObjectUtils.isEmpty(endpoint.timeout())
                ? CONNECT_TIME_OUT : (int) endpoint.timeout().toMillis();

        Config config = new Config();
        if (!ObjectUtils.isEmpty(endpoint.nodes())) {
            config.useClusterServers()
                    .addNodeAddress(endpoint.nodes().toArray(new String[0]))
                    .setConnectTimeout(connectTimeout)
                    .setTimeout(commandTimeout)
                    .setUsername(endpoint.username())
                    .setPassword(endpoint.password());
        } else {
            String prefix = endpoint.ssl() ? REDISS_PROTOCOL_PREFIX : REDIS_PROTOCOL_PREFIX;
            config.useSingleServer()
                    .setAddress(prefix + endpoint.host() + COLON + endpoint.port())
                    .setConnectTimeout(connectTimeout)
                    .setTimeout(commandTimeout)
                    .setDatabase(endpoint.database())
                    .setUsername(endpoint.username())
                    .setPassword(endpoint.password());
        }
        return Redisson.create(config);
    }

    /**
     * 两种配置来源（{@code spring.data.redis.*} 与 {@code fireworks.redis.multi.datasource.*}）
     * 归一化后的连接参数，避免 8 个裸参数在方法间传递。
     */
    private record Endpoint(String host, int port, String username, String password,
                            int database, boolean ssl, List<String> nodes,
                            Duration timeout, Duration connectTimeout) {

        static Endpoint of(RedisProperties properties) {
            // Redisson 侧不支持 sentinel：静默忽略会让应用连到 localhost，宁可直接失败
            Assert.isNull(properties.getSentinel(),
                    "Redisson（分布式锁）不支持 spring.data.redis.sentinel，请改用单机 host/port 或 cluster.nodes");
            List<String> nodes = properties.getCluster() == null ? null : properties.getCluster().getNodes();
            boolean ssl = properties.getSsl() != null && properties.getSsl().isEnabled();
            return new Endpoint(properties.getHost(), properties.getPort(), properties.getUsername(),
                    properties.getPassword(), properties.getDatabase(), ssl, nodes,
                    properties.getTimeout(), properties.getConnectTimeout());
        }

        static Endpoint of(MultiRedisProperties.DataSource dataSource) {
            List<String> nodes = dataSource.getCluster() == null ? null : dataSource.getCluster().getNodes();
            boolean ssl = dataSource.getSsl() != null && dataSource.getSsl().isEnabled();
            return new Endpoint(dataSource.getHost(), dataSource.getPort(), dataSource.getUsername(),
                    dataSource.getPassword(), dataSource.getDatabase(), ssl, nodes,
                    dataSource.getTimeout(), dataSource.getConnectTimeout());
        }
    }

}