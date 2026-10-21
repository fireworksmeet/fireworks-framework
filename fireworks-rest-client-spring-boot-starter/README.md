# Fireworks RestClient Spring Boot Starter

生产级 RestClient 配置。在 Spring Boot 官方流水线（`ClientHttpRequestFactoryBuilderCustomizer`）上补齐 Boot 不提供的 HC5 连接池参数，不接管 `RestClient.Builder` 与 `ClientHttpRequestFactory`。

## 职责划分

| 配置 | 来源 | 说明 |
| --- | --- | --- |
| connect / read 超时、重定向、SSL | `spring.http.client.*` | Spring Boot 官方属性，优先级最高 |
| 连接池容量、取连接等待、TTL、空闲回收、闲置校验 | `fireworks.rest-client.*` | Boot 不提供，由本模块补齐 |
| 负载均衡 | `@LoadBalanced RestClient.Builder` | Spring Cloud LoadBalancer |

> 本模块**不再**替换 `requestFactory`。Boot 的 `RestClientBuilderConfigurer` 会先按 `spring.http.client.*` 建好 factory，再应用所有 `RestClientCustomizer`；覆盖 factory 会让官方属性整体静默失效。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-rest-client-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置参数

```yaml
spring:
  http:
    client:
      connect-timeout: 3s          # TCP 握手超时；不配置走 HC5 默认 3 分钟
      read-timeout: 10s            # 等待响应超时；不配置等于不限制

fireworks:
  rest-client:
    connection-request-timeout: 800ms   # 从连接池取连接的等待上限
    max-conn-total: 500                 # 连接池总容量
    max-conn-per-route: 200             # 单个目标主机最大连接数
    time-to-live: 5m                    # 连接自建立起的总存活时间
    max-idle-time: 30s                  # 空闲多久被回收
    validate-after-inactivity: 2s       # 闲置多久后使用前重新校验
```

`spring.http.client.*` 未配置时本模块兜底 `connect-timeout=3s`、`read-timeout=10s`；显式配置后以官方属性为准。

### 3. 使用示例

#### 普通 HTTP 调用

```java
@Service
public class UserService {

    private final RestClient restClient;

    public UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.example.com")
                .build();
    }

    public UserDTO getUser(Long id) {
        return restClient.get()
                .uri("/users/{id}", id)
                .retrieve()
                .body(UserDTO.class);
    }
}
```

#### 负载均衡调用（Spring Cloud）

```java
@Service
public class UserService {

    private final RestClient lbRestClient;

    public UserService(@LoadBalanced RestClient.Builder lbRestClientBuilder) {
        this.lbRestClient = lbRestClientBuilder.build();
    }

    public UserDTO getUser(Long id) {
        return lbRestClient.get()
                .uri("http://user-service/api/users/{id}", id)
                .retrieve()
                .body(UserDTO.class);
    }
}
```

#### HTTP 接口代理（`RestClientUtil`）

```java
// 1. 定义 HTTP 接口
public interface UserClient {
    @GetExchange("/api/users/{id}")
    UserDTO getUser(@PathVariable Long id);
}

// 2. 创建代理（指定 baseUrl）
UserClient client = RestClientUtil.createProxy(restClientBuilder, UserClient.class, "http://user-service");

// 3. 调用
UserDTO user = client.getUser(10086L);
```

零散的单次调用还可以用 `RestClientUtil` 的快捷方法（返回响应体本身，空 body 返回 `null`）：

```java
// GET，参数只走 query
UserDTO user = RestClientUtil.get(builder, "/users/1", UserDTO.class, null, null);

// JSON POST；泛型返回用 ParameterizedTypeReference，避免 Result.data 退化成 Map
var type = new ParameterizedTypeReference<Result<List<UserDTO>>>() {
};
Result<List<UserDTO>> list = RestClientUtil.postJson(builder, "/users", type, body, null);

// 表单 POST（application/x-www-form-urlencoded），真正带 body
Result<Void> login = RestClientUtil.postForm(builder, "/login", Result.class, form, null);
```

> 选型：同一接口会被反复调用时，优先用上面的 `createProxy` 声明式接口——它能保留泛型返回类型、绑定 `@PathVariable` / `@RequestParam`，也方便 mock。

> `createProxy` 内部使用 `builder.clone()`，避免污染容器中共享的 `RestClient.Builder` 实例。

## 配置说明

### 超时（`spring.http.client.*`）

- `connect-timeout`：TCP 三次握手超时，内网 1~2s、外网 3~5s
- `read-timeout`：等待服务端响应，按下游 P99 的 2~3 倍设置
- 慢接口（报表、文件下载）需要的更大超时，用 `HttpClientContext` 的 per-request `RequestConfig` 覆盖，**不要**新建 factory

### 连接池（`fireworks.rest-client.*`）

- `max-conn-per-route`：`≥ 峰值QPS × 平均响应时间(秒) × 1.5`。示例：500QPS × 0.2s × 1.5 = 150，取 200
- `max-conn-total`：`Σ(max-conn-per-route)`；只调一个目标主机时两者可相等
- `connection-request-timeout`：从池中取连接的等待上限，快速失败优于无限排队；频繁超时说明 `max-conn-per-route` 不足
- `time-to-live`：连接自建立起的总存活时间，**与空闲无关**。设得太小会在高 QPS 下周期性重建连接；它的用途是让 DNS / 负载均衡变更逐步生效
- `max-idle-time`：空闲多久被回收，必须小于服务端 keepAlive（Nginx 默认 75s）
- `validate-after-inactivity`：闲置连接复用前先校验，用于规避服务端已关闭的僵尸连接（`NoHttpResponseException` / `Connection reset`）

取值语义与 HC5 一致：`-1ms` 表示不限制。

## 注意事项

1. `spring.http.client.factory` 被显式设成非 `http-components` 时，`fireworks.rest-client.*` 的池化参数不生效，启动日志会告警
2. 业务自定义 `RestClientCustomizer` 若再次调用 `requestFactory(...)`，会覆盖官方 factory 并使上述属性失效
3. 连接池指标（leased / available / pending）尚未接入：manager 由 Boot 内部创建，本模块拿不到实例
