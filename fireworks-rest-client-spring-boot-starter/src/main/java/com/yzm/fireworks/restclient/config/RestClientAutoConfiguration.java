package com.yzm.fireworks.restclient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import com.yzm.fireworks.restclient.properties.RestClientProperties;

/**
 * 生产级 RestClient 配置
 *
 * <p>功能特性：
 * <ul>
 *   <li>连接池管理（PoolingHttpClientConnectionManager）</li>
 *   <li>三级超时精细控制（connectTimeout / socketTimeout / connectionRequestTimeout）</li>
 *   <li>后台线程自动回收过期和空闲连接，防止僵尸连接</li>
 * </ul>
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RestClientProperties.class)
public class RestClientAutoConfiguration {

    public static final String LOAD_BALANCE_REST_CLIENT_BUILDER = "loadBalancedRestClientBuilder";
    public static final String DEFAULT_REST_CLIENT_BUILDER = "restClientBuilder";

    /**
     * 连接池管理器
     *
     * <p>单独声明为 Bean 便于：
     * <ol>
     *   <li>注入 MeterRegistry 暴露连接池监控指标</li>
     *   <li>后续扩展 SSL/TLS 自定义证书场景</li>
     * </ol>
     */
    @Bean
    @ConditionalOnMissingBean(PoolingHttpClientConnectionManager.class)
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager(
            RestClientProperties props) {

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(
                                ConnectionConfig.custom()
                                        // TCP 三次握手超时
                                        .setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeout()))
                                        // 连接建立后空闲保活时长
                                        // 须小于服务端 keepAlive，防止服务端关闭后客户端持有僵尸连接
                                        .setTimeToLive(TimeValue.ofMilliseconds(props.getKeepAlive()))
                                        .build()
                        )
                        .setDefaultSocketConfig(
                                SocketConfig.custom()
                                        // Socket 读数据超时（等待服务端响应）
                                        .setSoTimeout(Timeout.ofMilliseconds(props.getSocketTimeout()))
                                        // 开启 TCP KeepAlive 探活，由 OS 层面检测断开的连接
                                        .setSoKeepAlive(true)
                                        .build()
                        )
                        // 连接池总容量
                        .setMaxConnTotal(props.getMaxConnTotal())
                        // 单个目标主机最大连接数（核心参数，防止被某个慢服务拖垮）
                        .setMaxConnPerRoute(props.getMaxConnPerRoute())
                        .build();

        log.info("[RestClient] 连接池初始化完成 — maxConnTotal={}, maxConnPerRoute={}, " +
                        "connectTimeout={}ms, socketTimeout={}ms, keepAlive={}ms",
                props.getMaxConnTotal(), props.getMaxConnPerRoute(),
                props.getConnectTimeout(), props.getSocketTimeout(), props.getKeepAlive());

        return connectionManager;
    }

    /**
     * ClientHttpRequestFactory
     */
    @Bean
    @ConditionalOnMissingBean(CloseableHttpClient.class)
    public CloseableHttpClient closeableHttpClient(
            RestClientProperties props,
            PoolingHttpClientConnectionManager connectionManager) {

        RequestConfig requestConfig = RequestConfig.custom()
                // 从连接池获取连接的等待超时，建议设短：快速失败优于长时间排队
                // 若频繁报 ConnectionRequestTimeoutException，说明连接池容量不足
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(props.getConnectionRequestTimeout()))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // 启用后台线程定期驱逐过期连接（配合 connectionManager 的 closeIdleConnections）
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMilliseconds(props.getEvictIdleConnections()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ClientHttpRequestFactory.class)
    public ClientHttpRequestFactory clientHttpRequestFactory(CloseableHttpClient httpClient) {
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * 🟢 官方推荐：通过 RestClientCustomizer 全局定制 RestClient.Builder
     * 这样不会干扰 Spring Cloud LoadBalancer 对 RestClient.Builder 的生命周期管理
     */
    @Bean
    public RestClientCustomizer restClientCustomizer(
            ClientHttpRequestFactory clientHttpRequestFactory,
            ObjectMapper objectMapper) {
        return builder -> builder
                .requestFactory(clientHttpRequestFactory)
                .messageConverters(converters -> {
                    // 移除默认的 Jackson 转换器，添加使用容器中 ObjectMapper 的转换器
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });
    }

    /**
     * 2. 🟢 负载均衡 Builder（专用于内部微服务调用）
     * 使用 @Qualifier("loadBalancedRestClientBuilder") 明确标识
     */
    @Bean
    @LoadBalanced
    @Qualifier(LOAD_BALANCE_REST_CLIENT_BUILDER)
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalanced")
    public RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
        RestClient.Builder builder = RestClient.builder();
        // 手动调用 configure 方法，确保包含TraceId（Micrometer Tracing）等官方默认能能力
        configurer.configure(builder);
        return builder;
    }

    /**
     * 3. 🟢 普通/非负载均衡 Builder（专用于第三方接口或常规 HTTP 请求）
     * 设为 @Primary，默认注入
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = DEFAULT_REST_CLIENT_BUILDER)
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        RestClient.Builder builder = RestClient.builder();
        // 手动调用 configure 方法，确保包含TraceId（Micrometer Tracing）等官方默认能能力
        configurer.configure(builder);
        return builder;
    }

}
