package com.yzm.fireworks.webfluxclient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.webfluxclient.properties.WebClientProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 生产级 WebClient 自动配置
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration(after = WebClientAutoConfiguration.class) // 确保在 Spring Boot 官方配置之后触发
@EnableConfigurationProperties(WebClientProperties.class)
public class WebClientAutoConfiguration {

    public static final String LOAD_BALANCE_WEB_CLIENT_BUILDER = "loadBalancedWebClientBuilder";
    public static final String DEFAULT_WEB_CLIENT_BUILDER = "webClientBuilder";

    private final WebClientProperties webClientProperties;

    public WebClientAutoConfiguration(WebClientProperties webClientProperties) {
        this.webClientProperties = webClientProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider connectionProvider(WebClientProperties props) {
        ConnectionProvider provider = ConnectionProvider.builder("fireworks-webflux-pool")
                .maxConnections(props.getMaxConnections())
                .pendingAcquireMaxCount(props.getPendingAcquireMaxCount())
                .pendingAcquireTimeout(Duration.ofMillis(props.getAcquireTimeout()))
                .maxLifeTime(Duration.ofMillis(props.getMaxLifeTime()))
                .maxIdleTime(Duration.ofMillis(props.getMaxIdleTime()))
                .evictInBackground(Duration.ofMillis(props.getEvictInBackground()))
                .build();
        log.info("[WebClient] ConnectionProvider 初始化 — maxConnections={}, pendingAcquireMaxCount={}, " +
                        "maxIdleTime={}ms, maxLifeTime={}ms",
                props.getMaxConnections(), props.getPendingAcquireMaxCount(),
                props.getMaxIdleTime(), props.getMaxLifeTime());
        return provider;
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpClient httpClient(ConnectionProvider connectionProvider, WebClientProperties props) {
        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeout())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(props.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(props.getWriteTimeout(), TimeUnit.MILLISECONDS)));
    }

    /**
     * 🟢 1. 全局公共配置抽取为 WebClientCustomizer
     * 无论是负载均衡还是普通的 WebClient.Builder，都会自动应用这套连接池、请求头和编解码器配置
     */
    @Bean
    public WebClientCustomizer webClientCustomizer(HttpClient httpClient, ObjectMapper objectMapper) {
        return builder -> builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 全局默认请求头
                .defaultHeader("Accept", "application/json")
                // 配置使用容器中的 ObjectMapper
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(webClientProperties.getMaxInMemorySize());
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                });
    }

    /**
     * 🟢 2. 带负载均衡的 Builder（用于内部微服务调用）
     */
    @Bean
    @LoadBalanced
    @Qualifier(LOAD_BALANCE_WEB_CLIENT_BUILDER)
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalanced")
    public WebClient.Builder loadBalancedWebClientBuilder(ObjectProvider<WebClientCustomizer> customizers) {
        WebClient.Builder builder = WebClient.builder();
        // 💡 遍历注入所有 Customizer（包含了上面定义的连接池配置，以及 Spring Boot 自动注入的 TraceId/Observation 配置）
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    /**
     * 🟢 3. 不带负载均衡的通用 Builder（用于第三方/外网 API 调用）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = DEFAULT_WEB_CLIENT_BUILDER)
    public WebClient.Builder webClientBuilder(ObjectProvider<WebClientCustomizer> customizers) {
        WebClient.Builder builder = WebClient.builder();
        // 💡 同样遍历应用所有 Customizer，保持 TraceId 和公共配置一致
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

}