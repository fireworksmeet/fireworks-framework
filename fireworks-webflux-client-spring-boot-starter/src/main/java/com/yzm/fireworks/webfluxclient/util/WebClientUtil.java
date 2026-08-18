package com.yzm.fireworks.webfluxclient.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

/**
 * WebClient HTTP 服务代理工具类
 *
 * @author JYuan
 */
public class WebClientUtil {

    private WebClientUtil() {
        // 工具类私有构造
    }

    /**
     * 🟢 最常用的重载：根据指定的 baseUrl 创建代理
     *
     * @param builder     WebClient.Builder（由 Spring 容器注入，可以是 @LoadBalanced 或 普通 Builder）
     * @param serviceType HTTP Interfaces 接口类型
     * @param baseUrl     服务地址（如 "http://fireworks-auth" 或 "https://api.github.com"）
     */
    public static <T> T createProxy(WebClient.Builder builder, Class<T> serviceType, String baseUrl) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(serviceType, "serviceType 不能为空");

        // 使用 builder.clone() 防止修改传入的共享 builder 对象，造成多 Client 间的 baseUrl 相互污染
        WebClient.Builder clonedBuilder = builder.clone();

        if (StringUtils.hasText(baseUrl)) {
            clonedBuilder.baseUrl(baseUrl);
        }

        WebClient webClient = clonedBuilder.build();

        // 构建 HTTP 服务代理
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(adapter).build();

        return proxyFactory.createClient(serviceType);
    }

    /**
     * 🟢 重载：当 URL 已经在接口类上的 @HttpExchange("https://...") 中定义时调用
     */
    public static <T> T createProxy(WebClient.Builder builder, Class<T> serviceType) {
        return createProxy(builder, serviceType, null);
    }

    /**
     * 发送 Form/URL 参数请求 (GET / POST / PUT 等)
     *
     * @return 返回封装了状态码、响应头与响应体的 Mono<ResponseEntity<T>>
     */
    public static <T> Mono<ResponseEntity<T>> requestWithForm(
            WebClient.Builder builder,
            HttpMethod method,
            String url,
            Class<T> responseType,
            MultiValueMap<String, String> queryParams,
            HttpHeaders headers) {

        WebClient webClient = builder.clone().build();

        return webClient.method(method)
                .uri(url, uriBuilder -> {
                    if (queryParams != null && !queryParams.isEmpty()) {
                        uriBuilder.queryParams(queryParams);
                    }
                    return uriBuilder.build();
                })
                .headers(h -> {
                    if (headers != null) {
                        h.addAll(headers);
                    }
                })
                .retrieve()
                .toEntity(responseType);
    }

    /**
     * 以 JSON 形式发送 POST 请求
     *
     * @return 返回封装了状态码、响应头与响应体的 Mono<ResponseEntity<T>>
     */
    public static <T, D> Mono<ResponseEntity<T>> postWithJson(
            WebClient.Builder builder,
            String url,
            Class<T> responseType,
            D param,
            HttpHeaders headers) {

        WebClient webClient = builder.clone().build();

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    if (headers != null) {
                        h.addAll(headers);
                    }
                })
                .bodyValue(param) // 💡 WebClient 使用 bodyValue 传入普通 Java 对象，或 body() 传入 Mono/Flux
                .retrieve()
                .toEntity(responseType);
    }

    /**
     * 快捷方法：仅获取响应 Body 内容（不包含 ResponseHeader/StatusCode）
     */
    public static <T, D> Mono<T> postWithJsonForBody(
            WebClient.Builder builder,
            String url,
            Class<T> responseType,
            D param) {

        WebClient webClient = builder.clone().build();

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(param)
                .retrieve()
                .bodyToMono(responseType);
    }
}