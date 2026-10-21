package com.yzm.fireworks.webfluxclient.config;

import com.yzm.fireworks.webfluxclient.properties.WebClientProperties;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorSettings;
import org.springframework.boot.http.client.reactive.ReactorClientHttpConnectorBuilder;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient 连接池配置。
 *
 * <p>不接管 Boot 的 {@code WebClient.Builder} 与 {@code ClientHttpConnector}，而是通过
 * {@link ClientHttpConnectorBuilderCustomizer} 在官方流水线里提供 Reactor Netty 的连接池参数：
 * 容量、排队上限、生命周期、空闲回收。
 *
 * <p>职责划分：
 * <ul>
 *   <li>connect / read 超时、重定向、SSL：{@code spring.http.reactiveclient.*}（Boot 负责，优先级最高）</li>
 *   <li>codec 缓冲上限：{@code spring.codec.max-in-memory-size}（Boot 负责，Jackson codec 用容器 ObjectMapper）</li>
 *   <li>连接池：{@code fireworks.webflux-client.*}（本类负责）</li>
 * </ul>
 *
 * <p>已知取舍：通过 {@code withHttpClientFactory} 接管 client 创建，因此不再复用 Boot 的
 * {@code ReactorResourceFactory} 事件循环配置，改用 Reactor Netty 默认 EventLoopGroup。
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(WebClientProperties.class)
public class WebClientAutoConfiguration {

    public static final String LOAD_BALANCE_WEB_CLIENT_BUILDER = "loadBalancedWebClientBuilder";

    /**
     * 仅在业务未配置 {@code spring.http.reactiveclient.connect-timeout} 时兜底。
     */
    private static final Duration FALLBACK_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 仅在业务未配置 {@code spring.http.reactiveclient.read-timeout} 时兜底；不配置时等于不限制。
     */
    private static final Duration FALLBACK_READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 共享连接池；业务自定义同类型 Bean 时让位。
     */
    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider connectionProvider(WebClientProperties props, ClientHttpConnectorSettings settings) {
        ConnectionProvider provider = ConnectionProvider.builder("fireworks-webflux-pool")
                .maxConnections(props.getMaxConnections())
                .pendingAcquireMaxCount(props.getPendingAcquireMaxCount())
                .pendingAcquireTimeout(props.getAcquireTimeout())
                .maxLifeTime(props.getMaxLifeTime())
                .maxIdleTime(props.getMaxIdleTime())
                .evictInBackground(props.getEvictInBackground())
                .build();
        log.info("[WebClient] Reactor Netty 池化 — maxConnections={}, pendingAcquireMaxCount={}, acquireTimeout={}, "
                        + "maxLifeTime={}, maxIdleTime={}, evictInBackground={}, connectTimeout={}, readTimeout={}",
                props.getMaxConnections(), props.getPendingAcquireMaxCount(), props.getAcquireTimeout(),
                props.getMaxLifeTime(), props.getMaxIdleTime(), props.getEvictInBackground(),
                timeout(settings.connectTimeout(), FALLBACK_CONNECT_TIMEOUT),
                timeout(settings.readTimeout(), FALLBACK_READ_TIMEOUT));
        return provider;
    }

    /**
     * 在 Boot 构建 {@code ClientHttpConnector} 的流水线里注入连接池与超时兜底。
     *
     * <p>之所以用官方扩展点而不是自建 {@code ReactorClientHttpConnector}：一旦替换 connector，
     * {@code spring.http.reactiveclient.*} 会整体失效且无任何提示。
     */
    @Bean
    public ClientHttpConnectorBuilderCustomizer<ClientHttpConnectorBuilder<?>> fireworksConnectorCustomizer(
            ConnectionProvider connectionProvider, ClientHttpConnectorSettings settings) {

        return builder -> {
            if (!(builder instanceof ReactorClientHttpConnectorBuilder reactor)) {
                log.warn("[WebClient] 当前 connector 为 {}，fireworks.webflux-client.* 仅对 Reactor Netty 生效",
                        builder.getClass().getName());
                return builder;
            }
            Duration connectTimeout = timeout(settings.connectTimeout(), FALLBACK_CONNECT_TIMEOUT);
            Duration readTimeout = timeout(settings.readTimeout(), FALLBACK_READ_TIMEOUT);

            // httpClientFactory 的产物之后才会应用 settings 与 httpClientCustomizer，因此这里只负责池化与兜底
            reactor.withHttpClientFactory(() -> HttpClient.create(connectionProvider))
                    .withHttpClientCustomizer(client -> client
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                            .responseTimeout(readTimeout));
            return reactor;
        };
    }

    /**
     * 普通（非负载均衡）Builder。
     *
     * <p>Boot 已提供同类型 Bean，这里再声明一个是为了用 {@link Primary} 消除与负载均衡 Builder 的注入歧义，
     * 并保持 {@code prototype} 语义（单例 Builder 会被后来的 {@code baseUrl(...)} 污染）。
     */
    @Bean
    @Primary
    @Scope("prototype")
    WebClient.Builder fireworksWebClientBuilder(ObjectProvider<WebClientCustomizer> customizers) {
        return customized(customizers);
    }

    /**
     * 负载均衡 Builder（专用于内部微服务调用）。
     *
     * <p>保持 Spring 的标准行为：非 2xx 抛 {@code WebClientResponseException}。是否关闭状态码异常、
     * 改为从 {@code Result.code} 判定，由业务自行决定（`api.md` §4.3），框架不预设。
     *
     * <p>作用域为 {@code prototype}：Builder 可变，单例共享会让某个注入点的 {@code baseUrl(...)}
     * 污染其他注入点。
     */
    @Bean
    @LoadBalanced
    @Qualifier(LOAD_BALANCE_WEB_CLIENT_BUILDER)
    @Scope("prototype")
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalanced")
    WebClient.Builder loadBalancedWebClientBuilder(ObjectProvider<WebClientCustomizer> customizers) {
        return customized(customizers);
    }

    private static WebClient.Builder customized(ObjectProvider<WebClientCustomizer> customizers) {
        WebClient.Builder builder = WebClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    private static Duration timeout(Duration configured, Duration fallback) {
        return configured != null ? configured : fallback;
    }
}