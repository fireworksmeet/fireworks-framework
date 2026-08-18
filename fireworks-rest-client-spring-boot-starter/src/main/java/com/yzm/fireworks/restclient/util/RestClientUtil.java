package com.yzm.fireworks.restclient.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * @author JYuan
 */
public class RestClientUtil {

    private RestClientUtil() {
        // 工具类私有构造
    }

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
     * 发送 Form/URL 参数请求 (GET / POST / PUT 等)
     */
    public static <T> ResponseEntity<T> requestWithForm(
            RestClient.Builder builder,
            HttpMethod method,
            String url,
            Class<T> responseType,
            MultiValueMap<String, String> queryParams,
            HttpHeaders headers) {

        RestClient restClient = builder.clone().build();

        return restClient.method(method)
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
     */
    public static <T, D> ResponseEntity<T> postWithJson(
            RestClient.Builder builder,
            String url,
            Class<T> responseType,
            D param,
            HttpHeaders headers) {

        RestClient restClient = builder.clone().build();

        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    if (headers != null) {
                        h.addAll(headers);
                    }
                })
                .body(param)
                .retrieve()
                .toEntity(responseType);
    }
}