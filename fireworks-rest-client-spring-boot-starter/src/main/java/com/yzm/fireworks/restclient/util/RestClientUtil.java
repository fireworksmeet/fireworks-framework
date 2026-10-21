package com.yzm.fireworks.restclient.util;

import lombok.experimental.UtilityClass;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.function.Consumer;

/**
 * RestClient 便捷工具。
 * <p>
 * 只用于零散的单次调用；同一接口反复调用时优先用 {@link #createProxy} 声明式接口
 * （{@code @GetExchange} / {@code @PostExchange}），可保留泛型返回类型、参数绑定与可 mock 性。
 *
 * @author JYuan
 */
@UtilityClass
public class RestClientUtil {

    /**
     * 创建 HTTP 服务代理（指定 baseUrl）
     *
     * @param builder     RestClient.Builder 构建器
     * @param serviceType 接口 Class 类型
     * @param baseUrl     服务的基础路径/域名（如 "http://fireworks-auth" 或 "http://localhost:8081"）
     * @param <T>         代理类型
     * @return 代理客户端实例
     */
    public static <T> T createProxy(RestClient.Builder builder, Class<T> serviceType, String baseUrl) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(serviceType, "serviceType 不能为空");

        // 使用 builder.clone() 防止修改传入的共享 builder 对象，造成多 Client 间的 baseUrl 相互污染
        RestClient.Builder clonedBuilder = builder.clone();

        // 如果传入了有效的 baseUrl，则进行设置
        if (StringUtils.hasText(baseUrl)) {
            clonedBuilder.baseUrl(baseUrl);
        }

        RestClient restClient = clonedBuilder.build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(adapter).build();
        return proxyFactory.createClient(serviceType);
    }

    /**
     * 创建 HTTP 服务代理（使用 Builder 中已有的默认配置）
     *
     * @param builder     RestClient.Builder 构建器
     * @param serviceType 接口 Class 类型
     * @param <T>         代理类型
     * @return 代理客户端实例
     */
    public static <T> T createProxy(RestClient.Builder builder, Class<T> serviceType) {
        return createProxy(builder, serviceType, null);
    }

    /**
     * GET 请求（参数只走 query，不带 body）。
     *
     * @param builder      RestClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param queryParams  query 参数，可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空响应体时为 null
     */
    public static <T> T get(RestClient.Builder builder, String url, Class<T> responseType,
                            MultiValueMap<String, String> queryParams, HttpHeaders headers) {
        return get(builder, url, ParameterizedTypeReference.forType(responseType), queryParams, headers);
    }

    /**
     * GET 请求，适用于泛型返回（如 {@code Result<List<UserDTO>>}）。
     *
     * @param builder      RestClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param queryParams  query 参数，可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空响应体时为 null
     */
    public static <T> T get(RestClient.Builder builder, String url, ParameterizedTypeReference<T> responseType,
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
                .body(responseType);
    }

    /**
     * JSON POST 请求。
     *
     * @param builder      RestClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param body         请求体，按 JSON 序列化
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空响应体时为 null
     */
    public static <T> T postJson(RestClient.Builder builder, String url, Class<T> responseType,
                                 Object body, HttpHeaders headers) {
        return postJson(builder, url, ParameterizedTypeReference.forType(responseType), body, headers);
    }

    /**
     * JSON POST 请求，适用于泛型返回。
     *
     * @param builder      RestClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param body         请求体，按 JSON 序列化
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空响应体时为 null
     */
    public static <T> T postJson(RestClient.Builder builder, String url, ParameterizedTypeReference<T> responseType,
                                 Object body, HttpHeaders headers) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(responseType, "responseType 不能为空");

        return builder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headersConsumer(headers))
                .body(body)
                .retrieve()
                .body(responseType);
    }

    /**
     * 表单 POST 请求（{@code application/x-www-form-urlencoded}）。
     *
     * @param builder      RestClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param form         表单字段，不可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空响应体时为 null
     */
    public static <T> T postForm(RestClient.Builder builder, String url, Class<T> responseType,
                                 MultiValueMap<String, String> form, HttpHeaders headers) {
        return postForm(builder, url, ParameterizedTypeReference.forType(responseType), form, headers);
    }

    /**
     * 表单 POST 请求，适用于泛型返回。
     *
     * @param builder      RestClient.Builder 构建器
     * @param url          绝对地址，或相对 builder 中 baseUrl 的路径
     * @param responseType 返回类型
     * @param form         表单字段，不可为 null
     * @param headers      额外请求头，可为 null
     * @param <T>          返回类型
     * @return 响应体，空响应体时为 null
     */
    public static <T> T postForm(RestClient.Builder builder, String url, ParameterizedTypeReference<T> responseType,
                                 MultiValueMap<String, String> form, HttpHeaders headers) {
        Assert.notNull(builder, "builder 不能为空");
        Assert.notNull(responseType, "responseType 不能为空");
        Assert.notNull(form, "form 不能为空");

        return builder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headersConsumer(headers))
                .body(form)
                .retrieve()
                .body(responseType);
    }

    private static Consumer<HttpHeaders> headersConsumer(HttpHeaders headers) {
        return target -> {
            if (headers != null) {
                target.addAll(headers);
            }
        };
    }
}
