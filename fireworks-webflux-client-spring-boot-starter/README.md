# fireworks-webflux-client-spring-boot-starter

响应式 HTTP 客户端 Starter（Reactor Netty）。在 Spring Boot 官方连接器流水线（`ClientHttpConnectorBuilderCustomizer`）上补齐 Boot 不提供的连接池参数，不替换 `WebClient.Builder` 与 `ClientHttpConnector`。

## 职责划分

| 配置 | 来源 | 说明 |
| --- | --- | --- |
| connect / read 超时、重定向、SSL | `spring.http.reactiveclient.*` | Spring Boot 官方属性，优先级最高 |
| 编解码缓冲上限、Jackson 配置 | `spring.codec.max-in-memory-size` + 容器 `ObjectMapper` | Spring Boot 官方能力 |
| 连接池容量、排队、生命周期、空闲回收 | `fireworks.webflux-client.*` | Boot 不提供，由本模块补齐 |
| 负载均衡 | `@LoadBalanced WebClient.Builder` | Spring Cloud LoadBalancer |

> 本模块**不再**自建 `HttpClient` / `ReactorClientHttpConnector`。Boot 的 `ClientHttpConnectorAutoConfiguration` 会先按 `spring.http.reactiveclient.*` 建好 connector，再应用所有 `ClientHttpConnectorBuilderCustomizer`；替换 connector 会让官方属性整体静默失效。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-webflux-client-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 配置

```yaml
spring:
  http:
    reactiveclient:
      connect-timeout: 3s      # TCP 握手超时；不配置走 Reactor Netty 默认
      read-timeout: 10s        # 等待响应超时；不配置等于不限制
  codec:
    max-in-memory-size: 10MB   # 响应缓冲上限

fireworks:
  webflux-client:
    max-connections: 500            # 连接池上限
    pending-acquire-max-count: 1000 # 池满时允许排队的请求数，-1 为不限
    acquire-timeout: 1s             # 排队等待上限
    max-life-time: 30m              # 连接自建立起的总存活时间
    max-idle-time: 30s              # 空闲多久被回收
    evict-in-background: 60s        # 后台驱逐检查间隔
```

`spring.http.reactiveclient.*` 未配置时本模块兜底 `connect-timeout=3s`、`read-timeout=10s`；显式配置后以官方属性为准。

### 3. 注入并使用

```java
// 负载均衡 Builder：微服务内部服务名调用
@Autowired
@Qualifier(WebClientAutoConfiguration.LOAD_BALANCE_WEB_CLIENT_BUILDER)
private WebClient.Builder loadBalancedBuilder;

// 普通 Builder：第三方 / 外网 API
@Autowired
private WebClient.Builder fireworksWebClientBuilder;

UserDTO user = loadBalancedBuilder.build()
        .get().uri("http://user-service/api/users/{id}", 10086L)
        .retrieve().bodyToMono(UserDTO.class).block();
```

### 4. HTTP 接口代理（首选）

```java
public interface UserClient {
    @GetExchange("/api/users/{id}")
    Mono<UserDTO> getUser(@PathVariable Long id);
}

UserClient client = WebClientUtil.createProxy(loadBalancedBuilder, UserClient.class, "http://user-service");
Mono<UserDTO> mono = client.getUser(10086L);
```

### 5. 零散单次调用

```java
// GET，参数只走 query
Mono<UserDTO> one = WebClientUtil.get(builder, "/users/1", UserDTO.class, null, null);

// JSON POST；泛型返回用 ParameterizedTypeReference，避免 Result.data 退化成 Map
var type = new ParameterizedTypeReference<Result<List<UserDTO>>>() {
};
Mono<Result<List<UserDTO>>> list = WebClientUtil.postJson(builder, "/users", type, body, null);

// 表单 POST（application/x-www-form-urlencoded），真正带 body
Mono<Result<Void>> login = WebClientUtil.postForm(builder, "/login", Result.class, form, null);
```

## 核心 Bean

| Bean | 说明 |
| --- | --- |
| `connectionProvider` | Reactor Netty 连接池（业务定义同类型 Bean 时让位） |
| `fireworksConnectorCustomizer` | 在官方连接器流水线上注入连接池与超时兜底 |
| `fireworksWebClientBuilder` | `@Primary` 普通 Builder（外部调用） |
| `loadBalancedWebClientBuilder` | `@LoadBalanced` Builder（微服务内部调用） |

## 注意事项

- 负载均衡依赖 Spring Cloud LoadBalancer，`loadBalancedWebClientBuilder` 仅在类路径存在时注册。
- `fireworks.webflux-client.*` 只在 connector 为 Reactor Netty 时生效，其他实现启动日志会告警。
- 通过 `withHttpClientFactory` 接管 client 创建，因此不再复用 Boot 的 `ReactorResourceFactory` 事件循环配置，使用 Reactor Netty 默认 EventLoopGroup。
- 非 2xx 保持 Spring 标准行为（抛 `WebClientResponseException`）；需要按 `Result.code` 判定时由业务注册 `WebClientCustomizer` 自行定制（`api.md` §4.3）。
- 本模块为响应式客户端，返回 `Mono`/`Flux`；同步阻塞场景用 `fireworks-rest-client-spring-boot-starter`。
