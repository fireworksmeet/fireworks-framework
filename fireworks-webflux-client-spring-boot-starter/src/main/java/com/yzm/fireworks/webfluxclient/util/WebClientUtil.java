package com.yzm.fireworks.webfluxclient.util;

import lombok.experimental.UtilityClass;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

/**
 * WebClient 便捷工具。
 * <p>
 * 只用于零散的单次调用；同一接口反复调用时优先用 {@link #createProxy} 声明式接口
 * （{@code @GetExchange} / {@code @PostExchange}），可保留泛型返回类型、参数绑定与可 mock 性。
 *
 * @author JYuan
 */
@UtilityClass
public class WebClientUtil {

    /**
     * 根据指定的 baseUrl 创建 HTTP 接口代理
     *
     * @param builder     WebClient.Builder（由 Spring 容器注入，可以是 @LoadBalanced 或普通 Builder）
     * @param serviceType HTTP Interfaces 接口类型
     * @param baseUrl     服务地址（如 "http://fireworks-auth" 或 "https://api.github.com"）
     * @param <T>         代理类型
     * @return 代理客户端实例
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

        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(adapter).build();

        return proxyFactory.createClient(serviceType);
    }

    /**
     * 创建 HTTP 接口代理（URL 定义在接口的 {@code @HttpExchange} 上）
     *
     * @param builder     WebClient.Builder
     * @param serviceType HTTP Interfaces 接口类型
     * @param <T>         代理类型
     * @return 代理客户端实例
     */
    public static <T> T createProxy(WebClient.Builder builder, Class<T> serviceType) {
        return createProxy(builder, serviceType, null);
    }

    /**
     * GET 请求（参数只走 query，不带 body）。
     *
     * @param builder      WebClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param queryParams  query 参数，可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空 body 时为空 Mono
     */
    public static <T> Mono<T> get(WebClient.Builder builder, String url, Class<T> responseType,
                                  MultiValueMap<String, String> queryParams, HttpHeaders headers) {
        return get(builder, url, ParameterizedTypeReference.forType(responseType), queryParams, headers);
    }

    /**
     * GET 请求，适用于泛型返回（如 {@code Result<List<UserDTO>>}）。
     *
     * @param builder      WebClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param queryParams  query 参数，可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空 body 时为空 Mono
     */
    public static <T> Mono<T> get(WebClient.Builder builder, String url, ParameterizedTypeReference<T> responseType,
                                  MultiValueMap<String, String> queryParams, HttpHeaders headers) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(responseType, "responseType 不能为空");

        return builder.build()
                .get()
                .uri(url, uriBuilder -> {
                    if (queryParams != null && !queryParams.isEmpty()) {
                        uriBuilder.queryParams(queryParams);
                    }
                    return uriBuilder.build();
                })
                .headers(headersConsumer(headers))
                .retrieve()
                .bodyToMono(responseType);
    }

    /**
     * JSON POST 请求。
     *
     * @param builder      WebClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param body         请求体，按 JSON 序列化；为 null 时不发送 body
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空 body 时为空 Mono
     */
    public static <T> Mono<T> postJson(WebClient.Builder builder, String url, Class<T> responseType,
                                       Object body, HttpHeaders headers) {
        return postJson(builder, url, ParameterizedTypeReference.forType(responseType), body, headers);
    }

    /**
     * JSON POST 请求，适用于泛型返回。
     *
     * @param builder      WebClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param body         请求体，按 JSON 序列化；为 null 时不发送 body
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空 body 时为空 Mono
     */
    public static <T> Mono<T> postJson(WebClient.Builder builder, String url,
                                       ParameterizedTypeReference<T> responseType, Object body, HttpHeaders headers) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(responseType, "responseType 不能为空");

        WebClient.RequestBodySpec spec = builder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headersConsumer(headers));
        WebClient.RequestHeadersSpec<?> request = body != null ? spec.bodyValue(body) : spec;
        return request.retrieve().bodyToMono(responseType);
    }

    /**
     * 表单 POST 请求（{@code application/x-www-form-urlencoded}）。
     *
     * @param builder      WebClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param form         表单字段，不可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空 body 时为空 Mono
     */
    public static <T> Mono<T> postForm(WebClient.Builder builder, String url, Class<T> responseType,
                                       MultiValueMap<String, String> form, HttpHeaders headers) {
        return postForm(builder, url, ParameterizedTypeReference.forType(responseType), form, headers);
    }

    /**
     * 表单 POST 请求，适用于泛型返回。
     *
     * @param builder      WebClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param form         表单字段，不可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空 body 时为空 Mono
     */
    public static <T> Mono<T> postForm(WebClient.Builder builder, String url,
                                       ParameterizedTypeReference<T> responseType,
                                       MultiValueMap<String, String> form, HttpHeaders headers) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(responseType, "responseType 不能为空");
        Assert.notNull(form, "form 不能为空");

        return builder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headersConsumer(headers))
                .bodyValue(form)
                .retrieve()
                .bodyToMono(responseType);
    }

    private static Consumer<HttpHeaders> headersConsumer(HttpHeaders headers) {
        return target -> {
            if (headers != null) {
                target.addAll(headers);
            }
        };
    }
}
