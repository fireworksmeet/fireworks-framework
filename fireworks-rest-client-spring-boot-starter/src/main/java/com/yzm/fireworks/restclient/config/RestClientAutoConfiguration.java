package com.yzm.fireworks.restclient.config;

import com.yzm.fireworks.restclient.properties.RestClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.http.client.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient 连接池配置。
 *
 * <p>不接管 Boot 的 {@code RestClient.Builder} 与 {@code ClientHttpRequestFactory}，而是通过
 * {@link ClientHttpRequestFactoryBuilderCustomizer} 在官方流水线里补齐 Spring Boot 故意不提供的池化参数：
 * 容量、取连接等待上限、TTL、空闲回收、闲置校验。
 *
 * <p>职责划分：
 * <ul>
 *   <li>connect / read 超时、重定向、SSL：{@code spring.http.client.*}（Boot 负责，优先级最高）</li>
 *   <li>连接池：{@code fireworks.rest-client.*}（本类负责）</li>
 * </ul>
 *
 * <p>已知取舍：连接池由 Boot 内部创建，本类拿不到 {@code PoolingHttpClientConnectionManager} 实例，
 * 因此不注册池化指标（leased / available / pending）。若后续需要，应在
 * {@code withConnectionManagerCustomizer} 之外另行持有 manager。
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RestClientProperties.class)
@ConditionalOnProperty(prefix = "fireworks.rest-client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RestClientAutoConfiguration {

    public static final String LOAD_BALANCE_REST_CLIENT_BUILDER = "loadBalancedRestClientBuilder";

    /**
     * 仅在业务未配置 {@code spring.http.client.connect-timeout} 时兜底；
     * 不配置时 HC5 默认是 3 分钟。
     */
    private static final Duration FALLBACK_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 仅在业务未配置 {@code spring.http.client.read-timeout} 时兜底；不配置时等于不限制。
     */
    private static final Duration FALLBACK_READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 在 Boot 构建 {@code ClientHttpRequestFactory} 的流水线里追加 HC5 池化参数。
     *
     * <p>之所以用官方扩展点而不是自建 {@code CloseableHttpClient}：一旦替换 factory，
     * {@code spring.http.client.*} 会整体失效且无任何提示。
     */
    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<ClientHttpRequestFactoryBuilder<?>> fireworksHttpClientCustomizer(
            RestClientProperties props, ClientHttpRequestFactorySettings settings) {

        log.info("[RestClient] HttpClient5 池化 — maxConnections={}, maxConnectionsPerRoute={}, "
                        + "acquireTimeout={}, maxLifeTime={}, maxIdleTime={}, validateAfterInactivity={}",
                props.getMaxConnections(), props.getMaxConnectionsPerRoute(), props.getAcquireTimeout(),
                props.getMaxLifeTime(), props.getMaxIdleTime(), props.getValidateAfterInactivity());

        return builder -> {
            if (!(builder instanceof HttpComponentsClientHttpRequestFactoryBuilder http)) {
                log.warn("[RestClient] 当前 factory 为 {}，fireworks.rest-client.* 仅对 HttpClient5 生效",
                        builder.getClass().getName());
                return builder;
            }
            http.withConnectionManagerCustomizer(manager -> manager
                            .setMaxConnTotal(props.getMaxConnections())
                            .setMaxConnPerRoute(props.getMaxConnectionsPerRoute()))
                    .withConnectionConfigCustomizer(connection -> connection
                            // 连接超时属于 ConnectionConfig；factory.setConnectTimeout 自 6.2.13 起已废弃
                            .setConnectTimeout(Timeout.ofMilliseconds(
                                    (settings.connectTimeout() != null
                                            ? settings.connectTimeout() : FALLBACK_CONNECT_TIMEOUT).toMillis()))
                            .setTimeToLive(TimeValue.ofMilliseconds(props.getMaxLifeTime().toMillis()))
                            .setValidateAfterInactivity(
                                    TimeValue.ofMilliseconds(props.getValidateAfterInactivity().toMillis())))
                    .withHttpClientCustomizer(client -> client
                            .evictExpiredConnections()
                            .evictIdleConnections(TimeValue.ofMilliseconds(props.getMaxIdleTime().toMillis())))
                    .withCustomizer(factory -> {
                        // 设在 factory 上才会进入每个请求的 RequestConfig；设在 client 默认值上会被 Spring 覆盖
                        factory.setConnectionRequestTimeout(props.getAcquireTimeout());
                        if (settings.readTimeout() == null) {
                            factory.setReadTimeout(FALLBACK_READ_TIMEOUT);
                        }
                    });
            return http;
        };
    }

    /**
     * 普通（非负载均衡）Builder。
     *
     * <p>复用 Boot 的 {@link RestClientBuilderConfigurer}：由它按 {@code spring.http.client.*} 构建 factory，
     * 再应用容器里所有 {@code RestClientCustomizer}。
     *
     * <p>声明为 {@link Primary} 与 {@code prototype} 仅为了消除与负载均衡 Builder 的注入歧义，
     * 并保持 Builder 可变语义（单例 Builder 会被后来的 {@code baseUrl(...)} 污染）。
     */
    @Bean
    @Primary
    @Scope("prototype")
    RestClient.Builder fireworksRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    /**
     * 负载均衡 Builder（专用于内部微服务调用）。
     *
     * <p>保持 Spring 的标准行为：非 2xx 抛 {@code RestClientResponseException}。是否关闭状态码异常、
     * 改为从 {@code Result.code} 判定，由业务自行决定——取决于是否需要透传依赖的错误码与归属、
     * 是否需要按 4xx（如限流、参数不合法）做降级，属于业务侧的失败模型取舍，框架不预设。
     *
     * <p>需要该行为的业务，注册自己的 {@code RestClientCustomizer} 即可（注意兜底「非 2xx 且 body 为空」
     * 时 {@code body()} 返回 null 的情况）。
     *
     * <p>作用域为 {@code prototype}：Builder 是可变的，单例共享会让某个注入点的 {@code baseUrl(...)}
     * 污染其他注入点。
     */
    @Bean
    @LoadBalanced
    @Qualifier(LOAD_BALANCE_REST_CLIENT_BUILDER)
    @Scope("prototype")
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalanced")
    public RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }
}
