# fireworks-webflux-client-spring-boot-starter

基于 **WebClient（Reactor Netty）** 的生产级 HTTP 客户端 Starter，提供统一连接池、超时控制、JSON 编解码与 HTTP 接口代理能力。

## 功能特性

- **统一连接池**：基于 Reactor Netty `ConnectionProvider` 管理连接复用，支持最大连接数、排队数、连接生命周期与空闲驱逐。
- **超时控制**：连接 / 读取 / 写入超时统一配置。
- **全局定制**：通过 `WebClientCustomizer` 抽取公共配置，负载均衡与普通 `WebClient.Builder` 均自动应用。
- **负载均衡**：提供 `@LoadBalanced` 的 `loadBalancedWebClientBuilder`，用于微服务内部服务名调用。
- **接口代理**：`WebClientUtil.createProxy` 将声明式接口转换为 `WebClient` 代理对象（支持 Form / JSON 请求）。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-webflux-client-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 配置连接池（可选）

```yaml
fireworks:
  webflux-client:
    connect-timeout: 3000        # 连接超时（ms）
    read-timeout: 10000          # 读取超时（ms）
    write-timeout: 10000         # 写入超时（ms）
    max-connections: 500         # 最大连接数
    pending-acquire-max-count: 1000  # 排队获取最大数（约 2 倍 max-connections）
    acquire-timeout: 1000        # 获取连接最大等待时间（ms）
    max-life-time: 30000         # 连接最大生命周期（ms）
    max-idle-time: 30000         # 连接最大空闲时间（ms）
    evict-in-background: 60000   # 空闲连接驱逐间隔（ms）
    max-in-memory-size: 10485760 # 响应最大内存（字节）
```

### 3. 注入并使用 `WebClient`

自动注册两个 Builder（均应用了连接池与 ObjectMapper 编解码配置）：

```java
// 负载均衡 Builder：用于微服务内部服务名调用
@Autowired
@Qualifier(WebClientAutoConfiguration.LOAD_BALANCE_WEB_CLIENT_BUILDER)
private WebClient.Builder loadBalancedBuilder;

// 普通 Builder：用于第三方/外网 API
@Autowired
private WebClient.Builder webClientBuilder;

// 通过服务名调用
UserDTO user = loadBalancedBuilder.build()
        .get().uri("http://user-service/api/users/{id}", 10086L)
        .retrieve().bodyToMono(UserDTO.class).block();
```

### 4. HTTP 接口代理（`WebClientUtil`）

声明式接口 + 代理：

```java
// 定义接口
public interface UserClient {
    @GetExchange("/api/users/{id}")
    Mono<UserDTO> getUser(@PathVariable Long id);
}

// 创建代理
UserClient client = WebClientUtil.createProxy(webClientBuilder, UserClient.class, "http://user-service");

// 调用
Mono<UserDTO> mono = client.getUser(10086L);
```

`WebClientUtil` 同时提供 `requestWithForm`（Form 请求）与 `postWithJson`（JSON POST）快捷方法。

> `createProxy` 内部使用 `builder.clone()`，避免污染共享的 `WebClient.Builder`。

## 核心 Bean

| Bean | 说明 |
| --- | --- |
| `connectionProvider` | Reactor Netty 连接池 |
| `httpClient` | 配置超时的 Reactor `HttpClient` |
| `webClientCustomizer` | 公共配置定制器（连接器 + JSON 编解码 + 默认头） |
| `loadBalancedWebClientBuilder` | `@LoadBalanced` 的 Builder（微服务内部调用） |
| `webClientBuilder` | `@Primary` 普通 Builder（外部调用） |

## 注意事项

- 负载均衡依赖 Spring Cloud LoadBalancer，`LoadBalanced` Builder 仅在类路径存在时注册。
- 该模块为响应式（WebFlux）客户端，返回 `Mono`/`Flux`；同步阻塞请用 `fireworks-rest-client-spring-boot-starter`。
